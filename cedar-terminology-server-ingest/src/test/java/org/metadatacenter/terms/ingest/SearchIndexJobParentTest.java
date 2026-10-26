package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SearchIndexStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one parent the index keeps for a term.
 *
 * It used to be whichever edge a scan of the snapshot reached first, which follows internal row ids:
 * a parent created earlier in the ingest won. DOID's "cardiovascular system disease" was therefore
 * indexed under "disease", through a genus edge, rather than under "disease of anatomical entity",
 * and a search row named a chain the tree it opened did not draw.
 */
class SearchIndexJobParentTest {

  private static final String EX = "http://ex/";

  @TempDir Path dir;

  @Test
  void theIndexKeepsTheParentTheReleasesPathRunsThrough() throws Exception {
    Path file = dir.resolve("v1.sqlite");
    try (SnapshotStore s = SnapshotStore.openFile(file.toString())) {
      s.initSchema();
      // Each parent a scan by row id would have preferred is created first.
      s.addConcept(EX + "disease", "Disease");
      s.addConcept(EX + "pet", "Pet");
      s.addConcept(EX + "organ", "Organ disease");
      s.addConcept(EX + "mammal", "Mammal");
      s.addConcept(EX + "adrenal", "Adrenal disease");
      s.addConcept(EX + "dog", "Dog");
      s.addEdge(EX + "organ", EX + "disease", "rdfs:subClassOf");
      s.addEdge(EX + "adrenal", EX + "organ", "rdfs:subClassOf");
      s.addEdge(EX + "adrenal", EX + "disease", SnapshotStore.GENUS_PREDICATE);
      s.addEdge(EX + "dog", EX + "pet", "rdfs:subClassOf");
      s.addEdge(EX + "dog", EX + "mammal", "rdfs:subClassOf");
      s.materialize();
    }

    try (CatalogStore catalog = CatalogStore.openFile(dir.resolve("catalog.sqlite").toString());
         SearchIndexStore index = SearchIndexStore.openFile(dir.resolve("index.sqlite").toString())) {
      catalog.initSchema();
      index.initSchema();
      catalog.upsertOntology(new CatalogStore.OntologyInfo("EX", "Example", null, "OWL"));
      catalog.addSnapshot(new CatalogStore.SnapshotInfo("v1", "EX", "1", "2026-01-01",
          "2026-01-01T00:00:00Z", "OWL", "subsumption", 6, 5, file.toString(), "v1", "public"));
      catalog.setTag("EX", CatalogStore.TAG_LATEST, "v1");

      assertTrue(new SearchIndexJob(catalog, index).indexOntology("EX", false, 0).indexed());

      SearchIndexStore.IndexedTerm adrenal = index.term("EX", EX + "adrenal").orElseThrow();
      assertEquals(EX + "organ", adrenal.parentIri(), "a genus edge another parent implies is not the path");
      assertEquals("Organ disease", adrenal.parentLabel());
      assertEquals(EX + "mammal", index.term("EX", EX + "dog").orElseThrow().parentIri(),
          "of two asserted parents, the first by IRI, as the release's own path takes");
    }
  }
}
