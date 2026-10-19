package org.metadatacenter.terms.store;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IndexRollbackMatrixTest {
  @TempDir Path dir;

  @ParameterizedTest
  @ValueSource(strings = {"runtime", "error"})
  void interruptedIterationRollsBackRowsAndVersion(String kind) throws Exception {
    Path path = dir.resolve("index.sqlite");
    var old = new SearchIndexStore.IndexedTerm("EX", "http://example.org/old", "Old", false, null, false, 0);
    var fresh = new SearchIndexStore.IndexedTerm("EX", "http://example.org/new", "New", false, null, false, 0);
    try (SearchIndexStore index = SearchIndexStore.openFile(path.toString())) {
      index.initSchema();
      index.replaceOntology("EX", "old", "time", List.of(old), Map.of());
      // Fail after deletion and a first insert, as a bad decoded record or interrupted producer can.
      Throwable expected = kind.equals("runtime") ? new IllegalStateException("decode failed")
          : new AssertionError("producer failed");
      Collection<SearchIndexStore.IndexedTerm> broken = new AbstractCollection<>() {
        public int size() { return 2; }
        public Iterator<SearchIndexStore.IndexedTerm> iterator() {
          return new Iterator<>() {
            private boolean first = true;
            public boolean hasNext() { return true; }
            public SearchIndexStore.IndexedTerm next() {
              if (first) { first = false; return fresh; }
              if (expected instanceof RuntimeException runtime) throw runtime;
              throw (Error) expected;
            }
          };
        }
      };
      assertSame(expected, assertThrows(Throwable.class, () -> index.replaceOntology("EX", "new", "time", broken, Map.of())));
    }
    try (SearchIndexStore reader = SearchIndexStore.openForRead(path.toString())) {
      assertEquals("old", reader.indexedVersion("EX").orElseThrow());
      assertEquals(Optional.of(old), reader.term("EX", old.iri()));
      assertTrue(reader.term("EX", fresh.iri()).isEmpty());
    }
  }
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rollbackSurvivesPageCacheSpill(boolean bulk) throws Exception {
    Path path = dir.resolve("spill.sqlite");
    try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + path);
         SearchIndexStore index = new SearchIndexStore(connection)) {
      index.initSchema();
      List<SearchIndexStore.IndexedTerm> old = new ArrayList<>();
      for (int i = 0; i < 100; i++) old.add(new SearchIndexStore.IndexedTerm(
          "EX", "http://example.org/" + i, "Old" + "x".repeat(3000), false, null, false, 0));
      index.replaceOntology("EX", "old", "time", old, Map.of());
      if (bulk) index.applyBulkLoadPragmas();
      try (var statement = connection.createStatement()) {
        // Make a small ontology exceed the cache, as large production ontologies exceed 200 MB.
        statement.execute("PRAGMA cache_size=4");
        statement.execute("CREATE TRIGGER refuse BEFORE INSERT ON term WHEN NEW.iri='fail' BEGIN SELECT RAISE(ABORT, 'stop'); END");
      }
      List<SearchIndexStore.IndexedTerm> replacement = new ArrayList<>();
      for (int i = 0; i < 200; i++) replacement.add(new SearchIndexStore.IndexedTerm(
          "EX", "http://example.org/new" + i, "New" + "y".repeat(3000), false, null, false, 0));
      replacement.add(new SearchIndexStore.IndexedTerm("EX", "fail", "Fail", false, null, false, 0));
      assertThrows(java.sql.SQLException.class,
          () -> index.replaceOntology("EX", "new", "time", replacement, Map.of()));
      assertEquals(100, index.termCount(), "deleted pages must be restored even after spilling to disk");
    }
    try (SearchIndexStore reader = SearchIndexStore.openForRead(path.toString())) {
      assertEquals(100, reader.termCount());
      assertEquals("old", reader.indexedVersion("EX").orElseThrow());
    }
  }

  @ParameterizedTest
  @CsvSource({"false, rows", "true, rows", "false, postings", "true, postings"})
  void processDeathDuringReplacementPreservesCommittedIndex(boolean bulk, String stage) throws Exception {
    Path path = dir.resolve("crash.sqlite");
    Path output = dir.resolve("child.log");
    Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
        CrashWriter.class.getName(), path.toString(), Boolean.toString(bulk), stage)
        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue(child.waitFor(15, java.util.concurrent.TimeUnit.SECONDS), "crash writer finished");
      assertEquals(77, child.exitValue(), java.nio.file.Files.readString(output));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
    assertDoesNotThrow(() -> {
      try (SearchIndexStore reader = SearchIndexStore.openFile(path.toString())) {
        assertEquals(100, reader.termCount());
        assertEquals("old", reader.indexedVersion("EX").orElseThrow());
        assertTrue(reader.term("EX", "http://example.org/old0").isPresent());
        assertTrue(reader.term("EX", "http://example.org/new0").isEmpty());
        assertEquals(100, reader.search("Old", List.of("EX"), 200).size());
        assertEquals(0, reader.search("New", List.of("EX"), 200).size());
      }
    }, "a terminated writer must not damage the last committed index");
  }

  public static class CrashWriter {
    public static void main(String[] args) throws Exception {
      try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + args[0]);
           SearchIndexStore index = new SearchIndexStore(connection)) {
        index.initSchema();
        List<SearchIndexStore.IndexedTerm> old = new ArrayList<>();
        for (int i = 0; i < 100; i++) old.add(new SearchIndexStore.IndexedTerm(
            "EX", "http://example.org/old" + i, "Old" + "x".repeat(3000), false, null, false, 0));
        index.replaceOntology("EX", "old", "time", old, Map.of());
        if (Boolean.parseBoolean(args[1])) index.applyBulkLoadPragmas();
        try (var statement = connection.createStatement()) {
          statement.execute("PRAGMA cache_size=4");
          statement.execute("PRAGMA cache_spill=ON");
        }
        if (args[2].equals("postings")) {
          org.sqlite.Function.create(connection, "stop_process", new org.sqlite.Function() {
            @Override protected void xFunc() { Runtime.getRuntime().halt(77); }
          });
          try (var statement = connection.createStatement()) {
            // Version publication happens after both FTS batches, but before the transaction commits.
            statement.execute("CREATE TRIGGER stop BEFORE INSERT ON indexed_snapshot "
                + "BEGIN SELECT stop_process(); END");
          }
          List<SearchIndexStore.IndexedTerm> replacement = new ArrayList<>();
          for (int i = 0; i < 200; i++) replacement.add(new SearchIndexStore.IndexedTerm(
              "EX", "http://example.org/new" + i, "New" + "y".repeat(3000), false, null, false, 0));
          index.replaceOntology("EX", "new", "time", replacement, Map.of());
          throw new AssertionError("crash trigger did not execute");
        }
        Collection<SearchIndexStore.IndexedTerm> interrupted = new AbstractCollection<>() {
          public int size() { return 201; }
          public Iterator<SearchIndexStore.IndexedTerm> iterator() {
            return new Iterator<>() {
              private int n;
              public boolean hasNext() { return true; }
              public SearchIndexStore.IndexedTerm next() {
                if (n == 200) Runtime.getRuntime().halt(77); // Real process death, no finally/rollback.
                return new SearchIndexStore.IndexedTerm("EX", "http://example.org/new" + n++,
                    "New" + "y".repeat(3000), false, null, false, 0);
              }
            };
          }
        };
        index.replaceOntology("EX", "new", "time", interrupted, Map.of());
      }
    }
  }

}
