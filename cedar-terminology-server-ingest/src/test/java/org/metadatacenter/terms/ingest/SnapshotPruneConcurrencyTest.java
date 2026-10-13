package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.store.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotPruneConcurrencyTest {
  @TempDir Path dir;

  @Test
  void orphanCleanupCannotRemoveASnapshotBeingRegistered() throws Exception {
    Path catalogPath = dir.resolve("catalog.sqlite");
    Path snapshots = dir.resolve("snapshots");
    Files.createDirectories(snapshots);
    Path old = snapshots.resolve("old.sqlite");
    try (SnapshotStore store = SnapshotStore.openFile(old.toString())) {
      store.initSchema(); store.addConcept("http://example.org/old", "Old"); store.materialize();
    }
    try (CatalogStore seed = CatalogStore.openFile(catalogPath.toString())) {
      seed.initSchema();
      seed.upsertOntology(new CatalogStore.OntologyInfo("OLD", "Old", null, "OWL"));
      seed.addSnapshot(new CatalogStore.SnapshotInfo("old", "OLD", "1", "2026-01-01", "2026-01-01",
          "OWL", "subsumption", 1, 0, old.toString(), "old", "public"));
      seed.setTag("OLD", "latest", "old");
    }
    Path owl = dir.resolve("source.owl");
    Files.writeString(owl, """
        <?xml version="1.0"?>
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
          xmlns:owl="http://www.w3.org/2002/07/owl#">
          <owl:Ontology rdf:about="http://example.org/ontology"/>
          <owl:Class rdf:about="http://example.org/new"/>
        </rdf:RDF>
        """);
    CountDownLatch registering = new CountDownLatch(1);
    CountDownLatch finish = new CountDownLatch(1);
    SubmissionSource source = new SubmissionSource() {
      public OntologyAccess accessInfo(String acronym) { return new OntologyAccess("public", null); }
      public List<Submission> listSubmissions(String acronym) { return List.of(latestSubmission(acronym)); }
      public Submission latestSubmission(String acronym) { return new Submission(1, "1", "2026-01-01", "OWL"); }
      public Path download(String acronym, int id, Path target) throws IOException {
        Files.createDirectories(target);
        return Files.copy(owl, target.resolve("source.owl"));
      }
      public String backendId() {
        // Called by the real registration transaction after the file has been published.
        registering.countDown();
        try { assertTrue(finish.await(10, TimeUnit.SECONDS), "release registration"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        return "test";
      }
    };
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<IngestJob.IngestResult> ingest = executor.submit(() -> {
        try (CatalogStore catalog = CatalogStore.openFile(catalogPath.toString())) {
          return new IngestJob(source).ingestLatest(catalog, "NEW", snapshots);
        }
      });
      assertTrue(registering.await(10, TimeUnit.SECONDS), "ingest reached registration boundary");
      try (CatalogStore cleanup = CatalogStore.openFile(catalogPath.toString())) {
        cleanup.setBusyTimeoutMillis(1);
        try {
          new IngestJob(null).pruneOrphans(cleanup, snapshots, true);
        } catch (SQLException busy) {
          assertTrue(busy.getMessage().contains("SQLITE_BUSY"), busy.toString());
        }
      } finally { finish.countDown(); }
      IngestJob.IngestResult result = ingest.get(10, TimeUnit.SECONDS);
      try (CatalogStore catalog = CatalogStore.openFile(catalogPath.toString())) {
        assertTrue(Files.exists(result.snapshotFile()), "registered snapshot must still exist");
        assertTrue(new IngestJob(null).verifyStore(catalog, snapshots, false).clean());
        assertEquals(0, new IngestJob(null).pruneOrphans(catalog, snapshots, true).orphans());
      }
    } finally {
      finish.countDown(); executor.shutdownNow();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }
}
