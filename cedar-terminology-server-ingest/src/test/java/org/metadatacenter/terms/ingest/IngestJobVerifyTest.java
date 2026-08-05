package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestJobVerifyTest {

  @TempDir
  Path dir;

  private static CatalogStore.SnapshotInfo snap(String vid, String path) {
    return new CatalogStore.SnapshotInfo(vid, "DOID", "1", "2025-01-01", "2025-01-01T00:00:00Z",
        "OWL", "subsumption", 1, 0, path, "rawhash-" + vid, "open");
  }

  private static Path emptySnapshotFile(Path p) throws Exception {
    Files.createDirectories(p.getParent());
    try (SnapshotStore s = SnapshotStore.openFile(p.toString())) {
      s.initSchema();
    }
    return p;
  }

  private static void addConcept(Path snapshotFile) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + snapshotFile);
         Statement st = c.createStatement()) {
      st.executeUpdate("INSERT INTO concept (iri, pref_label) VALUES ('http://ex.org/1', 'One')");
    }
  }

  @Test
  void verify_passesAValidStoreAndFlagsMissingEmptyAndOrphan() throws Exception {
    Path catFile = dir.resolve("catalog.sqlite");
    Path snapshots = dir.resolve("snapshots");
    Path good = snapshots.resolve("DOID/v1.sqlite");
    addConcept(emptySnapshotFile(good)); // schema + one concept -> a valid snapshot

    IngestJob job = new IngestJob(null); // verify needs no source
    try (CatalogStore cat = CatalogStore.openFile(catFile.toString())) {
      cat.initSchema();
      cat.upsertOntology(new CatalogStore.OntologyInfo("DOID", "Human Disease Ontology",
          "http://purl.obolibrary.org/obo/doid.owl", "OWL"));
      cat.addSnapshot(snap("v1", "snapshots/DOID/v1.sqlite"));
      cat.setTag("DOID", CatalogStore.TAG_LATEST, "v1");

      // A present, non-empty, referenced snapshot with a resolvable latest is clean.
      IngestJob.VerifySummary clean = job.verifyStore(cat, snapshots, false);
      assertTrue(clean.clean(), "valid store should be clean");
      assertEquals(1, clean.ok());

      // An extra .sqlite on disk that no catalog row references is an orphan.
      Files.copy(good, snapshots.resolve("DOID/orphan.sqlite"));
      assertEquals(1, job.verifyStore(cat, snapshots, false).orphanFiles());

      // A registered snapshot whose file has the schema but no concepts is empty.
      emptySnapshotFile(snapshots.resolve("DOID/v2.sqlite"));
      cat.addSnapshot(snap("v2", "snapshots/DOID/v2.sqlite"));
      assertEquals(1, job.verifyStore(cat, snapshots, false).emptyConcepts());

      // A registered snapshot whose file is gone is missing (and makes latest unresolvable).
      Files.delete(good);
      IngestJob.VerifySummary broken = job.verifyStore(cat, snapshots, false);
      assertEquals(1, broken.missingFile());
      assertEquals(1, broken.unresolvableLatest());
      assertFalse(broken.clean());
    }
  }
}
