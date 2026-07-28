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
