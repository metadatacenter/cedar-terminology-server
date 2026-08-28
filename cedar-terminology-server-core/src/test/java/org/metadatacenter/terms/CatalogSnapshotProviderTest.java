package org.metadatacenter.terms;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyVersion;
import org.metadatacenter.terms.domainObjects.VersionTriple;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies catalog-backed local resolution: an ontology is served locally only when allowlisted
 * AND present in the catalog, and the resolved store answers the adapter's reads.
 */
public class CatalogSnapshotProviderTest {

  private static final String BASE = "http://ex/";

  private Path tempDir;
  private CatalogStore catalog;
  private CatalogSnapshotProvider provider;

  @BeforeEach
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("provider-test");

    // Build a snapshot file with the synthetic DAG.
    Path snapshotFile = tempDir.resolve("EX_v1.sqlite");
    try (SnapshotStore store = SnapshotStore.openFile(snapshotFile.toString())) {
      store.initSchema();
      for (String[] c : new String[][]{
          {"thing", "Thing"}, {"animal", "Animal"}, {"mammal", "Mammal"},
          {"cat", "Cat"}, {"dog", "Dog"}, {"pet", "Pet"}}) {
        store.addConcept(BASE + c[0], c[1]);
      }
      store.addEdge(BASE + "animal", BASE + "thing", "rdfs:subClassOf");
      store.addEdge(BASE + "mammal", BASE + "animal", "rdfs:subClassOf");
      store.addEdge(BASE + "cat", BASE + "mammal", "rdfs:subClassOf");
      store.addEdge(BASE + "dog", BASE + "mammal", "rdfs:subClassOf");
      store.addEdge(BASE + "pet", BASE + "thing", "rdfs:subClassOf");
      store.addEdge(BASE + "dog", BASE + "pet", "rdfs:subClassOf");
      store.materialize();
    }

    // Register it in a catalog on disk.
    Path catalogFile = tempDir.resolve("catalog.sqlite");
    catalog = CatalogStore.openFile(catalogFile.toString());
    catalog.initSchema();
    catalog.upsertOntology(new CatalogStore.OntologyInfo("EX", "Example", null, "OWL"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("v1", "EX", "1.0", "2025-01-01", "2025-01-02T00:00:00Z",
        "OWL", "subsumption", 6, 6, snapshotFile.toString(), "v1", "open"));
    catalog.setTag("EX", CatalogStore.TAG_LATEST, "v1");

    // A second, non-latest version v2 that adds "wolf" under mammal — for version-resolution tests.
    Path v2File = tempDir.resolve("EX_v2.sqlite");
    try (SnapshotStore store = SnapshotStore.openFile(v2File.toString())) {
      store.initSchema();
      for (String[] c : new String[][]{
          {"thing", "Thing"}, {"animal", "Animal"}, {"mammal", "Mammal"},
          {"cat", "Cat"}, {"dog", "Dog"}, {"pet", "Pet"}, {"wolf", "Wolf"}}) {
        store.addConcept(BASE + c[0], c[1]);
      }
      store.addEdge(BASE + "animal", BASE + "thing", "rdfs:subClassOf");
      store.addEdge(BASE + "mammal", BASE + "animal", "rdfs:subClassOf");
      store.addEdge(BASE + "cat", BASE + "mammal", "rdfs:subClassOf");
      store.addEdge(BASE + "dog", BASE + "mammal", "rdfs:subClassOf");
      store.addEdge(BASE + "wolf", BASE + "mammal", "rdfs:subClassOf");
      store.materialize();
    }
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("v2", "EX", "2.0", "2025-06-01", "2025-06-02T00:00:00Z",
        "OWL", "subsumption", 7, 7, v2File.toString(), "v2", "open")); // latest stays v1
    // Record EX's namespace so a class IRI can be mapped back to it (A6 raw_namespace reverse lookup).
    catalog.setOntologyIri("EX", "http://ex", "http://ex/");

