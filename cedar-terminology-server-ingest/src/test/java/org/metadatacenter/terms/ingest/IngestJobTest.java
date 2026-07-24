package org.metadatacenter.terms.ingest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end ingestion without a network: a fake {@link SubmissionSource} supplies a locally built
 * OWL file, and the test verifies the resulting snapshot file and catalog registration.
 */
public class IngestJobTest {

  private static final String BASE = "http://ex/";

  private Path tempDir;
  private Path sourceOwl;
  private CatalogStore catalog;

  private static IRI iri(String s) {
    return IRI.create(BASE + s);
  }

  private static Submission submission() {
    return new Submission(7, "v7", "2025-01-01", "OWL");
  }

  @Before
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("ingest-test");
    sourceOwl = tempDir.resolve("source.owl");
    buildOntology(sourceOwl);
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

  private SubmissionSource fakeSource() {
    return new SubmissionSource() {
      @Override
      public OntologyAccess accessInfo(String acronym) {
        return new OntologyAccess("public", null);
      }

      @Override
      public List<Submission> listSubmissions(String acronym) {
        return List.of(submission());
      }

      @Override
      public Submission latestSubmission(String acronym) {
        return submission();
      }

      @Override
      public Path download(String acronym, int submissionId, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Path dest = targetDir.resolve("source.owl");
        Files.copy(sourceOwl, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
      }
    };
  }

  @Test
  public void ingestLatest_writesSnapshotAndRegistersCatalog() throws Exception {
    IngestJob job = new IngestJob(fakeSource());
    Path snapshotDir = tempDir.resolve("snapshots");

    IngestJob.IngestResult r = job.ingestLatest(catalog, "EX", snapshotDir);

    assertEquals(5, r.classCount());
    assertEquals(4, r.edgeCount());
    assertTrue(Files.exists(r.snapshotFile()));

    CatalogStore.SnapshotInfo latest = catalog.resolveLatest("EX").orElseThrow();
    assertEquals(r.versionId(), latest.versionId());
    assertEquals("v7", latest.declaredVersion());
    assertEquals("subsumption", latest.hierarchyStatus());
    assertEquals(Integer.valueOf(5), latest.classCount());

    try (SnapshotStore store = SnapshotStore.openFile(r.snapshotFile().toString())) {
      assertEquals(List.of(BASE + "animal", BASE + "pet"), store.roots());
      assertEquals(List.of(BASE + "cat", BASE + "dog"), store.children(BASE + "mammal"));
      assertEquals("Dog", store.prefLabel(BASE + "dog").orElseThrow());
    }
  }

  @Test
  public void versionIdIsContentHashOfRawFile() throws Exception {
    String expected = IngestJob.sha256(sourceOwl);
    IngestJob job = new IngestJob(fakeSource());
    IngestJob.IngestResult r = job.ingestLatest(catalog, "EX", tempDir.resolve("snapshots"));
    assertEquals(expected, r.versionId());
  }

  @Test
  public void refusesRestrictedOntologyWithoutDownloading() throws Exception {
    SubmissionSource restricted = new SubmissionSource() {
      @Override
      public OntologyAccess accessInfo(String acronym) {
        return new OntologyAccess("private", null);
      }

      @Override
      public List<Submission> listSubmissions(String acronym) {
        return List.of(submission());
      }

      @Override
      public Submission latestSubmission(String acronym) {
        return submission();
      }

      @Override
      public Path download(String acronym, int submissionId, Path targetDir) {
        throw new AssertionError("download must not be called for restricted content");
      }
    };

    IngestJob job = new IngestJob(restricted);
    assertThrows(IOException.class, () -> job.ingestLatest(catalog, "RESTRICTED", tempDir.resolve("snapshots")));
    assertTrue(catalog.resolveLatest("RESTRICTED").isEmpty());
  }

  @Test
  public void ingestAllSkipsFailingSubmission() throws Exception {
    SubmissionSource flaky = new SubmissionSource() {
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
        if (submissionId == 1) {
          throw new IOException("simulated download failure");
        }
        Files.createDirectories(targetDir);
        Path dest = targetDir.resolve("source.owl");
        Files.copy(sourceOwl, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
      }
    };

    IngestJob job = new IngestJob(flaky);
    List<IngestJob.IngestResult> results = job.ingestAll(catalog, "EX", tempDir.resolve("snapshots"));

    assertEquals(1, results.size());                 // submission 1 failed and was skipped
    assertEquals(2, results.get(0).submissionId());
    assertEquals(1, catalog.listSnapshots("EX").size());
    assertEquals("v2", catalog.resolveLatest("EX").orElseThrow().declaredVersion()); // latest = the good one
  }

  private static void buildOntology(Path file) throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology ont = m.createOntology(IRI.create(BASE + "test"));

    OWLClass animal = df.getOWLClass(iri("animal"));
    OWLClass mammal = df.getOWLClass(iri("mammal"));
    OWLClass cat = df.getOWLClass(iri("cat"));
    OWLClass dog = df.getOWLClass(iri("dog"));
    OWLClass pet = df.getOWLClass(iri("pet"));
    OWLObjectProperty partOf = df.getOWLObjectProperty(iri("part_of"));

    m.addAxiom(ont, df.getOWLSubClassOfAxiom(mammal, animal));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(cat, mammal));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(dog, mammal));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(dog, pet));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(dog, df.getOWLObjectSomeValuesFrom(partOf, animal)));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), dog.getIRI(), df.getOWLLiteral("Dog")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), cat.getIRI(), df.getOWLLiteral("Cat")));

    try (var out = Files.newOutputStream(file)) {
      m.saveOntology(ont, out);
    }
  }
}
