package org.metadatacenter.terms.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SearchIndexStoreTest {

  private SearchIndexStore index;

  @BeforeEach
  public void setUp() throws Exception {
    index = SearchIndexStore.openInMemory();
    index.initSchema();
    index.replaceOntology("EX", "hash-v1", "2026-08-13T00:00:00Z",
        List.of(
            new SearchIndexStore.IndexedTerm("EX", "http://ex/melanoma", "melanoma", false, null, true, 31),
            new SearchIndexStore.IndexedTerm("EX", "http://ex/aquifer", "aquifer", false, null, false, 0),
            new SearchIndexStore.IndexedTerm("EX", "http://ex/hound", "hound", true, "http://ex/dog", false, 0)),
        Map.of(
            "http://ex/melanoma", List.of(new SearchIndexStore.IndexedName("synonym", "en", "malignant melanoma")),
            "http://ex/aquifer", List.of(new SearchIndexStore.IndexedName("prefLabel", "fr", "aquifère"))));
    index.replaceOntology("OTHER", "hash-o1", "2026-08-13T00:00:00Z",
        List.of(new SearchIndexStore.IndexedTerm("OTHER", "http://other/melanoma", "melanoma", false, null, false, 0)),
        Map.of());
    index.rebuildFullText();
  }

  @AfterEach
  public void tearDown() throws Exception {
    index.close();
  }

  @Test
  public void theBundledSqliteHasFts5() {
    // Everything else here rests on it, so it is asserted rather than assumed.
    assertTrue(SearchIndexStore.supportsFts5());
  }

  @Test
  public void aScopeCoveringMostOfTheIndexStillFiltersCountsAndWholeLabelGroups() throws Exception {
    index.replaceOntology("EXCLUDED", "v1", "now",
        List.of(new SearchIndexStore.IndexedTerm("EXCLUDED", "http://excluded/melanoma", "melanoma",
            false, null, true, 10)), Map.of());
    index.rebuildFullText();
    var scope = List.of("EX", "OTHER");
    assertEquals(2, index.matchCount("melanoma", scope, false, 100));
    assertEquals(1, index.matchCount("melanoma", scope, true, 100));
    assertEquals(1, index.matchCount("melanoma", scope, false, true, 100));
    assertEquals(List.of("EX", "OTHER"), index.searchByLabelRange("melanoma", scope, false, 0, 1)
        .stream().map(h -> h.term().acronym()).sorted().toList());
    assertTrue(index.searchByLabelRange("melanoma", scope, false, 1, 1).isEmpty());
    assertEquals(3, index.matchCount("melanoma", List.of("EX", "OTHER", "EXCLUDED"), false, 100));
  }

  @Test
  public void aQuerySpansEveryIndexedOntology() throws Exception {
    List<SearchIndexStore.IndexHit> hits = index.search("melanoma", List.of(), 20);
    assertEquals(List.of("EX", "OTHER"),
        hits.stream().map(h -> h.term().acronym()).sorted().toList());
  }

  @Test
  public void aQueryCanBeNarrowedToNamedOntologies() throws Exception {
    List<SearchIndexStore.IndexHit> hits = index.search("melanoma", List.of("OTHER"), 20);
    assertEquals(1, hits.size());
    assertEquals("OTHER", hits.get(0).term().acronym());
  }

  @Test
  public void ontologyDiscoveryDoesNotLoseLaterSourcesOrCountMatchingNamesTwice() throws Exception {
    var many = java.util.stream.IntStream.range(0, 10_001)
        .mapToObj(i -> new SearchIndexStore.IndexedTerm("AAA", "http://aaa/" + i,
            "organ " + i, false, null, false, 0)).toList();
    index.replaceOntology("AAA", "a1", "now", many, Map.of());
    index.replaceOntology("UBERON", "u1", "now", List.of(
        new SearchIndexStore.IndexedTerm("UBERON", "http://uberon/organ", "organ", false, null, false, 0)),
        Map.of("http://uberon/organ", List.of(
            new SearchIndexStore.IndexedName("synonym", "en", "body organ"),
            new SearchIndexStore.IndexedName("synonym", "en", "organ system"))));

    assertEquals(List.of(new SearchIndexStore.VocabularyMatch("AAA", 10_001),
        new SearchIndexStore.VocabularyMatch("UBERON", 1)), index.vocabularyFacet("organ"));
    // A fresh lookup must reflect re-ingestion, including retired terms.
    index.replaceOntology("AAA", "a2", "later", List.of(), Map.of());
    assertEquals(List.of(new SearchIndexStore.VocabularyMatch("UBERON", 1)), index.vocabularyFacet("organ"));
  }

  @Test
  public void ontologyDiscoveryUsesTheSameResidualTokensAndDiacriticFoldingAsTermSearch() throws Exception {
    index.replaceOntology("CHEM", "c1", "now", List.of(
        new SearchIndexStore.IndexedTerm("CHEM", "http://chem/d", "D-mannitol", false, null, false, 0),
        new SearchIndexStore.IndexedTerm("CHEM", "http://chem/l", "L-mannitol", false, null, false, 0)), Map.of());
    assertEquals(List.of(new SearchIndexStore.VocabularyMatch("CHEM", 1)), index.vocabularyFacet("D-mannitol"));
    assertEquals(List.of(new SearchIndexStore.VocabularyMatch("EX", 1)), index.vocabularyFacet("aquifere"));
    assertEquals(List.of(), index.vocabularyFacet("?!"));
  }

  @Test
  public void aTokenMatchesAsAPrefix() throws Exception {
    // What a picker's search means: "melano" reaches melanoma while it is still being typed.
    assertEquals(2, index.search("melano", List.of(), 20).size());
    assertEquals(0, index.search("elanoma", List.of(), 20).size(),
        "a prefix, not a substring — this is where the index and the snapshot differ");
  }

  @Test
  public void diacriticsAreFolded() throws Exception {
    List<SearchIndexStore.IndexHit> hits = index.search("aquifere", List.of(), 20);
    assertEquals(1, hits.size(), "the snapshot's LIKE cannot do this: SQLite folds ASCII case only");
    assertEquals("aquifère", hits.get(0).matched().get(0).value());
    assertEquals("fr", hits.get(0).matched().get(0).lang());
  }

  @Test
  public void aTermMatchedThroughASynonymSaysSo() throws Exception {
    List<SearchIndexStore.IndexHit> hits = index.search("malignant", List.of(), 20);
    assertEquals(1, hits.size());
    assertEquals("melanoma", hits.get(0).term().prefLabel());
    assertEquals("malignant melanoma", hits.get(0).matched().get(0).value());
  }

  @Test
  public void structuralFactsTravelWithTheTerm() throws Exception {
    // The branch results need these, and reading them per row from a snapshot is what the index exists
    // to avoid.
    SearchIndexStore.IndexedTerm term = index.search("melanoma", List.of("EX"), 20).get(0).term();
    assertTrue(term.hasChildren());
    assertEquals(31, term.descendantCount());

    SearchIndexStore.IndexedTerm obsolete = index.search("hound", List.of(), 20).get(0).term();
    assertTrue(obsolete.obsolete());
    assertEquals("http://ex/dog", obsolete.replacedBy());
  }

  @Test
  public void reindexingAnOntologyReplacesItRatherThanAddingToIt() throws Exception {
    index.replaceOntology("EX", "hash-v2", "2026-08-14T00:00:00Z",
        List.of(new SearchIndexStore.IndexedTerm("EX", "http://ex/wolf", "wolf", false, null, false, 0)),
        Map.of());
    index.rebuildFullText();

    assertEquals("hash-v2", index.indexedVersion("EX").orElseThrow());
    assertTrue(index.search("wolf", List.of(), 20).size() == 1);
    // A re-ingest can retire a term; merging would leave it searchable against a version that no
    // longer contains it.
    assertEquals(0, index.search("aquifere", List.of(), 20).size());
    assertEquals(1, index.search("melanoma", List.of(), 20).size(), "OTHER's melanoma is untouched");
  }

  @Test
  public void theIndexKnowsWhatVersionItHoldsOfEach() throws Exception {
    assertEquals(Map.of("EX", "hash-v1", "OTHER", "hash-o1"), index.indexedVersions());
    assertEquals(2, index.indexedOntologyCount());
    assertEquals(4, index.termCount());
    assertTrue(index.indexedVersion("NOSUCH").isEmpty());
  }

  @Test
  public void anOntologyWhoseAcronymSplitsIntoTokensStillScopesToItself() throws Exception {
    // The tokenizer splits on punctuation, so an acronym written into the index as text would let a
    // search scoped to MESH answer from RH-MESH as well. 191 of the 1,266 acronyms carry a hyphen or
    // an underscore, so this is the common case rather than the odd one.
    index.replaceOntology("MESH", "hash-m1", "2026-08-13T00:00:00Z",
        List.of(new SearchIndexStore.IndexedTerm("MESH", "http://mesh/1", "aspirin", false, null, false, 0)),
        Map.of());
    index.replaceOntology("RH-MESH", "hash-r1", "2026-08-13T00:00:00Z",
        List.of(new SearchIndexStore.IndexedTerm("RH-MESH", "http://rh/1", "aspirin", false, null, false, 0)),
        Map.of());
    index.rebuildFullText();

    List<SearchIndexStore.IndexHit> mesh = index.search("aspirin", List.of("MESH"), 10);
    assertEquals(1, mesh.size());
    assertEquals("MESH", mesh.get(0).term().acronym());

    List<SearchIndexStore.IndexHit> rh = index.search("aspirin", List.of("RH-MESH"), 10);
    assertEquals(1, rh.size());
    assertEquals("RH-MESH", rh.get(0).term().acronym());
  }

  @Test
  public void oneTokenStandsForOneOntology() {
    // Stripping the punctuation instead would collide these, and they hold different terms.
    assertNotEquals(SearchIndexStore.ontToken("COVID-19"), SearchIndexStore.ontToken("COVID19"));
    assertNotEquals(SearchIndexStore.ontToken("APOLLO-SV"), SearchIndexStore.ontToken("APOLLO_SV"));
    // One token to the tokenizer, so a scope cannot be answered by half of an acronym.
    assertEquals(1, SearchIndexStore.matchTokens(SearchIndexStore.ontToken("RH-MESH")).size());
  }

  @Test
  public void punctuationInAQueryIsTextRatherThanSyntax() {
    // FTS5 reads bare -, *, ", ( and OR as operators, and ontology labels are full of them.
    assertEquals("\"or\"*", SearchIndexStore.toPrefixMatch("OR"));
    assertEquals("", SearchIndexStore.toPrefixMatch("  -  "));
    assertFalse(SearchIndexStore.toPrefixMatch("a\"b").contains("a\"b"));
  }

  @Test
  public void aShortTokenIsHeldBackOnlyWhenAnotherTokenCanCarryTheMatch() {
    // "diabetes" is long enough to find the rows on its own, so "2" waits and is applied to them.
    SearchIndexStore.MatchPlan held = SearchIndexStore.toMatchPlan("type-2 diabetes");
    assertEquals("\"type\"* \"diabetes\"*", held.match());
    assertEquals(List.of("2"), held.residual());

    // Nothing here can carry it, so the query is put to the index whole, exactly as before.
    SearchIndexStore.MatchPlan whole = SearchIndexStore.toMatchPlan("e coli");
    assertEquals("\"e\"* \"coli\"*", whole.match());
    assertTrue(whole.residual().isEmpty());

    // A query with nothing short about it is unchanged either way.
    assertEquals("\"mannitol\"*", SearchIndexStore.toMatchPlan("mannitol").match());
  }

  @Test
  public void aHeldBackTokenFoldsANameAsTheIndexDoes() {
    // The index reaches these names through "e" and "a", so a held-back token must as well.
    assertTrue(SearchIndexStore.carriesResidual("échoendoscopie colique", List.of("e")));
    assertTrue(SearchIndexStore.carriesResidual("groupe électrogène à essence", List.of("a")));
    assertTrue(SearchIndexStore.carriesResidual("Ångström unit", List.of("a")));
    assertFalse(SearchIndexStore.carriesResidual("école", List.of("c")));
    // A query typed with the diacritic is read as the same tokens.
    assertEquals(List.of("e", "coli"), SearchIndexStore.matchTokens("É coli"));
  }

  @Test
  public void foldingAgreesWithTheIndexTokenizerForLatinGreekAndCyrillicLetters() throws Exception {
    // One token around each code point, put through the tokenizer the index is built with.
    java.util.Map<Integer, List<String>> indexed = new java.util.TreeMap<>();
    try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
         java.sql.Statement st = c.createStatement()) {
      st.execute("CREATE VIRTUAL TABLE t USING fts5(v, tokenize=\"" + SearchIndexStore.TOKENIZER + "\")");
      st.execute("CREATE VIRTUAL TABLE tv USING fts5vocab(t, instance)");
      try (java.sql.PreparedStatement ps = c.prepareStatement("INSERT INTO t(rowid, v) VALUES (?, ?)")) {
        for (int codePoint = 0x80; codePoint <= 0x1EFF; codePoint++) {
          boolean covered = codePoint <= 0x0527 || codePoint >= 0x1E00;
          // U+037F entered Unicode after the tokenizer's tables were drawn, so it is not lower-cased.
          if (!covered || codePoint == 0x037F || Character.getType(codePoint) == Character.UNASSIGNED) {
            continue;
          }
          ps.setInt(1, codePoint);
          ps.setString(2, "q" + Character.toString(codePoint) + "z");
          ps.addBatch();
        }
        ps.executeBatch();
      }
      try (java.sql.ResultSet rs = st.executeQuery("SELECT doc, term FROM tv ORDER BY doc, offset")) {
        while (rs.next()) {
          indexed.computeIfAbsent(rs.getInt(1), k -> new java.util.ArrayList<>()).add(rs.getString(2));
        }
      }
    }
    assertTrue(indexed.size() > 1400, "the tokenizer answered for " + indexed.size() + " code points");
    for (var entry : indexed.entrySet()) {
      assertEquals(entry.getValue(), SearchIndexStore.matchTokens("q" + Character.toString(entry.getKey()) + "z"),
          () -> String.format("U+%04X %s", entry.getKey(), Character.getName(entry.getKey())));
    }
  }

  @Test
  public void aHeldBackTokenMatchesTheStartOfAWordJustAsTheIndexWould() {
    // The rule has to be the prefix match it replaced: "d" reaches "D2", which is why holding it
    // back cannot become an exact comparison.
    assertTrue(SearchIndexStore.carriesResidual("vitamin D2 deficiency", List.of("d")));
    assertTrue(SearchIndexStore.carriesResidual("N,N'-diphenylthiourea", List.of("n")));
    // Any token may carry it, which is why "deficiency" would answer for "d" and this does not.
    assertFalse(SearchIndexStore.carriesResidual("vitamin C", List.of("d")));
    assertTrue(SearchIndexStore.carriesResidual("anything", List.of()));
  }

  @Test
  public void anExactNameOutranksALongerOneThatMerelyContainsIt() throws Exception {
    // The cap truncates before a caller can reorder, so this ranking decides which candidates a
    // caller ever sees. Ordering by label length alone would lead with a coded vocabulary's "49".
    index.replaceOntology("CODED", "hash-c1", "2026-08-13T00:00:00Z",
        List.of(new SearchIndexStore.IndexedTerm("CODED", "http://coded/49", "49", false, null, false, 0),
            new SearchIndexStore.IndexedTerm("CODED", "http://coded/m", "melanoma of skin", false, null, false, 0)),
        Map.of("http://coded/49", List.of(new SearchIndexStore.IndexedName("synonym", "en", "melanoma stage 49"))));
    index.rebuildFullText();

    List<SearchIndexStore.IndexHit> hits = index.search("melanoma", List.of(), 10);
    assertEquals("melanoma", hits.get(0).term().prefLabel(), "the exact name first");
    assertEquals("49", hits.get(hits.size() - 1).term().prefLabel(), "the coded one last, not first");
  }

  @Test
  public void aQueryOfPurePunctuationMatchesNothingRatherThanThrowing() throws Exception {
    assertEquals(0, index.search("---", List.of(), 20).size());
    assertEquals(0, index.search("", List.of(), 20).size());
  }

  /**
   * Opening the index to serve it never creates the file. A mistyped path used to leave an empty
   * index there, and the server then reported corpus-wide search as enabled and found nothing.
   */
  @Test
  public void openingForReadRefusesAMissingFileAndCreatesNothing(@TempDir Path dir) {
    Path missing = dir.resolve("search-index.sqlite");

    assertThrows(SQLException.class, () -> SearchIndexStore.openForRead(missing.toString()));
    assertFalse(Files.exists(missing), "opening for read created the missing file");
  }

  /** An index opened to serve it cannot be written, on whichever connection a thread reads. */
  @Test
  public void anIndexOpenedForReadRefusesWrites(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("search-index.sqlite");
    writeIndex(file);
    try (SearchIndexStore served = SearchIndexStore.openForRead(file.toString())) {
      assertThrows(SQLException.class, () -> served.replaceOntology(
          "EX", "hash-v2", "2026-08-14T00:00:00Z", List.of(), Map.of()));
      assertEquals("hash-v1", served.indexedVersion("EX").orElseThrow());
    }
  }

  /**
   * The server checks the schema of the index it opens before serving it. On an index already at
   * the current schema every statement of that check changes nothing, so it passes read-only.
   */
  @Test
  public void theStartupSchemaCheckPassesOnAnIndexOpenedForRead(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("search-index.sqlite");
    writeIndex(file);
    try (SearchIndexStore served = SearchIndexStore.openForRead(file.toString())) {
      served.initSchema();
      assertEquals(1, served.indexedOntologyCount());
      assertEquals(1, served.termCount());
      assertEquals("melanoma", served.term("EX", "http://ex/melanoma").orElseThrow().prefLabel());
    }
  }

  private static void writeIndex(Path file) throws Exception {
    try (SearchIndexStore writer = SearchIndexStore.openFile(file.toString())) {
      writer.initSchema();
      writer.replaceOntology("EX", "hash-v1", "2026-08-13T00:00:00Z",
          List.of(new SearchIndexStore.IndexedTerm("EX", "http://ex/melanoma", "melanoma", false, null, false, 0)),
          Map.of());
    }
  }
}