    // Allowlist EX and OTHER; only EX is actually ingested.
    provider = new CatalogSnapshotProvider(catalog, Set.of("EX", "OTHER"));
  }

  @AfterEach
  public void tearDown() throws Exception {
    provider.close(); // also closes the catalog
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

  @Test
  public void snapshotsNobodyHasAskedForAreClosedOnceTooManyAreOpen() throws Exception {
    // Left alone, every snapshot ever resolved stays open for the life of the process, so the
    // ceiling is the corpus rather than the working set. One open at a time here, and nothing may be
    // closed until it has gone unasked-for, which the property shortens to no time at all.
    System.setProperty("cedar.terminology.openSnapshots.max", "1");
    System.setProperty("cedar.terminology.openSnapshots.quietMillis", "0");
    try {
      Path dir = Files.createTempDirectory("evict");
      try (CatalogStore cat = CatalogStore.openFile(dir.resolve("catalog.sqlite").toString())) {
        cat.initSchema();
        for (String acronym : List.of("A", "B")) {
          Path file = dir.resolve(acronym + ".sqlite");
          try (SnapshotStore store = SnapshotStore.openFile(file.toString())) {
            store.initSchema();
            store.addConcept("http://x/" + acronym, acronym);
            store.materialize();
          }
          cat.upsertOntology(new CatalogStore.OntologyInfo(acronym, acronym, null, "OWL"));
          cat.addSnapshot(new CatalogStore.SnapshotInfo(acronym + "-v1", acronym, "1.0", "2025-01-01",
              "2025-01-02T00:00:00Z", "OWL", "subsumption", 1, 1, file.toString(), acronym + "-v1", "open"));
          cat.setTag(acronym, CatalogStore.TAG_LATEST, acronym + "-v1");
        }
        CatalogSnapshotProvider provider = new CatalogSnapshotProvider(cat, Set.of("A", "B"));
        assertTrue(provider.forOntology("A").isPresent());
        assertTrue(provider.forOntology("B").isPresent());
        // Asking again is what notices A has gone quiet; A is closed rather than kept for ever.
        assertTrue(provider.forOntology("B").isPresent());
        assertEquals(1, provider.openSnapshotCount(), "the quiet one was closed");
      }
    } finally {
      System.clearProperty("cedar.terminology.openSnapshots.max");
      System.clearProperty("cedar.terminology.openSnapshots.quietMillis");
    }
  }

  @Test
  public void resolvesAllowlistedAndIngested() throws Exception {
    var store = provider.forOntology("EX");
    assertTrue(store.isPresent());
    assertEquals(2, store.get().children(BASE + "mammal").size());
  }

  @Test
  public void rejectsNotAllowlisted() {
    assertTrue(provider.forOntology("NOPE").isEmpty());
    assertTrue(provider.forOntology(null).isEmpty());
  }

  @Test
  public void rejectsAllowlistedButNotIngested() {
    assertTrue(provider.forOntology("OTHER").isEmpty());
  }

  @Test
  public void cachesTheOpenStorePerVersion() {
    SnapshotStore first = provider.forOntology("EX").orElseThrow();
    SnapshotStore second = provider.forOntology("EX").orElseThrow();
    assertSame(first, second);
  }

  @Test
  public void resolvesAPinnedVersionIndependentOfLatest() throws Exception {
    // latest is v1 (no wolf). Pinning v2 serves the v2 snapshot (wolf present); pinning v1 or the
    // "latest" tag serves v1. This is reproducible resolution independent of where latest points.
    assertTrue(provider.forOntology("EX").orElseThrow().prefLabel(BASE + "wolf").isEmpty());
    assertTrue(provider.forOntology("EX", "v2").orElseThrow().prefLabel(BASE + "wolf").isPresent());
    assertTrue(provider.forOntology("EX", "v1").orElseThrow().prefLabel(BASE + "wolf").isEmpty());
    assertTrue(provider.forOntology("EX", "latest").orElseThrow().prefLabel(BASE + "wolf").isEmpty());
  }

  @Test
  public void listsVersionsWithLatestMarked() {
    List<OntologyVersion> vs = provider.versions("EX");
    assertEquals(2, vs.size());
    assertEquals(1, vs.stream().filter(OntologyVersion::latest).count());
    assertTrue(vs.stream().anyMatch(v -> v.versionId().equals("v1") && v.latest()));
    assertTrue(vs.stream().anyMatch(v -> v.versionId().equals("v2") && !v.latest()));
  }

  @Test
  public void listedVersionsCarryTheFullTriple() {
    // Each entry surfaces effectiveDate (the release day) alongside id and declaredVersion, so a
    // /versions listing and a resolve-current agree on the same triple. v1 released 2025-01-01.
    OntologyVersion v1 = provider.versions("EX").stream()
        .filter(v -> v.versionId().equals("v1")).findFirst().orElseThrow();
    assertEquals("1.0", v1.version());
    assertEquals("2025-01-01", v1.effectiveDate());
  }

  @Test
  public void unknownVersionResolvesEmpty() {
    assertTrue(provider.forOntology("EX", "no-such-version").isEmpty());
  }

  @Test
  public void resolvesByAsOfDate() throws Exception {
    // v1 released 2025-01-01 (no wolf), v2 released 2025-06-01 (wolf). "As of" a date serves the
    // newest snapshot published on or before it, independent of where latest (v1) points.
    assertTrue(provider.forOntology("EX", "2025-03-01").orElseThrow().prefLabel(BASE + "wolf").isEmpty());
    assertTrue(provider.forOntology("EX", "2025-07-01").orElseThrow().prefLabel(BASE + "wolf").isPresent());
  }

  @Test
  public void asOfDateBeforeAllHistoryResolvesEmpty() {
    // A pin we cannot honor fails to empty (caller falls back to remote), never silently to latest.
    assertTrue(provider.forOntology("EX", "2024-01-01").isEmpty());
  }

  @Test
  public void resolvesByDeclaredVersionLabel() throws Exception {
    // The free-form declared version resolves its snapshot: "2.0" is v2 (wolf), "1.0" is v1.
    assertTrue(provider.forOntology("EX", "2.0").orElseThrow().prefLabel(BASE + "wolf").isPresent());
    assertTrue(provider.forOntology("EX", "1.0").orElseThrow().prefLabel(BASE + "wolf").isEmpty());
  }

  @Test
  public void ambiguousDeclaredVersionServesTheNewest() throws Exception {
    // Two snapshots sharing declared version "2.0": the newer (later release) wins; a warning is
    // logged (not asserted here). This mirrors INCENTIVE publishing several submissions under one
    // label. Register a second "2.0" that is newer than v2 and adds "fox".
    Path v3File = tempDir.resolve("EX_v3.sqlite");
    try (SnapshotStore store = SnapshotStore.openFile(v3File.toString())) {
      store.initSchema();
      store.addConcept(BASE + "thing", "Thing");
      store.addConcept(BASE + "fox", "Fox");
      store.addEdge(BASE + "fox", BASE + "thing", "rdfs:subClassOf");
      store.materialize();
    }
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("v3", "EX", "2.0", "2025-09-01", "2025-09-02T00:00:00Z",
        "OWL", "subsumption", 2, 1, v3File.toString(), "v3", "open"));

    assertTrue(provider.forOntology("EX", "2.0").orElseThrow().prefLabel(BASE + "fox").isPresent());
  }

  @Test
  public void resolvesCurrentVersionTripleForLatest() {
    // latest is v1: released 2025-01-01, declared "1.0", content-hash id "v1".
    VersionTriple t = provider.currentVersion("EX").orElseThrow();
    assertEquals("v1", t.id());
    assertEquals("2025-01-01", t.effectiveDate());
    assertEquals("1.0", t.declaredVersion());
  }

  @Test
  public void currentVersionEmptyWhenNotServed() {
    assertTrue(provider.currentVersion("OTHER").isEmpty()); // allowlisted but never ingested
    assertTrue(provider.currentVersion("NOPE").isEmpty());  // not allowlisted
    assertTrue(provider.currentVersion(null).isEmpty());
  }

  @Test
  public void resolvesOntologyForConceptIriByNamespace() {
    assertEquals("EX", provider.ontologyForConceptIri("http://ex/wolf").orElseThrow());
    assertTrue(provider.ontologyForConceptIri("http://other/thing").isEmpty()); // unknown namespace
    assertTrue(provider.ontologyForConceptIri(null).isEmpty());
  }

  @Test
  public void resolvesCurrentVersionForClassViaTheService() throws Exception {
    // A class IRI -> its ontology (EX, by namespace) -> EX's current triple (latest is v1).
    SqliteTerminologyService service = new SqliteTerminologyService(provider);
    assertEquals("v1", service.resolveCurrentVersionForClass("http://ex/wolf").id());
    assertNull(service.resolveCurrentVersionForClass("http://other/x")); // namespace not ours
  }

  @Test
  public void resolvesCurrentVersionForValueSetCollection() throws Exception {
    // A value-set collection MYVS, ingested and marked as such, resolves its current triple — even
    // though it is NOT in the ontology serving allowlist (Set.of("EX","OTHER")). Value-set-collection
    // version resolution gates on the catalog's kind marker, not the search/browse allowlist.
    catalog.upsertOntology(new CatalogStore.OntologyInfo("MYVS", "My Value Sets", null, "SKOS"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("vs1", "MYVS", "2024-05-01", "2024-05-01",
        "2024-05-02T00:00:00Z", "SKOS", "subsumption", 3, 2, "/snapshots/MYVS/vs1.sqlite", "vs1", "open"));
    catalog.setTag("MYVS", CatalogStore.TAG_LATEST, "vs1");
    catalog.setOntologyKind("MYVS", CatalogStore.KIND_VALUE_SET_COLLECTION);

    VersionTriple triple = provider.currentVersionForValueSetCollection("MYVS").orElseThrow();
    assertEquals("vs1", triple.id());
    assertEquals("2024-05-01", triple.effectiveDate());

    // An ordinary ontology (EX) is not a value-set collection: the kind guard declines, so an
    // ontology of the same acronym can never answer here.
    assertTrue(provider.currentVersionForValueSetCollection("EX").isEmpty());
    assertTrue(provider.currentVersionForValueSetCollection("NOPE").isEmpty());
    assertTrue(provider.currentVersionForValueSetCollection(null).isEmpty());

    // End-to-end through the service: the freeze capability behind currentVersionByValueSetCollection.
    SqliteTerminologyService service = new SqliteTerminologyService(provider);
    assertEquals("vs1", service.resolveCurrentVersionForValueSetCollection("MYVS").id());
    assertNull(service.resolveCurrentVersionForValueSetCollection("EX"));   // an ontology, not a collection
    assertNull(service.resolveCurrentVersionForValueSetCollection("NOPE")); // unknown
  }

  @Test
  public void serviceReturnsNullTripleWhenNotServedLocally() throws Exception {
    // The ITerminologyService contract: null (not an empty Optional) signals "cannot freeze here",
    // which the publish pipeline reads as "defer to remote / leave unpinned".
    SqliteTerminologyService service = new SqliteTerminologyService(provider);
    assertEquals("v1", service.resolveCurrentVersion("EX").id());
    assertNull(service.resolveCurrentVersion("OTHER"));
  }

  @Test
  public void tripleEffectiveDateFallsBackToIngestDateAndTruncatesToDay() {
    // A source release date is truncated to its calendar day.
    VersionTriple withRelease = CatalogSnapshotProvider.toTriple(new CatalogStore.SnapshotInfo(
        "h", "EX", "9.9", "2025-06-01T00:00:00.000+00:00", "2026-07-29T02:56:36.324676Z",
        "OWL", "subsumption", 1, 1, "/x", "h", "open"));
    assertEquals("2025-06-01", withRelease.effectiveDate());
    // No source release date -> the ingest timestamp stands in, likewise truncated. Label may be null.
    VersionTriple noRelease = CatalogSnapshotProvider.toTriple(new CatalogStore.SnapshotInfo(
        "h", "EX", null, null, "2026-07-29T02:56:36.324676Z",
        "OWL", "subsumption", 1, 1, "/x", "h", "open"));
    assertEquals("2026-07-29", noRelease.effectiveDate());
    assertNull(noRelease.declaredVersion());
  }

  @Test
  public void asOfDateHelperExtractsLeadingCalendarDate() {
    assertEquals("2022-06-26", CatalogSnapshotProvider.asOfDate("2022-06-26").orElseThrow());
    assertEquals("2022-06-26", CatalogSnapshotProvider.asOfDate("2022-06-26T18:07:50.000-07:00").orElseThrow());
    assertTrue(CatalogSnapshotProvider.asOfDate("2024-13-40").isEmpty()); // not a real date
    assertTrue(CatalogSnapshotProvider.asOfDate("2.0").isEmpty());        // a declared-version label
    assertTrue(CatalogSnapshotProvider.asOfDate("ff05a9a36f3a").isEmpty()); // a content hash
    assertTrue(CatalogSnapshotProvider.asOfDate(null).isEmpty());
  }

  @Test
  public void servedThroughTheAdapter() throws Exception {
    SqliteTerminologyService service = new SqliteTerminologyService(provider);
    assertTrue(service.isAvailable("EX"));
    assertFalse(service.isAvailable("OTHER"));
    var children = service.getClassChildren(BASE + "mammal", "EX", 1, 50, null);
    assertEquals(java.util.List.of("cat", "dog"),
        children.getCollection().stream().map(c -> c.getId()).collect(Collectors.toList()));
  }

  @Test
  public void listsAllowlistedCatalogOntologiesAsMetadata() {
    // Only EX is both allowlisted and in the catalog; OTHER is allowlisted but never ingested.
    List<Ontology> onts = provider.ontologies();
    assertEquals(1, onts.size());
    Ontology ex = onts.get(0);
    assertEquals("EX", ex.getId());
    assertEquals("Example", ex.getName());
    assertFalse( ex.getIsFlat(),"a hierarchical snapshot is not flat");
    assertEquals("https://data.bioontology.org/ontologies/EX", ex.getLdId());
  }

  @Test
  public void findAllOntologiesReportsTheCatalogNotBioPortal() throws Exception {
    SqliteTerminologyService service = new SqliteTerminologyService(provider);
    assertEquals(List.of("EX"),
        service.findAllOntologies(true, null).stream().map(Ontology::getId).collect(Collectors.toList()));
    assertEquals("EX", service.findOntology("EX", false, null).getId());
  }
}
