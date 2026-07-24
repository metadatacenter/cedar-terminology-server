package org.metadatacenter.terms.store;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.metadatacenter.terms.store.CatalogStore.OntologyInfo;
import org.metadatacenter.terms.store.CatalogStore.SnapshotInfo;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CatalogStoreTest {

  private CatalogStore catalog;

  private static SnapshotInfo doidSnapshot(String versionId, String released) {
    return new SnapshotInfo(versionId, "DOID", "release/" + released, released, "2026-07-24T00:00:00Z",
        "OWL", "subsumption", 14000, 20000, "/snapshots/DOID/" + versionId + ".sqlite", versionId, "open");
  }

  @Before
  public void setUp() throws Exception {
    catalog = CatalogStore.openInMemory();
    catalog.initSchema();
    catalog.upsertOntology(new OntologyInfo("DOID", "Human Disease Ontology",
        "http://purl.obolibrary.org/obo/doid.owl", "OWL"));
    catalog.addSnapshot(doidSnapshot("hashV1", "2025-01-01"));
    catalog.addSnapshot(doidSnapshot("hashV2", "2025-06-01"));
    catalog.setTag("DOID", CatalogStore.TAG_LATEST, "hashV2");
  }

  @After
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
}
