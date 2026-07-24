package org.metadatacenter.terms;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Verifies catalog-backed local resolution: an ontology is served locally only when allowlisted
 * AND present in the catalog, and the resolved store answers the adapter's reads.
 */
public class CatalogSnapshotProviderTest {

  private static final String BASE = "http://ex/";

  private Path tempDir;
  private CatalogStore catalog;
  private CatalogSnapshotProvider provider;

  @Before
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

    // Allowlist EX and OTHER; only EX is actually ingested.
    provider = new CatalogSnapshotProvider(catalog, Set.of("EX", "OTHER"));
  }

  @After
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
  public void servedThroughTheAdapter() throws Exception {
    SqliteTerminologyService service = new SqliteTerminologyService(provider);
    assertTrue(service.isAvailable("EX"));
    assertFalse(service.isAvailable("OTHER"));
    var children = service.getClassChildren(BASE + "mammal", "EX", 1, 50, null);
    assertEquals(java.util.List.of("cat", "dog"),
        children.getCollection().stream().map(c -> c.getId()).collect(Collectors.toList()));
  }
}
