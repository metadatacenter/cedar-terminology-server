package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.metadatacenter.terms.store.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class TerminologyPublicationTest {
  @TempDir Path dir;

  private Path snapshot(String version, boolean properties) throws Exception {
    Path path = dir.resolve(version + ".sqlite");
    try (SnapshotStore s = SnapshotStore.openFile(path.toString())) {
      s.initSchema();
      s.addConcept("http://example.org/class", version);
      s.materialize();
      if (properties) s.properties().write(List.of(new SnapshotProperties.Property(
          "http://example.org/property", "object", version, false, List.of(), List.of())));
    }
    return path;
  }

  private void register(CatalogStore catalog, String version, boolean properties) throws Exception {
    Path file = snapshot(version, properties);
    catalog.upsertOntology(new CatalogStore.OntologyInfo("EX", "Example", null, "OWL"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo(version, "EX", version, "2026-01-01",
        "2026-01-01T00:00:00Z", "OWL", "subsumption", 1, 0, file.toString(), version, "public"));
    catalog.setTag("EX", CatalogStore.TAG_LATEST, version);
  }

  private void sql(Path file, String sql) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file); Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  static Stream<Arguments> failures() {
    return Stream.of(false, true).flatMap(bulk -> Stream.of("term", "name", "indexed_snapshot", "property_index", "property_indexed_snapshot")
        .map(table -> Arguments.of(bulk, table)));
  }

  @ParameterizedTest
  @MethodSource("failures")
  void failedReplacementKeepsBothSearchLanesAndCanBeRetried(boolean bulk, String table) throws Exception {
    Path indexPath = dir.resolve("search.sqlite");
    try (CatalogStore catalog = CatalogStore.openFile(dir.resolve("catalog.sqlite").toString());
         SearchIndexStore index = SearchIndexStore.openFile(indexPath.toString())) {
      catalog.initSchema();
      index.initSchema();
      register(catalog, "old", true);
      SearchIndexJob job = new SearchIndexJob(catalog, index);
      assertTrue(job.indexOntology("EX", false, 0).indexed());
      index.rebuildFullText();
      register(catalog, "new", true);
      if (bulk) index.applyBulkLoadPragmas(); // Use the CLI's real production settings, too.
      sql(indexPath, "CREATE TRIGGER refuse BEFORE INSERT ON " + table
          + " BEGIN SELECT RAISE(ABORT, 'injected publication failure'); END");
      SQLException refused = assertThrows(SQLException.class, () -> job.indexOntology("EX", false, 0));
      assertTrue(refused.getMessage().contains("injected publication failure"), refused.toString());
      try (SearchIndexStore reader = SearchIndexStore.openForRead(indexPath.toString())) {
        assertEquals("old", reader.indexedVersion("EX").orElseThrow());
        assertEquals("old", reader.term("EX", "http://example.org/class").orElseThrow().prefLabel());
        assertEquals("old", reader.properties().version("EX").orElseThrow());
        assertEquals(1, reader.search("old", List.of("EX"), 10).size());
        assertEquals(1, reader.properties().search("old", List.of("EX"), List.of(), 0, 10).total());
      }
      sql(indexPath, "DROP TRIGGER refuse");
      assertTrue(job.indexOntology("EX", false, 0).indexed());
      assertEquals("new", index.indexedVersion("EX").orElseThrow());
      assertEquals("new", index.properties().version("EX").orElseThrow());
      assertEquals(0, index.properties().search("old", List.of("EX"), List.of(), 0, 10).total());
      assertEquals("already current", job.indexOntology("EX", false, 0).skipped());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"sql", "runtime", "error"})
  void failedCatalogRegistrationLeavesNothingPublished(String failure) throws Exception {
    Path path = dir.resolve("catalog.sqlite");
    try (CatalogStore catalog = CatalogStore.openFile(path.toString())) {
      catalog.initSchema();
      Throwable expected = switch (failure) {
        case "sql" -> new SQLException("registration failed");
        case "runtime" -> new IllegalStateException("registration failed");
        default -> new AssertionError("registration failed");
      };
      assertSame(expected, assertThrows(Throwable.class, () -> catalog.inTransaction(() -> {
        catalog.upsertOntology(new CatalogStore.OntologyInfo("EX", "Example", null, "OWL"));
        if (expected instanceof SQLException sql) throw sql;
        if (expected instanceof RuntimeException runtime) throw runtime;
        throw (Error) expected;
      })));
    }
    try (CatalogStore reopened = CatalogStore.openFile(path.toString())) {
      assertTrue(reopened.listOntologies().isEmpty(), "failed transaction must not commit in finally");
    }
  }

  @Test
  void switchingToALegacySnapshotDoesNotKeepAnotherVersionsProperties() throws Exception {
    try (CatalogStore catalog = CatalogStore.openInMemory(); SearchIndexStore index = SearchIndexStore.openInMemory()) {
      catalog.initSchema(); index.initSchema();
      register(catalog, "old", true);
      SearchIndexJob job = new SearchIndexJob(catalog, index);
      job.indexOntology("EX", false, 0);
      register(catalog, "legacy", false);
      job.indexOntology("EX", false, 0);
      assertEquals("legacy", index.indexedVersion("EX").orElseThrow());
      assertTrue(index.properties().version("EX").isEmpty(), "unknown extraction is not the previous release");
      assertEquals(0, index.properties().search("old", List.of("EX"), List.of(), 0, 10).total());
    }
  }
  @Test
  void completedOntologyUpdatePublishesSearchableNamesWithItsVersion() throws Exception {
    try (CatalogStore catalog = CatalogStore.openInMemory(); SearchIndexStore index = SearchIndexStore.openInMemory()) {
      catalog.initSchema(); index.initSchema();
      register(catalog, "old", true);
      SearchIndexJob job = new SearchIndexJob(catalog, index);
      job.indexOntology("EX", false, 0);
      index.rebuildFullText();
      register(catalog, "new", true);
      job.indexOntology("EX", false, 0);
      assertEquals("new", index.indexedVersion("EX").orElseThrow());
      assertEquals(0, index.search("old", List.of("EX"), 10).size());
      assertEquals(1, index.search("new", List.of("EX"), 10).size());
    }
  }

  @Test
  void anExistingCompletedIndexAcquiresIncrementalSearchMaintenanceOnUpgrade() throws Exception {
    Path indexPath = dir.resolve("upgrade.sqlite");
    try (CatalogStore catalog = CatalogStore.openInMemory()) {
      catalog.initSchema();
      register(catalog, "old", true);
      try (SearchIndexStore index = SearchIndexStore.openFile(indexPath.toString())) {
        index.initSchema();
        for (String event : List.of("insert", "delete", "update"))
          sql(indexPath, "DROP TRIGGER name_fts_" + event);
        new SearchIndexJob(catalog, index).indexOntology("EX", false, 0);
        index.rebuildFullText(); // An index completed by the old batch writer.
      }
      register(catalog, "new", true);
      try (SearchIndexStore upgraded = SearchIndexStore.openFile(indexPath.toString())) {
        upgraded.initSchema();
        upgraded.initSchema(); // Initialization is safe to repeat.
        new SearchIndexJob(catalog, upgraded).indexOntology("EX", false, 0);
        assertEquals(0, upgraded.search("old", List.of("EX"), 10).size());
        assertEquals(1, upgraded.search("new", List.of("EX"), 10).size());
      }
    }
  }

}
