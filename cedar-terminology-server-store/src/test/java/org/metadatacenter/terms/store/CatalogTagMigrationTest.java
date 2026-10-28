package org.metadatacenter.terms.store;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogTagMigrationTest {
  @TempDir Path directory;

  private static CatalogStore.SnapshotInfo snapshot(String acronym, String version) {
    return new CatalogStore.SnapshotInfo(version, acronym, "release", "2026-01-01", "2026-01-02",
        "OWL", "subsumption", 1, 0, acronym + "/" + version + ".sqlite", "raw", "public");
  }

  private Path seed(boolean legacy) throws Exception {
    Path path = directory.resolve("catalog.sqlite");
    try (CatalogStore catalog = CatalogStore.openFile(path.toString())) {
      catalog.initSchema();
      for (String acronym : List.of("A", "B")) {
        catalog.upsertOntology(new CatalogStore.OntologyInfo(acronym, acronym, null, "OWL"));
        catalog.addSnapshot(snapshot(acronym, "shared"));
        catalog.setTag(acronym, "latest", "shared");
      }
      catalog.addSnapshot(snapshot("A", "only-a"));
      catalog.setTag("A", "release", "only-a");
    }
    if (legacy) {
      sql(path, "CREATE TEMP TABLE saved_tags AS SELECT rowid AS saved_id,* FROM version_tag",
          "DROP TABLE version_tag",
          "CREATE TABLE version_tag (acronym TEXT NOT NULL REFERENCES ontology_source(acronym), "
              + "tag TEXT NOT NULL, version_id TEXT NOT NULL REFERENCES snapshot(version_id), "
              + "PRIMARY KEY(acronym,tag))",
          "INSERT INTO version_tag(rowid,acronym,tag,version_id) SELECT saved_id,acronym,tag,version_id FROM saved_tags");
    }
    return path;
  }

  private static void sql(Path path, String... commands) throws Exception {
    try (var db = DriverManager.getConnection("jdbc:sqlite:" + path); var statement = db.createStatement()) {
      for (String command : commands) statement.execute(command);
    }
  }

  private static List<List<Object>> rows(Path path, String query) throws Exception {
    List<List<Object>> result = new ArrayList<>();
    try (var db = DriverManager.getConnection("jdbc:sqlite:" + path);
         var statement = db.createStatement(); var rows = statement.executeQuery(query)) {
      while (rows.next()) {
        List<Object> row = new ArrayList<>();
        for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.add(rows.getObject(i));
        result.add(row);
      }
    }
    return result;
  }

  @Test
  void migrationPreservesRowsIndexesTriggersAndSharedHashes() throws Exception {
    Path path = seed(true);
    sql(path, "CREATE INDEX tag_versions ON version_tag(version_id)",
        "CREATE TRIGGER protect_release BEFORE DELETE ON version_tag WHEN OLD.tag='release' "
            + "BEGIN SELECT RAISE(ABORT,'release protected'); END");
    var beforeTags = rows(path, "SELECT rowid,* FROM version_tag ORDER BY rowid");
    var beforeSnapshots = rows(path, "SELECT * FROM snapshot ORDER BY acronym,version_id");
    var beforeObjects = rows(path, "SELECT name,sql FROM sqlite_master WHERE name IN ('tag_versions','protect_release') ORDER BY name");
    try (CatalogStore catalog = CatalogStore.openFile(path.toString())) {
      catalog.initSchema();
      assertEquals("shared", catalog.resolveLatest("A").orElseThrow().versionId());
      assertEquals("shared", catalog.resolveLatest("B").orElseThrow().versionId());
      assertEquals("only-a", catalog.resolve("A", "release").orElseThrow().versionId());
      var schemaVersion = rows(path, "PRAGMA schema_version");
      catalog.initSchema();
      assertEquals(schemaVersion, rows(path, "PRAGMA schema_version"), "repeat initialization must not rebuild");
    }
    assertEquals(beforeTags, rows(path, "SELECT rowid,* FROM version_tag ORDER BY rowid"));
    assertEquals(beforeSnapshots, rows(path, "SELECT * FROM snapshot ORDER BY acronym,version_id"));
    assertEquals(beforeObjects, rows(path, "SELECT name,sql FROM sqlite_master WHERE name IN ('tag_versions','protect_release') ORDER BY name"));
    assertTrue(rows(path, "PRAGMA foreign_key_check").isEmpty());
    assertThrows(SQLException.class, () -> sql(path, "DELETE FROM version_tag WHERE tag='release'"));
    assertThrows(SQLException.class, () -> sql(path, "PRAGMA foreign_keys=ON",
        "INSERT INTO version_tag VALUES ('B','cross-source','only-a')"));
  }

  @Test
  void migrationRestoresEnabledForeignKeys() throws Exception {
    Path path = seed(true);
    // JDBC connection options let an initializer arrive with enforcement already enabled.
    try (CatalogStore catalog = CatalogStore.openFile(path + "?foreign_keys=on")) {
      catalog.initSchema();
      assertEquals("shared", catalog.resolveLatest("A").orElseThrow().versionId());
      assertThrows(SQLException.class, () -> catalog.addSnapshot(snapshot("UNKNOWN", "bad")),
          "migration must restore the connection's original enforcement setting");
    }
    assertTrue(rows(path, "PRAGMA foreign_key_check").isEmpty());
  }

  @Test
  void invalidLegacyTagAbortsWithoutChangingTheTableOrAnyRows() throws Exception {
    Path path = seed(true);
    sql(path, "UPDATE version_tag SET version_id='only-a' WHERE acronym='B'");
    var schema = rows(path, "SELECT type,name,sql FROM sqlite_master ORDER BY type,name");
    var tags = rows(path, "SELECT rowid,* FROM version_tag ORDER BY rowid");
    var snapshots = rows(path, "SELECT * FROM snapshot ORDER BY acronym,version_id");
    try (CatalogStore catalog = CatalogStore.openFile(path.toString())) {
      SQLException failure = assertThrows(SQLException.class, catalog::initSchema);
      assertTrue(failure.getMessage().contains("outside its vocabulary"), failure.toString());
    }
    assertEquals(schema, rows(path, "SELECT type,name,sql FROM sqlite_master ORDER BY type,name"));
    assertEquals(tags, rows(path, "SELECT rowid,* FROM version_tag ORDER BY rowid"));
    assertEquals(snapshots, rows(path, "SELECT * FROM snapshot ORDER BY acronym,version_id"));
  }

  @Test
  void normalTagWritesRejectMissingAndCrossVocabularyVersionsWithoutMovingLatest() throws Exception {
    Path path = seed(false);
    try (CatalogStore catalog = CatalogStore.openFile(path.toString())) {
      assertThrows(SQLException.class, () -> catalog.setTag("B", "latest", "only-a"));
      assertThrows(SQLException.class, () -> catalog.setTag("B", "latest", "missing"));
      assertThrows(SQLException.class, () -> catalog.setTag("UNKNOWN", "latest", "shared"));
      assertEquals("shared", catalog.resolveLatest("B").orElseThrow().versionId());
      catalog.inTransaction(() -> {
        catalog.addSnapshot(snapshot("B", "new"));
        catalog.setTag("B", "latest", "new");
      });
      assertEquals("new", catalog.resolveLatest("B").orElseThrow().versionId());
    }
    assertTrue(rows(path, "PRAGMA foreign_key_check").isEmpty());
  }
}
