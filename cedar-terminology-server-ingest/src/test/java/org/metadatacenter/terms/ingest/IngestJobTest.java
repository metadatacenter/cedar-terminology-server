package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @BeforeEach
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("ingest-test");
    sourceOwl = tempDir.resolve("source.owl");
    buildOntology(sourceOwl);
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
  public void ingestValueSetCollection_usesTheSameMechanismAndMarksKind() throws Exception {
    // A value-set collection is ingested through the exact same content-hash path as an ontology (same
    // snapshot, same version id), then its catalog row is marked kind=value_set_collection so its
    // version resolves separately. This is what backs freeze-on-publish for a value-set constraint.
    IngestJob job = new IngestJob(fakeSource());
    Path snapshotDir = tempDir.resolve("snapshots");

    IngestJob.IngestResult r = job.ingestValueSetCollectionLatest(catalog, "MYVS", snapshotDir);

    // Same snapshot machinery as an ontology: file written, catalog registered, latest tag set.
    assertEquals(5, r.classCount());
    assertTrue(Files.exists(r.snapshotFile()));
    CatalogStore.SnapshotInfo latest = catalog.resolveLatest("MYVS").orElseThrow();
    assertEquals(r.versionId(), latest.versionId());
    assertEquals(64, r.versionId().length()); // normalized content hash

    // But the row is now a value-set collection, not an ontology.
    assertTrue(catalog.isValueSetCollection("MYVS"));
    assertFalse(catalog.isValueSetCollection("EX")); // an ordinary ingest stays an ontology
  }

  @Test
  public void fileCatalogRecordsSnapshotPathRelativeToTheCatalogDirectory() throws Exception {
    // A file-based catalog records each snapshot path relative to its own directory, so the whole
    // store (catalog + snapshots) can be copied to a server without rewriting any paths. The
    // snapshot file is still written to its real absolute location; only the recorded path is
    // relative. Reads resolve it back to an absolute path under the catalog directory.
    Path catalogFile = tempDir.resolve("catalog.sqlite");
    try (CatalogStore fileCatalog = CatalogStore.openFile(catalogFile.toString())) {
      fileCatalog.initSchema();
      IngestJob job = new IngestJob(fakeSource());
      IngestJob.IngestResult r = job.ingestLatest(fileCatalog, "EX", tempDir.resolve("snapshots"));

      String stored = rawStoredPath(catalogFile, r.versionId());
      assertEquals("snapshots/EX/" + r.versionId() + ".sqlite", stored);

      // The resolved path (via the API) is absolute, points under the catalog dir, and opens.
      String resolved = fileCatalog.resolveLatest("EX").orElseThrow().filePath();
      assertEquals(tempDir.resolve("snapshots/EX/" + r.versionId() + ".sqlite").toString(), resolved);
      assertTrue(Files.exists(Path.of(resolved)));
    }
  }

  /** Reads the file_path column exactly as stored, bypassing the store's resolution. */
  private static String rawStoredPath(Path catalogFile, String versionId) throws Exception {
    try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + catalogFile);
         var ps = conn.prepareStatement("SELECT file_path FROM snapshot WHERE version_id = ?")) {
      ps.setString(1, versionId);
      try (var rs = ps.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  @Test
  public void versionIdIsNormalizedContentHash_rawHashKeptAsFileHash() throws Exception {
    // Identity is the normalized content hash (VERSIONING-DESIGN §4.3), not the raw-file hash; the
    // raw hash is retained as file_hash for provenance.
    String rawHash = IngestJob.sha256(sourceOwl);
    IngestJob job = new IngestJob(fakeSource());
    IngestJob.IngestResult r = job.ingestLatest(catalog, "EX", tempDir.resolve("snapshots"));
    assertNotEquals(rawHash, r.versionId());
    assertEquals(64, r.versionId().length());
    assertEquals(rawHash, catalog.getSnapshot(r.versionId()).orElseThrow().fileHash());
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

  @Test
  public void selectSource_choosesOboFoundryWithTheGivenRelease() {
    // The standard ingest CLI can draw from OBO Foundry, not just BioPortal (no network here — the
    // selection + PURL addressing is what's under test). BioPortal selection needs the API key env and
    // is covered by the ingest path elsewhere.
    SubmissionSource current = IngestJob.selectSource("obofoundry", null);
    assertTrue(current instanceof OboFoundrySubmissionSource);
    assertEquals("obofoundry", current.backendId());
    assertEquals("http://purl.obolibrary.org/obo/doid.owl",
        ((OboFoundrySubmissionSource) current).downloadUrl("DOID"));

    SubmissionSource dated = IngestJob.selectSource("obofoundry", "2024-05-29");
    assertEquals("http://purl.obolibrary.org/obo/doid/releases/2024-05-29/doid.owl",
        ((OboFoundrySubmissionSource) dated).downloadUrl("DOID"));
  }

  @Test
  public void recordsTheSourceBackendOnTheSnapshot() throws Exception {
    // The backend a snapshot's bytes came from is recorded as audit provenance. The default BioPortal
    // source leaves it 'bioportal'; a second source (here an OBO-Foundry-like stub) records its own id.
    // Identity is unaffected — same content, same version_id regardless of backend.
    IngestJob.IngestResult bp = new IngestJob(fakeSource()).ingestLatest(catalog, "EX", tempDir.resolve("bp"));
    assertEquals("bioportal", catalog.snapshotProvenance(bp.versionId(), "EX").orElseThrow().backend());

    SubmissionSource obo = new SubmissionSource() {
      @Override
      public String backendId() {
        return "obofoundry";
      }

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
    IngestJob.IngestResult r = new IngestJob(obo).ingestLatest(catalog, "OBOEX", tempDir.resolve("obo"));
    assertEquals("obofoundry", catalog.snapshotProvenance(r.versionId(), "OBOEX").orElseThrow().backend());
    // Same source bytes as the BioPortal ingest ⇒ identical content-hash version_id: identity does not
    // depend on the backend.
    assertEquals(bp.versionId(), r.versionId());
  }

  @Test
  public void derivesTheCanonicalIriAtIngest_andJoinsSourcesByIri() throws Exception {
    // A6 derived iri via a later backfill; ingest now derives it inline (from this snapshot's dominant
    // own namespace), so a fresh ingest is iri-identified immediately. The iri is content-derived, so
    // the same ontology ingested under two acronyms — as two authorities might label it — shares one
    // iri, and acronymsForIri joins them. The test ontology's namespace is http://ex/ -> iri http://ex.
    IngestJob job = new IngestJob(fakeSource());
    job.ingestLatest(catalog, "EX", tempDir.resolve("a"));
    assertEquals("http://ex", catalog.ontologyIri("EX").orElseThrow());

    job.ingestLatest(catalog, "EX_ALIAS", tempDir.resolve("b")); // same bytes, a different acronym
    assertEquals("http://ex", catalog.ontologyIri("EX_ALIAS").orElseThrow());
    assertEquals(List.of("EX", "EX_ALIAS"), catalog.acronymsForIri("http://ex"));
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

  private static void buildEmptyOntology(Path file) throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLOntology ont = m.createOntology(IRI.create(BASE + "empty")); // valid ontology, zero classes
    try (var out = Files.newOutputStream(file)) {
      m.saveOntology(ont, out);
    }
  }

  @Test
  public void ingest_refusesToRegisterAnEmptyExtraction() throws Exception {
    // A parse that yields zero classes (a failed download/import, a classpath gap) must not register
    // a snapshot — otherwise it would silently replace a good snapshot with an empty one.
    buildEmptyOntology(sourceOwl);
    IngestJob job = new IngestJob(fakeSource());
    assertThrows(IOException.class, () -> job.ingestLatest(catalog, "EX", tempDir.resolve("snapshots")));
    assertTrue(catalog.resolveLatest("EX").isEmpty());
  }
}
