package org.metadatacenter.terms.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
  public void aLabelTheQueryBeginsComesBeforeOneThatMerelyCarriesIt() throws Exception {
    // The property the fast path rests on. A page filled from labels the query begins can contain
    // nothing from labels that carry it elsewhere, because the first always outrank the second — so
    // filling a page that way is not an approximation of the ranked answer, it is the ranked answer.
    index.replaceOntology("EX2", "hash-x1", "2026-08-13T00:00:00Z",
        List.of(
            new SearchIndexStore.IndexedTerm("EX2", "http://x/1", "cell membrane", false, null, false, 0),
            new SearchIndexStore.IndexedTerm("EX2", "http://x/2", "T cell", false, null, false, 0),
            new SearchIndexStore.IndexedTerm("EX2", "http://x/3", "cell", false, null, false, 0)),
        Map.of());
    index.rebuildFullText();

    List<String> page = index.searchByLabelPage("cell", List.of(), false, 1, 2).stream()
        .map(hit -> hit.term().prefLabel()).toList();
    assertEquals(List.of("cell", "cell membrane"), page,
        "the exact name, then the one it begins — never the one carrying it in second position");

    // Asking for more than the prefixes can fill falls back, and the fallback finds the rest.
    List<String> all = index.searchByLabelPage("cell", List.of(), false, 1, 10).stream()
        .map(hit -> hit.term().prefLabel()).toList();
    assertTrue(all.contains("T cell"), "a label carrying the query is reached once the page needs it");
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
}
