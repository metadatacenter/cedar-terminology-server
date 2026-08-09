package org.metadatacenter.terms.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.metadatacenter.terms.store.CatalogStore.OntologyInfo;
import org.metadatacenter.terms.store.CatalogStore.SnapshotInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CatalogStoreTest {

  private CatalogStore catalog;

  private static SnapshotInfo doidSnapshot(String versionId, String released) {
    return new SnapshotInfo(versionId, "DOID", "release/" + released, released, "2026-07-24T00:00:00Z",
        "OWL", "subsumption", 14000, 20000, "/snapshots/DOID/" + versionId + ".sqlite", versionId, "open");
  }

  @BeforeEach
  public void setUp() throws Exception {
    catalog = CatalogStore.openInMemory();
    catalog.initSchema();
    catalog.upsertOntology(new OntologyInfo("DOID", "Human Disease Ontology",
        "http://purl.obolibrary.org/obo/doid.owl", "OWL"));
    catalog.addSnapshot(doidSnapshot("hashV1", "2025-01-01"));
    catalog.addSnapshot(doidSnapshot("hashV2", "2025-06-01"));
    catalog.setTag("DOID", CatalogStore.TAG_LATEST, "hashV2");
  }

  @AfterEach
  public void tearDown() throws Exception {
    catalog.close();
  }

  @Test
  public void resolveLatest_followsTheTag() throws Exception {
    Optional<SnapshotInfo> latest = catalog.resolveLatest("DOID");
    assertTrue(latest.isPresent());
    assertEquals("hashV2", latest.get().versionId());
    assertEquals("subsumption", latest.get().hierarchyStatus());
    assertEquals("/snapshots/DOID/hashV2.sqlite", latest.get().filePath());
  }

  @Test
  public void tagFlipIsAtomicAndRepeatable() throws Exception {
    catalog.setTag("DOID", CatalogStore.TAG_LATEST, "hashV1");
    assertEquals("hashV1", catalog.resolveLatest("DOID").orElseThrow().versionId());
    catalog.setTag("DOID", CatalogStore.TAG_LATEST, "hashV2");
    assertEquals("hashV2", catalog.resolveLatest("DOID").orElseThrow().versionId());
  }

  @Test
  public void upsertOntology_doesNotDowngradeARealNameToTheAcronym() throws Exception {
    // A re-ingest from a title-less source (name falls back to the acronym) must not wipe a real name.
    catalog.upsertOntology(new OntologyInfo("DOID", "DOID", "http://purl.obolibrary.org/obo/doid.owl", "OWL"));
    assertEquals("Human Disease Ontology", catalog.ontologyName("DOID"));

    // A first ingest with only the acronym legitimately stores the acronym...
    catalog.upsertOntology(new OntologyInfo("FOO", "FOO", "http://ex.org/foo", "OWL"));
    assertEquals("FOO", catalog.ontologyName("FOO"));
    // ...a later ingest that does carry a title sets it...
    catalog.upsertOntology(new OntologyInfo("FOO", "The Foo Ontology", "http://ex.org/foo", "OWL"));
    assertEquals("The Foo Ontology", catalog.ontologyName("FOO"));
    // ...and a subsequent title-less re-ingest keeps it.
    catalog.upsertOntology(new OntologyInfo("FOO", "FOO", "http://ex.org/foo", "OWL"));
    assertEquals("The Foo Ontology", catalog.ontologyName("FOO"));
  }

  @Test
  public void inTransaction_rollsBackEveryWriteOnFailure() throws Exception {
    // The atomicity a crash mid-ingest relies on: a unit of work that writes and then fails must leave
    // the catalog exactly as it was — the tag flip and the new snapshot are both rolled back.
    assertThrows(java.sql.SQLException.class, () -> catalog.inTransaction(() -> {
      catalog.setTag("DOID", CatalogStore.TAG_LATEST, "hashV1"); // would move latest off hashV2
      catalog.addSnapshot(doidSnapshot("hashV3", "2025-09-01"));  // would register a third snapshot
      throw new java.sql.SQLException("simulated crash mid-registration");
    }));
    assertEquals("hashV2", catalog.resolveLatest("DOID").orElseThrow().versionId());
    assertTrue(catalog.getSnapshot("hashV3").isEmpty());
    assertEquals(2, catalog.listSnapshots("DOID").size());
  }

  @Test
  public void inTransaction_commitsEveryWriteOnSuccess() throws Exception {
    catalog.inTransaction(() -> {
      catalog.addSnapshot(doidSnapshot("hashV3", "2025-09-01"));
      catalog.setTag("DOID", CatalogStore.TAG_LATEST, "hashV3");
    });
    assertEquals("hashV3", catalog.resolveLatest("DOID").orElseThrow().versionId());
    assertEquals(3, catalog.listSnapshots("DOID").size());
  }

  @Test
  public void getSnapshotByVersionId() throws Exception {
    SnapshotInfo v1 = catalog.getSnapshot("hashV1").orElseThrow();
    assertEquals("DOID", v1.acronym());
    assertEquals("2025-01-01", v1.releasedAt());
    assertEquals(Integer.valueOf(14000), v1.classCount());
  }

  @Test
  public void listSnapshots_orderedByRelease() throws Exception {
    List<SnapshotInfo> snaps = catalog.listSnapshots("DOID");
    assertEquals(2, snaps.size());
    assertEquals("hashV1", snaps.get(0).versionId());
    assertEquals("hashV2", snaps.get(1).versionId());
  }

  @Test
  public void listOntologies() throws Exception {
    List<OntologyInfo> onts = catalog.listOntologies();
    assertEquals(1, onts.size());
    assertEquals("DOID", onts.get(0).acronym());
  }

  @Test
  public void addSnapshotIsIdempotentOnVersionId() throws Exception {
    // Re-adding the same version_id (e.g. a re-run backfill) updates rather than failing.
    catalog.addSnapshot(new SnapshotInfo("hashV1", "DOID", "release/updated", "2025-01-01",
        "2026-01-01T00:00:00Z", "OWL", "subsumption", 15000, 21000,
        "/snapshots/DOID/hashV1.sqlite", "hashV1", "open"));
    assertEquals(2, catalog.listSnapshots("DOID").size()); // still two, not three
    assertEquals("release/updated", catalog.getSnapshot("hashV1").orElseThrow().declaredVersion());
  }

  @Test
  public void twoOntologiesSharingAContentHashKeepSeparateSnapshots() throws Exception {
    // INCENTIVE and INCENTIVE-VARS resolve to the same SKOS file on BioPortal, so both ingest to the
    // same content hash. Each must keep its own snapshot row (keyed on (version_id, acronym)) and its
    // tag must resolve to its own row, not the other ontology's.
    catalog.upsertOntology(new OntologyInfo("INCENTIVE", "INCENTIVE", null, "SKOS"));
    catalog.upsertOntology(new OntologyInfo("INCENTIVE-VARS", "INCENTIVE-VARS", null, "SKOS"));
    String shared = "sharedhash";
    catalog.addSnapshot(new SnapshotInfo(shared, "INCENTIVE", "1.0", "2025-01-01",
        "2026-01-01T00:00:00Z", "SKOS", "subsumption", 81, 75,
        "/snapshots/INCENTIVE/" + shared + ".sqlite", shared, "open"));
    catalog.addSnapshot(new SnapshotInfo(shared, "INCENTIVE-VARS", "1.0", "2025-01-01",
        "2026-01-01T00:00:00Z", "SKOS", "subsumption", 81, 75,
        "/snapshots/INCENTIVE-VARS/" + shared + ".sqlite", shared, "open"));
    catalog.setTag("INCENTIVE", CatalogStore.TAG_LATEST, shared);
    catalog.setTag("INCENTIVE-VARS", CatalogStore.TAG_LATEST, shared);

    // Adding the second did not overwrite the first: both rows survive.
    assertEquals(1, catalog.listSnapshots("INCENTIVE").size());
    assertEquals(1, catalog.listSnapshots("INCENTIVE-VARS").size());
    // Each tag resolves to that ontology's own snapshot (its own file), despite the shared hash.
    assertEquals("/snapshots/INCENTIVE/" + shared + ".sqlite",
        catalog.resolveLatest("INCENTIVE").orElseThrow().filePath());
    assertEquals("/snapshots/INCENTIVE-VARS/" + shared + ".sqlite",
        catalog.resolveLatest("INCENTIVE-VARS").orElseThrow().filePath());
  }

  @Test
  public void snapshotInfoRoundTrips() throws Exception {
    // Every field survives persist -> reload (record equality covers all 12 columns).
    SnapshotInfo info = new SnapshotInfo("rt", "DOID", "release/x", "2025-03-03", "2025-03-04T00:00:00Z",
        "OWL", "subsumption", 111, 222, "/snapshots/DOID/rt.sqlite", "rt", "public");
    catalog.addSnapshot(info);
    assertEquals(info, catalog.getSnapshot("rt").orElseThrow());
  }

  @Test
  public void ontologyInfoRoundTrips() throws Exception {
    OntologyInfo o = new OntologyInfo("ONT2", "Ontology Two", "http://example.org/ont2", "SKOS");
    catalog.upsertOntology(o);
    assertTrue(catalog.listOntologies().contains(o));
  }

  @Test
  public void unknownResolutionsAreEmpty() throws Exception {
    assertTrue(catalog.resolveLatest("NCIT").isEmpty());
    assertTrue(catalog.getSnapshot("nope").isEmpty());
  }

  /* --------------------------------------------------------------------------------------------
   * Date and declared-version resolution — fixtures mirror INCENTIVE's real BioPortal history:
   * three submissions labelled 0.1.1 (two on the same day), then 0.1.2 and 0.1.3 on the same day,
   * then a second 0.1.3 seventeen months later. Timestamps carry the real varying UTC offsets.
   * ------------------------------------------------------------------------------------------ */

  private static SnapshotInfo incentive(String vid, String declared, String released, String ingested) {
    return new SnapshotInfo(vid, "INCENTIVE", declared, released, ingested, "SKOS", "subsumption",
        81, 75, "/snapshots/INCENTIVE/" + vid + ".sqlite", vid, "open");
  }

  private void loadIncentiveHistory() throws Exception {
    catalog.upsertOntology(new OntologyInfo("INCENTIVE", "INCENTIVE", null, "SKOS"));
    catalog.addSnapshot(incentive("v0_1_1a", "0.1.1", "2022-06-25T00:00:00.000+00:00", "2026-07-29T02:56:35.412653Z"));
    catalog.addSnapshot(incentive("v0_1_1b", "0.1.1", "2022-06-26T00:00:00.000+00:00", "2026-07-29T02:56:35.632712Z"));
    catalog.addSnapshot(incentive("v0_1_1c", "0.1.1", "2022-06-26T18:07:50.000-07:00", "2026-07-29T02:56:35.507962Z"));
    catalog.addSnapshot(incentive("v0_1_2", "0.1.2", "2022-06-28T00:00:00.000+00:00", "2026-07-29T02:56:35.807427Z"));
    catalog.addSnapshot(incentive("v0_1_3a", "0.1.3", "2022-06-28T00:00:00.000+00:00", "2026-07-29T02:56:35.976592Z"));
    catalog.addSnapshot(incentive("v0_1_3b", "0.1.3", "2023-11-23T00:00:00.000+00:00", "2026-07-29T02:56:36.324676Z"));
  }

  @Test
  public void resolveAsOfDate_picksNewestReleasedOnOrBeforeTheDate() throws Exception {
    loadIncentiveHistory();
    // On 06-27 the newest release on or before is the evening 06-26 submission.
    assertEquals("v0_1_1c", catalog.resolveAsOfDate("INCENTIVE", "2022-06-27").orElseThrow().versionId());
    // A year and a half later, the 0.1.3 re-release is current.
    assertEquals("v0_1_3b", catalog.resolveAsOfDate("INCENTIVE", "2024-01-01").orElseThrow().versionId());
  }

  @Test
  public void resolveAsOfDate_isInclusiveOfTheReleaseDay() throws Exception {
    loadIncentiveHistory();
    // Asking as of 06-25 includes the snapshot released that very day (day-granular, inclusive).
    assertEquals("v0_1_1a", catalog.resolveAsOfDate("INCENTIVE", "2022-06-25").orElseThrow().versionId());
  }

  @Test
  public void resolveAsOfDate_beforeAllHistoryIsEmpty() throws Exception {
    loadIncentiveHistory();
    assertTrue(catalog.resolveAsOfDate("INCENTIVE", "2022-06-24").isEmpty());
  }

  @Test
  public void resolveAsOfDate_breaksSameDayTiesDeterministically() throws Exception {
    loadIncentiveHistory();
    // 0.1.2 and 0.1.3 share the 06-28 release date; the later-ingested (0.1.3) wins the tie.
    assertEquals("v0_1_3a", catalog.resolveAsOfDate("INCENTIVE", "2022-06-28").orElseThrow().versionId());
  }

  @Test
  public void resolveByDeclaredVersion_returnsEveryMatchNewestFirst() throws Exception {
    loadIncentiveHistory();
    List<SnapshotInfo> ones = catalog.resolveByDeclaredVersion("INCENTIVE", "0.1.1");
    assertEquals(3, ones.size()); // the label is not unique
    assertEquals("v0_1_1c", ones.get(0).versionId()); // newest of the three (06-26 evening)
  }

  @Test
  public void resolveByDeclaredVersion_uniqueLabelResolvesToTheOneSnapshot() throws Exception {
    loadIncentiveHistory();
    List<SnapshotInfo> two = catalog.resolveByDeclaredVersion("INCENTIVE", "0.1.2");
    assertEquals(1, two.size());
    assertEquals("v0_1_2", two.get(0).versionId());
  }

  @Test
  public void resolveByDeclaredVersion_unknownLabelIsEmpty() throws Exception {
    loadIncentiveHistory();
    assertTrue(catalog.resolveByDeclaredVersion("INCENTIVE", "9.9").isEmpty());
  }

  @Test
  public void ontologyIri_storesCanonicalAndProvenanceRoundTrip() throws Exception {
    // Unset until derived.
    assertTrue(catalog.ontologyIri("DOID").isEmpty());
    catalog.setOntologyIri("DOID", "http://purl.obolibrary.org/obo/doid",
        "http://purl.obolibrary.org/obo/DOID_");
    assertEquals("http://purl.obolibrary.org/obo/doid", catalog.ontologyIri("DOID").orElseThrow());
    // Re-deriving overwrites rather than duplicating.
    catalog.setOntologyIri("DOID", "http://purl.obolibrary.org/obo/doid2", "raw2");
    assertEquals("http://purl.obolibrary.org/obo/doid2", catalog.ontologyIri("DOID").orElseThrow());
  }

  @Test
  public void ontologyIri_unknownAcronymIsEmpty() throws Exception {
    assertTrue(catalog.ontologyIri("NOPE").isEmpty());
  }

  @Test
  public void acronymsForIri_joinsTheSameOntologyAcrossSources() throws Exception {
    // The canonical iri is content-derived, so one ontology held under two acronyms (as two
    // authorities might label it) shares one iri; acronymsForIri returns both, the join acronym cannot
    // make. DOID's own acronym has no iri yet, so it is not returned.
    catalog.upsertOntology(new OntologyInfo("DO", "Disease Ontology", null, "OWL"));
    catalog.upsertOntology(new OntologyInfo("HUMAN-DO", "Human Disease Ontology", null, "OWL"));
    String iri = "http://purl.obolibrary.org/obo/doid";
    catalog.setOntologyIri("DO", iri, "http://purl.obolibrary.org/obo/DOID_");
    catalog.setOntologyIri("HUMAN-DO", iri, "http://purl.obolibrary.org/obo/DOID_");

    assertEquals(List.of("DO", "HUMAN-DO"), catalog.acronymsForIri(iri)); // both, ascending
    assertEquals(List.of(), catalog.acronymsForIri("http://purl.obolibrary.org/obo/unknown"));
    assertEquals(List.of(), catalog.acronymsForIri(null));
    // Both acronyms map to one canonical identity row.
    assertEquals(List.of(iri), catalog.listOntologyIdentities());
  }

  @Test
  public void resolveLatestByIri_spansSourcesAndPicksNewest() throws Exception {
    // Identity is the iri, so a lookup by iri spans every source-acronym that holds the ontology. Here
    // DOID (from setUp, latest hashV2 released 2025-06-01) and a second source DOID-OBO (latest hashObo
    // released 2026-01-01) share one iri; resolveLatestByIri returns the newest across both, while
    // resolveLatest stays scoped to a single acronym.
    String iri = "http://purl.obolibrary.org/obo/doid";
    catalog.setOntologyIri("DOID", iri, "http://purl.obolibrary.org/obo/DOID_");
    catalog.upsertOntology(new OntologyInfo("DOID-OBO", "DOID via OBO Foundry", null, "OWL"));
    catalog.setOntologyIri("DOID-OBO", iri, "http://purl.obolibrary.org/obo/DOID_");
    catalog.addSnapshot(new SnapshotInfo("hashObo", "DOID-OBO", "2026-01-01", "2026-01-01",
        "2026-01-02T00:00:00Z", "OWL", "subsumption", 1, 0, "/snapshots/DOID-OBO/hashObo.sqlite",
        "hashObo", "open"));
    catalog.setTag("DOID-OBO", CatalogStore.TAG_LATEST, "hashObo");

    assertEquals("hashObo", catalog.resolveLatestByIri(iri).orElseThrow().versionId()); // newest source wins
    assertEquals("hashV2", catalog.resolveLatest("DOID").orElseThrow().versionId());    // acronym-scoped
    assertTrue(catalog.resolveLatestByIri("http://purl.obolibrary.org/obo/unknown").isEmpty());
  }

  @Test
  public void reKey_migratesAcronymKeyedCatalogToIriIdentityPlusSource(@TempDir Path dir) throws Exception {
    // A pre-re-key catalog: the old single acronym-keyed ontology table, iri already derived on its
    // rows. Opening it must split into the iri-keyed identity table + the acronym-keyed source table,
    // preserving every acronym read and building one identity row per distinct iri.
    Path dbFile = dir.resolve("prekey.sqlite");
    try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile);
         var s = conn.createStatement()) {
      s.executeUpdate("CREATE TABLE ontology (acronym TEXT PRIMARY KEY, name TEXT NOT NULL, "
          + "source_iri TEXT, default_format TEXT, iri TEXT, raw_namespace TEXT, "
          + "kind TEXT NOT NULL DEFAULT 'ontology')");
      // Two source-acronyms of one ontology (same iri), one distinct value-set collection, one with no iri.
      s.executeUpdate("INSERT INTO ontology VALUES ('DOID','Disease Ontology',null,'OWL',"
          + "'http://purl.obolibrary.org/obo/doid','http://purl.obolibrary.org/obo/DOID_','ontology')");
      s.executeUpdate("INSERT INTO ontology VALUES ('HUMANDO','Human DO',null,'OWL',"
          + "'http://purl.obolibrary.org/obo/doid','http://purl.obolibrary.org/obo/DOID_','ontology')");
      s.executeUpdate("INSERT INTO ontology VALUES ('CEDARVS','Value Sets',null,'SKOS',"
          + "'http://x/cedarvs','http://x/cedarvs/','value_set_collection')");
      s.executeUpdate("INSERT INTO ontology VALUES ('LC-CARRIERS','Empty',null,'OWL',null,null,'ontology')");
    }
    try (CatalogStore migrated = CatalogStore.openFile(dbFile.toString())) {
      migrated.initSchema();
      // Every acronym read is preserved through the split.
      assertEquals("http://purl.obolibrary.org/obo/doid", migrated.ontologyIri("DOID").orElseThrow());
      assertTrue(migrated.isValueSetCollection("CEDARVS"));
      assertFalse(migrated.isValueSetCollection("DOID"));
      assertTrue(migrated.ontologyIri("LC-CARRIERS").isEmpty()); // a null iri stays null, no identity row
      // The two acronyms of one ontology join by iri.
      assertEquals(List.of("DOID", "HUMANDO"),
          migrated.acronymsForIri("http://purl.obolibrary.org/obo/doid"));
      // One identity row per distinct iri (LC-CARRIERS contributes none).
      assertEquals(List.of("http://purl.obolibrary.org/obo/doid", "http://x/cedarvs"),
          migrated.listOntologyIdentities());
    }
  }

  @Test
  public void acronymForNamespace_resolvesTheUniqueOwner() throws Exception {
    catalog.setOntologyIri("DOID", "http://purl.obolibrary.org/obo/doid",
        "http://purl.obolibrary.org/obo/DOID_");
    assertEquals("DOID",
        catalog.acronymForNamespace("http://purl.obolibrary.org/obo/DOID_").orElseThrow());
    assertTrue(catalog.acronymForNamespace("http://unknown.example/").isEmpty());
    assertTrue(catalog.acronymForNamespace(null).isEmpty());
  }

  @Test
  public void acronymForNamespace_ambiguousNamespaceResolvesEmpty() throws Exception {
    // Two ontologies share a generic base (as webprotege-hosted ones do) — the owner can't be
    // determined from a namespace alone, so the reverse lookup declines rather than guessing.
    catalog.upsertOntology(new OntologyInfo("WP-A", "A", null, "OWL"));
    catalog.upsertOntology(new OntologyInfo("WP-B", "B", null, "OWL"));
    catalog.setOntologyIri("WP-A", "http://webprotege.stanford.edu/A", "http://webprotege.stanford.edu/");
    catalog.setOntologyIri("WP-B", "http://webprotege.stanford.edu/B", "http://webprotege.stanford.edu/");
    assertTrue(catalog.acronymForNamespace("http://webprotege.stanford.edu/").isEmpty());
  }

  @Test
  public void initSchemaIsIdempotentForTheAddedColumns() throws Exception {
    // ensureColumn must not fail when the iri / raw_namespace columns already exist (re-run of the
    // backfill, or a fresh catalog whose CREATE already has them).
    catalog.initSchema();
    catalog.initSchema();
    catalog.setOntologyIri("DOID", "http://purl.obolibrary.org/obo/doid", "raw");
    assertEquals("http://purl.obolibrary.org/obo/doid", catalog.ontologyIri("DOID").orElseThrow());
  }

  @Test
  public void ontologyKindDefaultsToOntology_andIsNotAValueSetCollection() throws Exception {
    // Every ordinary ontology row reads kind='ontology' via the column DEFAULT, so a value-set
    // collection lookup never mistakes it for one.
    assertFalse(catalog.isValueSetCollection("DOID"));
    assertFalse(catalog.isValueSetCollection("NOPE")); // unknown acronym
    assertFalse(catalog.isValueSetCollection(null));
  }

  @Test
  public void setOntologyKind_marksAValueSetCollection() throws Exception {
    catalog.upsertOntology(new OntologyInfo("CEDARVS", "CEDAR Value Sets", null, "SKOS"));
    assertFalse(catalog.isValueSetCollection("CEDARVS")); // defaults to ontology until marked
    catalog.setOntologyKind("CEDARVS", CatalogStore.KIND_VALUE_SET_COLLECTION);
    assertTrue(catalog.isValueSetCollection("CEDARVS"));
    // Idempotent, and reversible back to an ontology.
    catalog.setOntologyKind("CEDARVS", CatalogStore.KIND_VALUE_SET_COLLECTION);
    assertTrue(catalog.isValueSetCollection("CEDARVS"));
    catalog.setOntologyKind("CEDARVS", CatalogStore.KIND_ONTOLOGY);
    assertFalse(catalog.isValueSetCollection("CEDARVS"));
  }

  @Test
  public void ensureColumnBackfillsKindOnAPreExistingCatalog(@TempDir Path dir) throws Exception {
    // A catalog created before the kind column existed: build the old ontology table (no kind) via raw
    // JDBC, insert a row, then open a CatalogStore and run initSchema (which ensureColumn-migrates).
    // The migrated row must read the default and be markable as a value-set collection.
    Path dbFile = dir.resolve("legacy.sqlite");
    try (var conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbFile);
         var s = conn.createStatement()) {
      s.executeUpdate("CREATE TABLE ontology (acronym TEXT PRIMARY KEY, name TEXT NOT NULL, "
          + "source_iri TEXT, default_format TEXT)");
      s.executeUpdate("INSERT INTO ontology (acronym, name) VALUES ('OLDVS', 'legacy')");
    }
    try (CatalogStore legacy = CatalogStore.openFile(dbFile.toString())) {
      legacy.initSchema(); // adds iri/raw_namespace/provenance/kind columns idempotently
      assertFalse(legacy.isValueSetCollection("OLDVS")); // reads the DEFAULT after migration
      legacy.setOntologyKind("OLDVS", CatalogStore.KIND_VALUE_SET_COLLECTION);
      assertTrue(legacy.isValueSetCollection("OLDVS"));
    }
  }

  @Test
  public void snapshotBackendDefaultsToBioportalForEveryRow() throws Exception {
    // The backend column carries a constant DEFAULT, so existing snapshots read 'bioportal' with no
    // separate backfill.
    assertEquals("bioportal", catalog.snapshotProvenance("hashV1", "DOID").orElseThrow().backend());
    assertEquals("bioportal", catalog.snapshotProvenance("hashV2", "DOID").orElseThrow().backend());
  }

  @Test
  public void snapshotProvenanceRoundTrips() throws Exception {
    // submission_id and source_date are null until set; then round-trip.
    var before = catalog.snapshotProvenance("hashV1", "DOID").orElseThrow();
    assertEquals(null, before.submissionId());
    assertEquals(null, before.sourceDate());

    catalog.setSnapshotProvenance("hashV1", "DOID", 352, "2025-01-01");
    var after = catalog.snapshotProvenance("hashV1", "DOID").orElseThrow();
    assertEquals(Integer.valueOf(352), after.submissionId());
    assertEquals("2025-01-01", after.sourceDate());
    assertEquals("bioportal", after.backend()); // unchanged
  }

  @Test
  public void snapshotProvenanceIsScopedToTheAcronym() throws Exception {
    // A content hash shared by two ontologies keeps separate provenance rows (as for the snapshot).
    catalog.upsertOntology(new OntologyInfo("SHARED-A", "A", null, "SKOS"));
    catalog.upsertOntology(new OntologyInfo("SHARED-B", "B", null, "SKOS"));
    String h = "shared";
    catalog.addSnapshot(new SnapshotInfo(h, "SHARED-A", "1", "2025-01-01", "2025-01-02T00:00:00Z",
        "SKOS", "subsumption", 1, 1, "/a.sqlite", h, "open"));
    catalog.addSnapshot(new SnapshotInfo(h, "SHARED-B", "1", "2025-01-01", "2025-01-02T00:00:00Z",
        "SKOS", "subsumption", 1, 1, "/b.sqlite", h, "open"));
    catalog.setSnapshotProvenance(h, "SHARED-A", 10, "2025-01-01");

    assertEquals(Integer.valueOf(10), catalog.snapshotProvenance(h, "SHARED-A").orElseThrow().submissionId());
    assertEquals(null, catalog.snapshotProvenance(h, "SHARED-B").orElseThrow().submissionId());
  }

  @Test
  public void unknownSnapshotProvenanceIsEmpty() throws Exception {
    assertTrue(catalog.snapshotProvenance("nope", "DOID").isEmpty());
  }

  @Test
  public void sourceDateExtractedFromDeclaredVersionString() {
    // A bare or embedded ISO date is a self-claimed source date; free-text/non-ISO yields null.
    assertEquals("2026-06-08", CatalogStore.SnapshotProvenance.sourceDateFromDeclaredVersion("2026-06-08"));
    assertEquals("2021-10-26",
        CatalogStore.SnapshotProvenance.sourceDateFromDeclaredVersion("releases/2021-10-26"));
    assertEquals(null, CatalogStore.SnapshotProvenance.sourceDateFromDeclaredVersion("Version 1.0.0"));
    assertEquals(null, CatalogStore.SnapshotProvenance.sourceDateFromDeclaredVersion("10-2024"));
    assertEquals(null, CatalogStore.SnapshotProvenance.sourceDateFromDeclaredVersion("2026-13-40")); // invalid
    assertEquals(null, CatalogStore.SnapshotProvenance.sourceDateFromDeclaredVersion(null));
  }

  @Test
  public void cutover_rewritesVersionIdsAndMergesDuplicates() throws Exception {
    // MERGE has three snapshots: raw1 and raw2 are the same content (content hash "cA"), raw3 is
    // distinct ("cB"). latest is raw3.
    catalog.upsertOntology(new OntologyInfo("MERGE", "Merge", null, "OWL"));
    catalog.addSnapshot(mergeSnap("raw1", "2025-01-01"));
    catalog.addSnapshot(mergeSnap("raw2", "2025-02-01"));
    catalog.addSnapshot(mergeSnap("raw3", "2025-03-01"));
    catalog.setTag("MERGE", CatalogStore.TAG_LATEST, "raw3");

    catalog.cutoverToContentHash(List.of(
        new CatalogStore.VersionRemap("MERGE", "raw2", "cA", true),  // survivor of the dup pair
        new CatalogStore.VersionRemap("MERGE", "raw1", "cA", false), // merged away
        new CatalogStore.VersionRemap("MERGE", "raw3", "cB", true)));

    // Two rows remain, keyed by content hash; the duplicate is gone.
    List<SnapshotInfo> after = catalog.listSnapshots("MERGE");
    assertEquals(2, after.size());
    assertTrue(catalog.resolveVersion("MERGE", "cA").isPresent());
    assertTrue(catalog.resolveVersion("MERGE", "cB").isPresent());
    assertTrue(catalog.resolveVersion("MERGE", "raw1").isEmpty()); // old raw ids no longer resolve
    assertTrue(catalog.resolveVersion("MERGE", "raw2").isEmpty());
    // latest followed raw3 -> its content hash.
    assertEquals("cB", catalog.resolveLatest("MERGE").orElseThrow().versionId());
    // file_hash is untouched (still the raw hash) on the surviving row.
    assertEquals("raw2", catalog.resolveVersion("MERGE", "cA").orElseThrow().fileHash());
  }

  @Test
  public void cutover_repointsATagThatSatOnAMergedAwayDuplicate() throws Exception {
    // latest points at the row that gets merged away; it must repoint to the survivor's content hash.
    catalog.upsertOntology(new OntologyInfo("MERGE", "Merge", null, "OWL"));
    catalog.addSnapshot(mergeSnap("raw1", "2025-01-01"));
    catalog.addSnapshot(mergeSnap("raw2", "2025-02-01"));
    catalog.setTag("MERGE", CatalogStore.TAG_LATEST, "raw1"); // tag on the loser

    catalog.cutoverToContentHash(List.of(
        new CatalogStore.VersionRemap("MERGE", "raw2", "cA", true),
        new CatalogStore.VersionRemap("MERGE", "raw1", "cA", false)));

    assertEquals(1, catalog.listSnapshots("MERGE").size());
    assertEquals("cA", catalog.resolveLatest("MERGE").orElseThrow().versionId());
  }

  private static SnapshotInfo mergeSnap(String rawHash, String released) {
    return new SnapshotInfo(rawHash, "MERGE", "1.0", released, "2026-01-01T00:00:00Z",
        "OWL", "subsumption", 1, 1, "/snapshots/MERGE/" + rawHash + ".sqlite", rawHash, "open");
  }

  @Test
  public void inMemoryCatalogHasNoBaseDir() {
    // The shared in-memory catalog has no file, so no base directory to resolve against.
    assertTrue(catalog.baseDir().isEmpty());
  }

  @Test
  public void fileCatalogResolvesRelativePathsAgainstItsOwnDirectory(@TempDir Path dir) throws Exception {
    // A snapshot path stored relative to the catalog file resolves to an absolute path under the
    // catalog's own directory. This is what makes the store relocatable: copy catalog + snapshots
    // anywhere and reads still find the files, with no stored absolute paths to rewrite.
    Path catalogFile = dir.resolve("catalog.sqlite");
    try (CatalogStore fileCatalog = CatalogStore.openFile(catalogFile.toString())) {
      fileCatalog.initSchema();
      assertEquals(dir, fileCatalog.baseDir().orElseThrow());
      fileCatalog.upsertOntology(new OntologyInfo("DOID", "Human Disease Ontology", null, "OWL"));
      fileCatalog.addSnapshot(new SnapshotInfo("relV", "DOID", "release/x", "2025-01-01",
          "2026-01-01T00:00:00Z", "OWL", "subsumption", 1, 1,
          "snapshots/DOID/relV.sqlite", "relV", "open")); // relative path
      fileCatalog.setTag("DOID", CatalogStore.TAG_LATEST, "relV");

      String resolved = fileCatalog.resolveLatest("DOID").orElseThrow().filePath();
      assertEquals(dir.resolve("snapshots/DOID/relV.sqlite").toString(), resolved);
      assertTrue(Path.of(resolved).isAbsolute());
    }
  }

  @Test
  public void fileCatalogLeavesAbsolutePathsUnchanged(@TempDir Path dir) throws Exception {
    // Backward compatibility: a catalog written before relative paths stores absolute paths, which
    // must be served verbatim rather than re-rooted under the catalog directory.
    Path catalogFile = dir.resolve("catalog.sqlite");
    String absolute = dir.resolve("elsewhere/DOID/absV.sqlite").toString();
    try (CatalogStore fileCatalog = CatalogStore.openFile(catalogFile.toString())) {
      fileCatalog.initSchema();
      fileCatalog.upsertOntology(new OntologyInfo("DOID", "Human Disease Ontology", null, "OWL"));
      fileCatalog.addSnapshot(new SnapshotInfo("absV", "DOID", "release/x", "2025-01-01",
          "2026-01-01T00:00:00Z", "OWL", "subsumption", 1, 1, absolute, "absV", "open"));
      fileCatalog.setTag("DOID", CatalogStore.TAG_LATEST, "absV");

      assertEquals(absolute, fileCatalog.resolveLatest("DOID").orElseThrow().filePath());
      assertFalse(absolute.startsWith(dir.resolve("snapshots").toString()));
    }
  }
}
