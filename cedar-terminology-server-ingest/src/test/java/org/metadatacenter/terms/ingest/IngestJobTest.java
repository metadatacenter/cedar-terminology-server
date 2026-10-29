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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

  /** {@link #fakeSource()} with a display name supplied on its access info, like BioPortal's. */
  private SubmissionSource namedSource(String displayName) {
    SubmissionSource base = fakeSource();
    return new SubmissionSource() {
      @Override
      public OntologyAccess accessInfo(String acronym) {
        return new OntologyAccess("public", null, displayName);
      }

      @Override
      public List<Submission> listSubmissions(String acronym) throws IOException, InterruptedException {
        return base.listSubmissions(acronym);
      }

      @Override
      public Submission latestSubmission(String acronym) throws IOException, InterruptedException {
        return base.latestSubmission(acronym);
      }

      @Override
      public Path download(String acronym, int submissionId, Path targetDir)
          throws IOException, InterruptedException {
        return base.download(acronym, submissionId, targetDir);
      }
    };
  }

  private String displayNameOf(String acronym) throws Exception {
    return catalog.listOntologies().stream()
        .filter(o -> acronym.equals(o.acronym()))
        .map(CatalogStore.OntologyInfo::name)
        .findFirst()
        .orElseThrow();
  }

  @Test
  public void ingestLatest_persistsSourceDisplayName_elseFallsBackToAcronym() throws Exception {
    // A source that supplies a human-readable title stores it as the catalog display name.
    new IngestJob(namedSource("Human Disease Ontology"))
        .ingestLatest(catalog, "DOID", tempDir.resolve("snapshots"));
    assertEquals("Human Disease Ontology", displayNameOf("DOID"));

    // A source with no title falls back to the acronym — the only label available — rather than
    // leaving the name null.
    new IngestJob(fakeSource())
        .ingestLatest(catalog, "EX", tempDir.resolve("snapshots"));
    assertEquals("EX", displayNameOf("EX"));
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
      var event = fileCatalog.ingestHistory("EX").get(0);
      assertTrue(Path.of(event.rawFilePath()).isAbsolute());
      try (var db = java.sql.DriverManager.getConnection("jdbc:sqlite:" + catalogFile);
           var statement = db.createStatement();
           var rows = statement.executeQuery("SELECT raw_file_path FROM ingest_event")) {
        assertTrue(rows.next());
        assertTrue(rows.getString(1).startsWith("snapshots/EX/raw/"));
      }
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
    // Identity is the normalized content hash (VERSIONING-ROADMAP "The Model" §4.3), not the raw-file hash; the
    // raw hash is retained as file_hash for provenance.
    String rawHash = IngestJob.sha256(sourceOwl);
    IngestJob job = new IngestJob(fakeSource());
    IngestJob.IngestResult r = job.ingestLatest(catalog, "EX", tempDir.resolve("snapshots"));
    assertNotEquals(rawHash, r.versionId());
    assertEquals(64, r.versionId().length());
    assertEquals(rawHash, catalog.getSnapshot(r.versionId()).orElseThrow().fileHash());
  }

  @Test
  public void repeatedContentPreservesFileAndProvenanceButRecordsEveryDownload() throws Exception {
    var first = new IngestJob(fakeSource()).ingestLatest(catalog, "EX", tempDir.resolve("snapshots"));
    var before = catalog.resolveLatest("EX").orElseThrow();
    var provenance = catalog.snapshotProvenance(first.versionId(), "EX").orElseThrow();
    // Metadata outside identity must survive, even if the next source would supply different links.
    try (SnapshotStore snapshot = SnapshotStore.openFile(first.snapshotFile().toString())) {
      snapshot.setMeta("original-observation", "preserved");
    }
    byte[] original = Files.readAllBytes(first.snapshotFile());
    // Different raw serialization, identical normalized content.
    Files.writeString(sourceOwl, "\n", java.nio.file.StandardOpenOption.APPEND);
    SubmissionSource delegate = fakeSource();
    SubmissionSource other = new SubmissionSource() {
      public String backendId() { return "ols"; }
      public OntologyAccess accessInfo(String acronym) throws IOException, InterruptedException {
        return delegate.accessInfo(acronym);
      }
      public List<Submission> listSubmissions(String acronym) { return List.of(submission()); }
      public Submission latestSubmission(String acronym) { return submission(); }
      public Path download(String acronym, int id, Path directory) throws IOException, InterruptedException {
        return delegate.download(acronym, id, directory);
      }
    };
    var second = new IngestJob(other).ingestSubmission(catalog, "EX",
        new Submission(99, "another release", null, "OWL"), tempDir.resolve("different-output"), true);
    assertEquals(first.versionId(), second.versionId());
    assertEquals(first.snapshotFile(), second.snapshotFile());
    assertEquals(before, catalog.resolveLatest("EX").orElseThrow());
    assertEquals(provenance, catalog.snapshotProvenance(first.versionId(), "EX").orElseThrow());
    org.junit.jupiter.api.Assertions.assertArrayEquals(original, Files.readAllBytes(first.snapshotFile()));
    assertEquals(1, catalog.listSnapshots("EX").size());
    var history = catalog.ingestHistory("EX");
    assertEquals(2, history.size());
    assertEquals(List.of("bioportal", "ols"), history.stream().map(CatalogStore.IngestEvent::backend).toList());
    assertEquals(List.of(7, 99), history.stream().map(CatalogStore.IngestEvent::submissionId).toList());
    assertNotEquals(history.get(0).rawFileHash(), history.get(1).rawFileHash());
    for (var event : history) {
      assertEquals(first.versionId(), event.versionId());
      assertEquals("succeeded", event.status());
      assertTrue(Files.isRegularFile(Path.of(event.rawFilePath())));
      assertEquals(event.rawFileHash(), PropertyBackfillJob.sha256(Path.of(event.rawFilePath())));
      assertFalse(java.time.Instant.parse(event.retrievedAt()).isBefore(java.time.Instant.parse(event.startedAt())));
      assertFalse(java.time.Instant.parse(event.completedAt()).isBefore(java.time.Instant.parse(event.retrievedAt())));
    }
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
    var event = catalog.ingestHistory("RESTRICTED").get(0);
    assertEquals("failed", event.status());
    assertEquals(null, event.versionId());
    assertEquals(null, event.retrievedAt());
    assertEquals(null, event.rawFilePath());
    assertEquals("IOException", event.errorType());
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

  /** Builds a source that serves each acronym the file given for it and records each download. */
  private static SubmissionSource serving(Map<String, Path> files, List<String> downloads) {
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
        downloads.add(acronym + ":" + submissionId);
        Files.createDirectories(targetDir);
        Path dest = targetDir.resolve("source.owl");
        Files.copy(files.get(acronym), dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
      }
    };
  }

  /**
   * The header-IRI backfill gives an ontology the catalog holds by acronym alone the identity its
   * owl:Ontology header declares. The header is read from a fresh download of the source's latest
   * submission, not from anything already stored, and is written to the catalog and to the
   * ontology's snapshot. An ontology that has an identity, or that the run does not name, is not
   * fetched and does not change.
   */
  @Test
  public void backfillHeaderIris_restoresIdentityFromTheLatestSubmissionsHeader() throws Exception {
    Path snapshotDir = tempDir.resolve("snapshots");
    List<String> downloads = new ArrayList<>();
    IngestJob job = new IngestJob(serving(
        Map.of("EX", sourceOwl, "KEPT", sourceOwl, "OUTSIDE", sourceOwl), downloads));
    IngestJob.IngestResult ex = job.ingestLatest(catalog, "EX", snapshotDir);
    job.ingestLatest(catalog, "KEPT", snapshotDir);
    job.ingestLatest(catalog, "OUTSIDE", snapshotDir);
    catalog.clearOntologyIri("EX");
    catalog.clearOntologyIri("OUTSIDE");
    // The source's latest submission now declares a different ontology IRI from the snapshot's.
    buildOntology(sourceOwl, IRI.create(BASE + "renamed"));
    downloads.clear();

    IngestJob.HeaderIriSummary summary = job.backfillHeaderIris(catalog, snapshotDir, Set.of("EX", "KEPT"));

    assertEquals(List.of("EX:7"), downloads, "only the named ontology without an identity is fetched");
    assertEquals(BASE + "renamed", catalog.ontologyIri("EX").orElseThrow());
    try (SnapshotStore store = SnapshotStore.openFile(ex.snapshotFile().toString())) {
      assertEquals(BASE + "renamed", store.getMeta("ontology_iri").orElseThrow());
    }
    assertEquals("http://ex", catalog.ontologyIri("KEPT").orElseThrow());
    assertTrue(catalog.ontologyIri("OUTSIDE").isEmpty());
    assertEquals(new IngestJob.HeaderIriSummary(1, 1, 0, 0, 1, 0), summary);
  }

  /**
   * An ontology stays acronym-only when its header cannot identify it: when it declares no IRI, and
   * when two ontologies with different content declare the same one, which is not a merge.
   */
  @Test
  public void backfillHeaderIris_leavesAnOntologyAcronymOnlyWhenItsHeaderCannotIdentifyIt() throws Exception {
    Path anonymous = tempDir.resolve("anonymous.owl");
    buildOntology(anonymous, null);
    Path larger = tempDir.resolve("larger.owl");
    buildOntologyWithAnExtraClass(larger);
    Path snapshotDir = tempDir.resolve("snapshots");
    IngestJob job = new IngestJob(serving(
        Map.of("NOHEADER", anonymous, "FIRST", sourceOwl, "SECOND", larger), new ArrayList<>()));
    for (String acronym : List.of("NOHEADER", "FIRST", "SECOND")) {
      job.ingestLatest(catalog, acronym, snapshotDir);
      catalog.clearOntologyIri(acronym);
    }

    IngestJob.HeaderIriSummary summary = job.backfillHeaderIris(catalog, snapshotDir, Set.of());

    assertTrue(catalog.ontologyIri("NOHEADER").isEmpty());
    assertTrue(catalog.ontologyIri("FIRST").isEmpty(), "a header shared with other content was kept");
    assertTrue(catalog.ontologyIri("SECOND").isEmpty(), "a header shared with other content was kept");
    assertEquals(new IngestJob.HeaderIriSummary(3, 2, 1, 0, 0, 3), summary);
  }

  /**
   * The label backfill, which gives snapshots taken before label capture their labels without
   * changing their identity. It re-extracts the snapshot's own submission and copies the labels in
   * only when the re-extraction hashes to the snapshot's version id, so the stored content never
   * changes. Here a snapshot is ingested and its label table emptied, as an old one would be.
   */
  @Test
  public void backfillLabels_restoresTheLabelsOfASnapshotThatPredatesThem() throws Exception {
    Path snapshotDir = tempDir.resolve("snapshots");
    IngestJob.IngestResult ingested = new IngestJob(fakeSource()).ingestLatest(catalog, "EX", snapshotDir);
    int labels = labelCount(ingested.snapshotFile());
    assertTrue(labels > 0, "the fixture should carry labels to lose");
    removeLabels(ingested.snapshotFile());

    IngestJob.BackfillSummary first = new IngestJob(fakeSource()).backfillLabels(catalog, snapshotDir, Set.of());
    assertEquals(1, first.filled());
    assertEquals(labels, first.labelsAdded());
    assertEquals(labels, labelCount(ingested.snapshotFile()));
    assertEquals(ingested.versionId(), catalog.resolveLatest("EX").orElseThrow().versionId(),
        "the backfill must not change the snapshot's identity");

    // Resumable: a snapshot that has its labels is skipped.
    IngestJob.BackfillSummary second = new IngestJob(fakeSource()).backfillLabels(catalog, snapshotDir, Set.of());
    assertEquals(0, second.filled());
    assertEquals(1, second.alreadyLabeled());
  }

  /** A source that now serves different content cannot enrich the snapshot: the identity gate declines. */
  @Test
  public void backfillLabels_leavesASnapshotAloneWhenTheSourceHasMovedOn() throws Exception {
    Path snapshotDir = tempDir.resolve("snapshots");
    IngestJob.IngestResult ingested = new IngestJob(fakeSource()).ingestLatest(catalog, "EX", snapshotDir);
    removeLabels(ingested.snapshotFile());
    buildOntologyWithAnExtraClass(sourceOwl);

    IngestJob.BackfillSummary summary = new IngestJob(fakeSource()).backfillLabels(catalog, snapshotDir, Set.of());
    assertEquals(0, summary.filled());
    assertEquals(1, summary.mismatched());
    assertEquals(0, labelCount(ingested.snapshotFile()), "a declined snapshot must be left untouched");
  }

  /** Each run serves one backend, so a snapshot another backend produced is skipped rather than refetched. */
  @Test
  public void backfillLabels_skipsSnapshotsAnotherBackendProduced() throws Exception {
    Path snapshotDir = tempDir.resolve("snapshots");
    IngestJob.IngestResult ingested = new IngestJob(fakeSource()).ingestLatest(catalog, "EX", snapshotDir);
    removeLabels(ingested.snapshotFile());
    SubmissionSource base = fakeSource();
    SubmissionSource otherBackend = new SubmissionSource() {
      @Override
      public String backendId() {
        return "obofoundry";
      }

      @Override
      public OntologyAccess accessInfo(String acronym) throws IOException, InterruptedException {
        return base.accessInfo(acronym);
      }

      @Override
      public List<Submission> listSubmissions(String acronym) throws IOException, InterruptedException {
        return base.listSubmissions(acronym);
      }

      @Override
      public Submission latestSubmission(String acronym) throws IOException, InterruptedException {
        return base.latestSubmission(acronym);
      }

      @Override
      public Path download(String acronym, int submissionId, Path targetDir) throws IOException, InterruptedException {
        return base.download(acronym, submissionId, targetDir);
      }
    };

    IngestJob.BackfillSummary summary = new IngestJob(otherBackend).backfillLabels(catalog, snapshotDir, Set.of());
    assertEquals(0, summary.filled());
    assertEquals(1, summary.otherBackend());
    assertEquals(0, labelCount(ingested.snapshotFile()));
  }

  /**
   * The label backfill can read the retained download instead of the source, which is the route
   * once a source has moved on. A snapshot whose download is gone is counted and left alone.
   */
  @Test
  public void backfillLabelsFromRaw_restoresLabelsFromTheRetainedDownload() throws Exception {
    Path snapshotDir = tempDir.resolve("snapshots");
    IngestJob.IngestResult kept = new IngestJob(fakeSource()).ingestLatest(catalog, "EX", snapshotDir);
    IngestJob.IngestResult lost = new IngestJob(fakeSource()).ingestLatest(catalog, "LOST", snapshotDir);
    int labels = labelCount(kept.snapshotFile());
    removeLabels(kept.snapshotFile());
    removeLabels(lost.snapshotFile());
    deleteRecursively(snapshotDir.resolve("LOST").resolve("raw"));

    // The job has no source, so the retained download is its only input.
    IngestJob.BackfillSummary first = new IngestJob(null).backfillLabelsFromRaw(catalog, snapshotDir, Set.of());
    assertEquals(1, first.filled());
    assertEquals(1, first.noSubmission(), "the snapshot whose download is gone");
    assertEquals(0, first.failed());
    assertEquals(labels, labelCount(kept.snapshotFile()));
    assertEquals(0, labelCount(lost.snapshotFile()));
    assertEquals(kept.versionId(), catalog.resolveLatest("EX").orElseThrow().versionId());

    IngestJob.BackfillSummary second = new IngestJob(null).backfillLabelsFromRaw(catalog, snapshotDir, Set.of());
    assertEquals(0, second.filled());
    assertEquals(1, second.alreadyLabeled());
  }

  /**
   * Downloads retained before content-hash naming kept the source's own file name, so the
   * definition backfill finds one by hashing what the directory holds.
   */
  @Test
  public void backfillDefinitionsFromRaw_findsADownloadKeptUnderItsSourceName() throws Exception {
    addDefinition(sourceOwl, "dog", "A domesticated canid.");
    Path snapshotDir = tempDir.resolve("snapshots");
    IngestJob.IngestResult ingested = new IngestJob(fakeSource()).ingestLatest(catalog, "EX", snapshotDir);
    assertEquals(1, definitionCount(ingested.snapshotFile()), "the fixture should carry a definition to lose");
    removeDefinitions(ingested.snapshotFile());
    Path rawDir = snapshotDir.resolve("EX").resolve("raw");
    deleteRecursively(rawDir);
    Files.createDirectories(rawDir);
    Files.copy(sourceOwl, rawDir.resolve("doid.owl"));

    IngestJob.BackfillSummary first = new IngestJob(null).backfillDefinitionsFromRaw(catalog, snapshotDir, Set.of());
    assertEquals(1, first.filled());
    assertEquals(1, first.labelsAdded(), "the count of definitions added");
    assertEquals(1, definitionCount(ingested.snapshotFile()));
    assertEquals(ingested.versionId(), catalog.resolveLatest("EX").orElseThrow().versionId());

    IngestJob.BackfillSummary second = new IngestJob(null).backfillDefinitionsFromRaw(catalog, snapshotDir, Set.of());
    assertEquals(0, second.filled());
    assertEquals(1, second.alreadyLabeled());
  }

  /**
   * Definitions are refetched only for a snapshot whose download is gone, and only the release that
   * built the snapshot may supply them. What the pass fetches is retained, so the next one need not.
   */
  @Test
  public void refetchDefinitions_fetchesOnlyWhereTheDownloadIsGoneAndOnlyTheSameRelease() throws Exception {
    addDefinition(sourceOwl, "dog", "A domesticated canid.");
    Path snapshotDir = tempDir.resolve("snapshots");
    IngestJob.IngestResult ingested = new IngestJob(fakeSource()).ingestLatest(catalog, "EX", snapshotDir);
    String fileHash = catalog.getSnapshot(ingested.versionId()).orElseThrow().fileHash();
    removeDefinitions(ingested.snapshotFile());

    // The download is still held, so the raw pass is the route and nothing is fetched.
    IngestJob.BackfillSummary held = new IngestJob(fakeSource()).refetchDefinitions(catalog, snapshotDir, Set.of(), false);
    assertEquals(1, held.alreadyLabeled());
    assertEquals(0, held.filled());

    Path rawDir = snapshotDir.resolve("EX").resolve("raw");
    deleteRecursively(rawDir);
    Path release = tempDir.resolve("release.owl");
    Files.copy(sourceOwl, release);

    // The source now serves another release, whose definitions are not this snapshot's.
    buildOntologyWithAnExtraClass(sourceOwl);
    IngestJob.BackfillSummary moved = new IngestJob(fakeSource()).refetchDefinitions(catalog, snapshotDir, Set.of(), false);
    assertEquals(1, moved.mismatched());
    assertEquals(0, moved.filled());
    assertEquals(0, definitionCount(ingested.snapshotFile()), "a declined snapshot must be left untouched");

    // The source serves the snapshot's own release again. Its definitions attach, and its download
    // is kept this time.
    Files.copy(release, sourceOwl, StandardCopyOption.REPLACE_EXISTING);
    IngestJob.BackfillSummary same = new IngestJob(fakeSource()).refetchDefinitions(catalog, snapshotDir, Set.of(), false);
    assertEquals(1, same.filled());
    assertEquals(1, definitionCount(ingested.snapshotFile()));
    assertTrue(Files.exists(rawDir.resolve(fileHash + ".gz")), "the refetched download was not retained");
    assertEquals(ingested.versionId(), catalog.resolveLatest("EX").orElseThrow().versionId());
  }

  /**
   * BioPortal serves some submissions as a zip that also holds a catalog or a readme. The
   * ontology is the largest entry, and a zip with no file in it is refused rather than parsed.
   */
  @Test
  public void decompress_takesTheLargestEntryOfAZipAndRefusesOneWithNoFile() throws Exception {
    Path zip = tempDir.resolve("submission.zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
      zipEntry(out, "README.txt", "Read me first.".getBytes());
      out.putNextEntry(new ZipEntry("ontology/"));
      out.closeEntry();
      zipEntry(out, "ontology/doid.owl", Files.readAllBytes(sourceOwl));
      zipEntry(out, "catalog-v001.xml", "<catalog/>".getBytes());
    }
    Path expanded = IngestJob.decompress(zip);
    assertEquals(Files.readString(sourceOwl), Files.readString(expanded));
    try (var listing = Files.list(tempDir)) {
      assertEquals(List.of(), listing.map(p -> p.getFileName().toString()).filter(n -> n.contains(".entry")).toList(),
          "the smaller entries were left beside the archive");
    }

    Path noFile = tempDir.resolve("directories-only.zip");
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(noFile))) {
      out.putNextEntry(new ZipEntry("ontology/"));
      out.closeEntry();
    }
    assertThrows(IOException.class, () -> IngestJob.decompress(noFile));
  }

  /**
   * An argument that looks like an option but is not one is refused. It used to be taken for an
   * acronym, so a typo such as --valueset tried to ingest an ontology of that name and the
   * operation the operator meant never ran.
   */
  /**
   * An OBO file's {@code import:} declarations are dropped before it is parsed, because OWLAPI fetches
   * each one and a single unreachable import fails the whole load. Every other line is kept, in order,
   * in a sibling file, and the download itself is left as it was.
   */
  @Test
  public void stripOboImports_dropsTheImportDeclarationsOfAnOboFile() throws Exception {
    Path obo = tempDir.resolve("ex.obo");
    List<String> original = List.of(
        "format-version: 1.2",
        "ontology: ex",
        "import: http://purl.obolibrary.org/obo/chebi.owl",
        "import: exposure-envo_pattern.owl",
        "",
        "[Term]",
        "id: EX:0000001",
        "name: dog",
        "comment: kept, although it mentions import: in its text",
        "is_a: CHEBI:23367 ! a parent from the import");
    Files.write(obo, original);

    Path stripped = IngestJob.stripOboImports(obo);

    assertEquals(tempDir.resolve("ex.obo.noimports"), stripped);
    assertEquals(List.of(
        "format-version: 1.2",
        "ontology: ex",
        "",
        "[Term]",
        "id: EX:0000001",
        "name: dog",
        "comment: kept, although it mentions import: in its text",
        "is_a: CHEBI:23367 ! a parent from the import"), Files.readAllLines(stripped));
    assertEquals(original, Files.readAllLines(obo), "the download itself was rewritten");
  }

  /**
   * Nothing but an OBO file's import declarations is stripped. An OBO file that imports nothing is
   * parsed as it is, with no copy written. A file in another syntax is not touched even where a line
   * of a literal begins {@code import:}. Its imports are left to the extractor's own policy, and
   * rewriting a literal would change the release's content.
   */
  @Test
  public void stripOboImports_leavesEverythingElseAsItIs() throws Exception {
    Path plain = tempDir.resolve("plain.obo");
    Files.write(plain, List.of("format-version: 1.2", "ontology: ex", "", "[Term]", "id: EX:0000001"));

    assertEquals(plain, IngestJob.stripOboImports(plain));
    assertFalse(Files.exists(tempDir.resolve("plain.obo.noimports")));

    Path owl = tempDir.resolve("other.owl");
    String rdf = """
        <?xml version="1.0"?>
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                 xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
                 xmlns:owl="http://www.w3.org/2002/07/owl#">
          <owl:Ontology rdf:about="http://ex/other">
            <owl:imports rdf:resource="http://purl.obolibrary.org/obo/chebi.owl"/>
            <rdfs:comment>Converted from OBO, whose header read
        import: http://purl.obolibrary.org/obo/chebi.owl</rdfs:comment>
          </owl:Ontology>
        </rdf:RDF>
        """;
    Files.writeString(owl, rdf);

    assertEquals(owl, IngestJob.stripOboImports(owl));
    assertEquals(rdf, Files.readString(owl));
  }

  /**
   * An OBO release that imports a file nobody can fetch still ingests, with its own terms and the
   * hierarchy between them. Here the import names a local file that does not exist.
   */
  @Test
  public void ingest_extractsAnOboReleaseWhoseImportCannotBeFetched() throws Exception {
    Files.write(sourceOwl, List.of(
        "format-version: 1.2",
        "ontology: ex",
        "import: " + tempDir.resolve("missing.owl").toUri(),
        "",
        "[Term]",
        "id: EX:0000001",
        "name: dog",
        "is_a: EX:0000002 ! animal",
        "",
        "[Term]",
        "id: EX:0000002",
        "name: animal"));

    IngestJob.IngestResult r =
        new IngestJob(fakeSource()).ingestLatest(catalog, "EX", tempDir.resolve("snapshots"));

    String obo = "http://purl.obolibrary.org/obo/EX_";
    assertEquals(2, r.classCount());
    try (SnapshotStore store = SnapshotStore.openFile(r.snapshotFile().toString())) {
      assertEquals("dog", store.prefLabel(obo + "0000001").orElseThrow());
      assertEquals(List.of(obo + "0000001"), store.children(obo + "0000002"));
    }
  }

  @Test
  public void anUnrecognizedOptionIsRefusedRatherThanTakenForAnAcronym() {
    assertEquals("DOID", IngestJob.acronymOrRefuse("DOID"));
    IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
        () -> IngestJob.acronymOrRefuse("--valueset"));
    assertTrue(refused.getMessage().contains("--valueset"), refused.getMessage());
    assertTrue(IngestJob.USAGE.contains("--valuesets"), "the usage should list the option meant");
  }

  private static int labelCount(Path snapshot) throws Exception {
    try (SnapshotStore store = SnapshotStore.openFile(snapshot.toString())) {
      return store.labelCount();
    }
  }

  private static int definitionCount(Path snapshot) throws Exception {
    try (SnapshotStore store = SnapshotStore.openFile(snapshot.toString())) {
      return store.definitionCount();
    }
  }

  /** Empties the definition table, leaving the snapshot as one taken before definitions were captured. */
  private static void removeDefinitions(Path snapshot) throws Exception {
    try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + snapshot);
         var statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM definition");
    }
  }

  private static void deleteRecursively(Path dir) throws IOException {
    try (var paths = Files.walk(dir)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(p);
      }
    }
  }

  private static void zipEntry(ZipOutputStream out, String name, byte[] bytes) throws IOException {
    out.putNextEntry(new ZipEntry(name));
    out.write(bytes);
    out.closeEntry();
  }

  /** Adds an IAO definition of one class to the ontology file. */
  private static void addDefinition(Path file, String cls, String text) throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLOntology ont = m.loadOntologyFromOntologyDocument(file.toFile());
    OWLDataFactory df = m.getOWLDataFactory();
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(
        df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115")), iri(cls),
        df.getOWLLiteral(text)));
    try (var out = Files.newOutputStream(file)) {
      m.saveOntology(ont, out);
    }
  }

  /** Empties the label table, leaving the snapshot as one taken before labels were captured. */
  private static void removeLabels(Path snapshot) throws Exception {
    try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + snapshot);
         var statement = connection.createStatement()) {
      statement.executeUpdate("DELETE FROM label");
    }
  }

  private static void buildOntologyWithAnExtraClass(Path file) throws Exception {
    buildOntology(file);
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLOntology ont = m.loadOntologyFromOntologyDocument(file.toFile());
    OWLDataFactory df = m.getOWLDataFactory();
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(df.getOWLClass(iri("bird")), df.getOWLClass(iri("animal"))));
    try (var out = Files.newOutputStream(file)) {
      m.saveOntology(ont, out);
    }
  }

  private static void buildOntology(Path file) throws Exception {
    buildOntology(file, IRI.create(BASE + "test"));
  }

  /** Builds the fixture ontology with {@code ontologyIri} in its header, or with no header IRI when null. */
  private static void buildOntology(Path file, IRI ontologyIri) throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology ont = ontologyIri == null ? m.createOntology() : m.createOntology(ontologyIri);

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
    var event = catalog.ingestHistory("EX").get(0);
    assertEquals("failed", event.status());
    assertEquals(null, event.versionId());
    assertTrue(Files.isRegularFile(Path.of(event.rawFilePath())));
    assertEquals(event.rawFileHash(), PropertyBackfillJob.sha256(Path.of(event.rawFilePath())));
  }

  @Test
  public void auditFailureRollsBackSnapshotRegistrationAndLatestTag() throws Exception {
    Path file = tempDir.resolve("rollback.sqlite");
    try (CatalogStore c = CatalogStore.openFile(file.toString())) {
      c.initSchema();
      var job = new IngestJob(fakeSource());
      var first = job.ingestLatest(c, "EX", tempDir.resolve("snapshots"));
      buildOntologyWithAnExtraClass(sourceOwl);
      try (var db = java.sql.DriverManager.getConnection("jdbc:sqlite:" + file);
           var statement = db.createStatement()) {
        statement.executeUpdate("CREATE TRIGGER refuse_success BEFORE INSERT ON ingest_event "
            + "WHEN NEW.status = 'succeeded' BEGIN SELECT RAISE(ABORT, 'audit refused'); END");
      }
      assertThrows(java.sql.SQLException.class,
          () -> job.ingestLatest(c, "EX", tempDir.resolve("snapshots")));
      assertEquals(first.versionId(), c.resolveLatest("EX").orElseThrow().versionId());
      assertEquals(1, c.listSnapshots("EX").size());
      assertEquals(List.of("succeeded", "failed"),
          c.ingestHistory("EX").stream().map(CatalogStore.IngestEvent::status).toList());
    }
  }

  @Test
  public void retainByHash_namesADownloadForItsBytesAndCompressesIt() throws Exception {
    Path dir = tempDir;
    // Two releases of one ontology, served under the same filename — the collision that lost about a
    // quarter of the store's sources, since the second landed on the first.
    Path raw = dir.resolve("doid.owl");
    String first = "<owl>first release, which is long enough to be worth compressing. "
        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa</owl>";
    Files.writeString(raw, first);
    String firstHash = IngestJob.sha256(raw);
    Path keptFirst = IngestJob.retainByHash(raw, firstHash);

    Files.writeString(raw, "<owl>second release</owl>");
    String secondHash = IngestJob.sha256(raw);
    Path keptSecond = IngestJob.retainByHash(raw, secondHash);

    // Both survive, named for what they are.
    assertTrue(Files.exists(keptFirst));
    assertTrue(Files.exists(keptSecond));
    assertEquals(firstHash + ".gz", keptFirst.getFileName().toString());
    assertNotEquals(keptFirst, keptSecond);
    // Smaller on disk than the text it holds, and readable back to exactly those bytes.
    assertTrue(Files.size(keptFirst) < first.length());
    assertEquals(first, Files.readString(IngestJob.decompress(keptFirst)));

    // Re-downloading a release we already hold keeps the one copy rather than adding a second.
    Files.writeString(raw, first);
    Path again = IngestJob.retainByHash(raw, firstHash);
    assertEquals(keptFirst, again);
    assertFalse(Files.exists(raw));
  }
}
