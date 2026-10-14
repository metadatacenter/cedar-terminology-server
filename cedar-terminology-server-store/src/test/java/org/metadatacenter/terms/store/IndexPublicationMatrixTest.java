package org.metadatacenter.terms.store;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class IndexPublicationMatrixTest {
  @TempDir Path dir;

  static Stream<Arguments> publications() {
    return Stream.of(false, true).flatMap(bulk -> Stream.of(false, true).flatMap(combined ->
        Stream.of("current", "triggers", "legacy").map(schema -> Arguments.of(bulk, combined, schema))));
  }

  @ParameterizedTest
  @MethodSource("publications")
  void replacementAndUpgradeKeepPostingsInStepWithContent(boolean bulk, boolean combined, String schema)
      throws Exception {
    Path path = dir.resolve("index.sqlite");
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
         SearchIndexStore index = new SearchIndexStore(c)) {
      index.initSchema();
      publish(index, combined, "MESH", "old", "oldword");
      publish(index, combined, "RH-MESH", "other", "oldword"); // Same IRI and text, distinct source.
      try (var s = c.createStatement()) {
        if (schema.equals("triggers")) {
          // Exact previous writer contract, installed on a populated index before upgrading.
          s.execute("CREATE TRIGGER name_fts_insert AFTER INSERT ON name BEGIN "
              + "INSERT INTO name_fts(rowid,value,ont) VALUES(new.name_id,new.value,new.ont); END");
          s.execute("CREATE TRIGGER name_fts_delete AFTER DELETE ON name BEGIN "
              + "INSERT INTO name_fts(name_fts,rowid,value,ont) VALUES('delete',old.name_id,old.value,old.ont); END");
          s.execute("CREATE TRIGGER name_fts_update AFTER UPDATE ON name BEGIN "
              + "INSERT INTO name_fts(name_fts,rowid,value,ont) VALUES('delete',old.name_id,old.value,old.ont); "
              + "INSERT INTO name_fts(rowid,value,ont) VALUES(new.name_id,new.value,new.ont); END");
        } else if (schema.equals("legacy")) {
          s.execute("DROP TABLE name_fts");
          s.execute("CREATE VIRTUAL TABLE name_fts USING fts5(value,content='name',content_rowid='name_id',"
              + "tokenize='unicode61 remove_diacritics 2')");
          s.execute("INSERT INTO name_fts(name_fts) VALUES('rebuild')");
        }
      }
    }
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
         SearchIndexStore index = new SearchIndexStore(c)) {
      index.initSchema();
      index.initSchema();
      if (bulk) index.applyBulkLoadPragmas();
      for (int attempt = 0; attempt < 2; attempt++) {
        publish(index, combined, "MESH", "new", "newword");
        assertEquals("new", index.indexedVersion("MESH").orElseThrow());
        assertEquals(0, index.search("oldword", List.of("MESH"), 10).size());
        assertEquals(1, index.search("oldword", List.of("RH-MESH"), 10).size());
        assertEquals(1, index.search("newword", List.of("MESH"), 10).size());
        assertEquals(1, index.search("synnewword", List.of("MESH"), 10).size());
        assertEquals(2, index.search("aquifere", List.of(), 10).size());
        integrity(c); // Before any global rebuild: duplication and stale postings must be detected here.
      }
      if (combined) index.replaceOntology("MESH", "empty", "time", List.of(), Map.of(), List.of());
      else index.replaceOntology("MESH", "empty", "time", List.of(), Map.of());
      assertEquals(0, index.search("newword", List.of(), 10).size());
      assertEquals(1, index.search("oldword", List.of(), 10).size());
      integrity(c);
      publish(index, combined, "MESH", "again", "restored");
      integrity(c);
      index.rebuildFullText(); // Explicit repair/legacy-schema upgrade stays usable.
      assertEquals(1, index.search("restored", List.of("MESH"), 10).size());
      assertEquals(0, index.search("restored", List.of("RH-MESH"), 10).size());
      integrity(c);
    }
    try (SearchIndexStore reader = SearchIndexStore.openForRead(path.toString())) {
      assertEquals(1, reader.search("restored", List.of("MESH"), 10).size());
      assertEquals(1, reader.search("oldword", List.of("RH-MESH"), 10).size());
    }
  }

  private void publish(SearchIndexStore index, boolean combined, String acronym, String version, String label)
      throws Exception {
    var terms = List.of(new SearchIndexStore.IndexedTerm(acronym, "http://ex/shared", label, false, null, false, 0),
        new SearchIndexStore.IndexedTerm(acronym, "http://ex/null", null, false, null, false, 0),
        new SearchIndexStore.IndexedTerm(acronym, "http://ex/blank", " ", false, null, false, 0));
    var names = Map.of("http://ex/shared", List.of(
        new SearchIndexStore.IndexedName("synonym", "en", "syn" + label),
        new SearchIndexStore.IndexedName("synonym", "en", "syn" + label),
        new SearchIndexStore.IndexedName("prefLabel", "fr", "aquifère")));
    if (combined) index.replaceOntology(acronym, version, "time", terms, names, List.of());
    else index.replaceOntology(acronym, version, "time", terms, names);
  }

  private void integrity(Connection c) throws Exception {
    try (var s = c.createStatement()) {
      s.execute("INSERT INTO name_fts(name_fts,rank) VALUES('integrity-check',1)");
    }
  }
}
