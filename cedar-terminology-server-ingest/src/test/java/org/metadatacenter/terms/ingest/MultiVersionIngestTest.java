package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotDiff;
import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backfills two versions of an ontology through {@link IngestJob#ingestAll} and then diffs the two
 * resulting snapshots. Version 1 has {@code melanoma}; version 2 replaces it with {@code carcinoma}.
 */
public class MultiVersionIngestTest {

  private static final String ONT = "DTEST";
  private static final String BASE = "http://ex/";

  private Path tempDir;
  private Path owlV1;
  private Path owlV2;
  private CatalogStore catalog;

  @BeforeEach
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("multi-ingest");
    owlV1 = tempDir.resolve("v1.owl");
    owlV2 = tempDir.resolve("v2.owl");
    buildOntology(owlV1, "melanoma");
    buildOntology(owlV2, "carcinoma");
    catalog = CatalogStore.openInMemory();
    catalog.initSchema();
  }

  @AfterEach
  public void tearDown() throws Exception {
    catalog.close();
    if (tempDir != null) {
      try (var paths = Files.walk(tempDir)) {
        paths.sorted(Comparator.reverseOrder()).forEach(p -> {
          try {
            Files.deleteIfExists(p);
          } catch (IOException ignored) {
          }
        });
      }
    }
  }

  private SubmissionSource twoVersionSource() {
    return new SubmissionSource() {
      @Override
      public OntologyAccess accessInfo(String acronym) {
        return new OntologyAccess("public", null);
      }

      @Override
      public List<Submission> listSubmissions(String acronym) {
        return List.of(new Submission(1, "v1", "2024-01-01", "OWL"),
            new Submission(2, "v2", "2025-01-01", "OWL"));
      }

      @Override
      public Submission latestSubmission(String acronym) {
        return new Submission(2, "v2", "2025-01-01", "OWL");
      }

      @Override
      public Path download(String acronym, int submissionId, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Path dest = targetDir.resolve("sub" + submissionId + ".owl");
        Files.copy(submissionId == 1 ? owlV1 : owlV2, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
      }
    };
  }

  @Test
  public void ingestAll_registersEveryVersionAndTagsNewestLatest() throws Exception {
    IngestJob job = new IngestJob(twoVersionSource());
    List<IngestJob.IngestResult> results = job.ingestAll(catalog, ONT, tempDir.resolve("snapshots"));

    assertEquals(2, results.size());
    assertEquals(2, catalog.listSnapshots(ONT).size());

    // latest points at the newest submission (id 2)
    IngestJob.IngestResult newest = results.stream()
        .max(Comparator.comparingInt(IngestJob.IngestResult::submissionId)).orElseThrow();
    assertEquals(newest.versionId(), catalog.resolveLatest(ONT).orElseThrow().versionId());
    assertEquals("v2", catalog.resolveLatest(ONT).orElseThrow().declaredVersion());
  }

  @Test
  public void diffBetweenVersionsReportsAddedAndRemoved() throws Exception {
    IngestJob job = new IngestJob(twoVersionSource());
    List<IngestJob.IngestResult> results = job.ingestAll(catalog, ONT, tempDir.resolve("snapshots"));

    Path v1 = results.stream().filter(r -> r.submissionId() == 1).findFirst().orElseThrow().snapshotFile();
    Path v2 = results.stream().filter(r -> r.submissionId() == 2).findFirst().orElseThrow().snapshotFile();

    try (SnapshotStore from = SnapshotStore.openFile(v1.toString());
         SnapshotStore to = SnapshotStore.openFile(v2.toString())) {
      SnapshotDiff.Diff d = new SnapshotDiff().diff(from, to);
      assertTrue(d.addedConcepts().contains(BASE + "carcinoma"));
      assertTrue(d.removedConcepts().contains(BASE + "melanoma"));
      // Edge strings now carry the source predicate ("child -[pred]-> parent"); assert the subsumption
      // edge by its endpoints, regardless of the predicate token.
      assertTrue(d.addedEdges().stream()
          .anyMatch(e -> e.startsWith(BASE + "carcinoma") && e.endsWith("-> " + BASE + "cancer")));
      assertTrue(d.removedEdges().stream()
          .anyMatch(e -> e.startsWith(BASE + "melanoma") && e.endsWith("-> " + BASE + "cancer")));
    }
  }

  @Test
  public void reIngestingIsIdempotent_soAnInterruptedRunHealsOnRerun() throws Exception {
    // A crash mid-registration can leave a partial catalog state; the operator's remedy is to re-run
    // the ingest. Because every registration step is an idempotent upsert inside one transaction, a
    // full re-run converges to the same single, correct state — no duplicate snapshots, same latest,
    // same canonical identity.
    IngestJob job = new IngestJob(twoVersionSource());
    job.ingestAll(catalog, ONT, tempDir.resolve("snapshots"));

    int snapshotsAfterFirst = catalog.listSnapshots(ONT).size();
    String latestAfterFirst = catalog.resolveLatest(ONT).orElseThrow().versionId();
    java.util.Optional<String> iriAfterFirst = catalog.ontologyIri(ONT);

    job.ingestAll(catalog, ONT, tempDir.resolve("snapshots")); // re-run, as recovery after interruption

    assertEquals(snapshotsAfterFirst, catalog.listSnapshots(ONT).size());
    assertEquals(latestAfterFirst, catalog.resolveLatest(ONT).orElseThrow().versionId());
    assertEquals(iriAfterFirst, catalog.ontologyIri(ONT));
  }

  @Test
  public void versionIdIsTheContentHash_soIdenticalContentMergesAndDiffersFromRawHash() throws Exception {
    // Two submissions with byte-different files but the SAME extracted content (v1 saved twice)
    // share a content-hash version id, so ingestAll registers one snapshot, not two -- the merge the
    // §4.3 cutover does for existing data now happens at ingest time.
    SubmissionSource sameContentTwice = new SubmissionSource() {
      @Override public OntologyAccess accessInfo(String acronym) { return new OntologyAccess("public", null); }
      @Override public List<Submission> listSubmissions(String acronym) {
        return List.of(new Submission(1, "a", "2024-01-01", "OWL"), new Submission(2, "b", "2025-01-01", "OWL"));
      }
      @Override public Submission latestSubmission(String acronym) { return new Submission(2, "b", "2025-01-01", "OWL"); }
      @Override public Path download(String acronym, int submissionId, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Path dest = targetDir.resolve("sub" + submissionId + ".owl");
        Files.copy(owlV1, dest, StandardCopyOption.REPLACE_EXISTING); // same content for both submissions
        return dest;
      }
    };
    List<IngestJob.IngestResult> results = new IngestJob(sameContentTwice)
        .ingestAll(catalog, ONT, tempDir.resolve("snapshots"));

    assertEquals(1, catalog.listSnapshots(ONT).size(), "identical content collapses to one snapshot");
    // The version id is a content hash (64 hex chars), not the raw-file hash recorded as file_hash.
    CatalogStore.SnapshotInfo snap = catalog.listSnapshots(ONT).get(0);
    assertEquals(64, snap.versionId().length());
    assertTrue(results.stream().allMatch(r -> r.versionId().equals(snap.versionId())));
  }

  private static void buildOntology(Path file, String leaf) throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology ont = m.createOntology(IRI.create(BASE + "test"));
    var disease = df.getOWLClass(IRI.create(BASE + "disease"));
    var cancer = df.getOWLClass(IRI.create(BASE + "cancer"));
    var leafCls = df.getOWLClass(IRI.create(BASE + leaf));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(cancer, disease));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(leafCls, cancer));
    try (var out = Files.newOutputStream(file)) {
      m.saveOntology(ont, out);
    }
  }
}
