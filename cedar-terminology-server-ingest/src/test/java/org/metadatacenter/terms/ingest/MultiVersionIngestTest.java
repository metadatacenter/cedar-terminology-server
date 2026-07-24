package org.metadatacenter.terms.ingest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.terms.store.CatalogStore;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

  @Before
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("multi-ingest");
    owlV1 = tempDir.resolve("v1.owl");
    owlV2 = tempDir.resolve("v2.owl");
    buildOntology(owlV1, "melanoma");
    buildOntology(owlV2, "carcinoma");
    catalog = CatalogStore.openInMemory();
    catalog.initSchema();
  }

  @After
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
      assertTrue(d.addedEdges().contains(BASE + "carcinoma -> " + BASE + "cancer"));
      assertTrue(d.removedEdges().contains(BASE + "melanoma -> " + BASE + "cancer"));
    }
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
