package org.metadatacenter.terms.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  public void punctuationInAQueryIsTextRatherThanSyntax() {
    // FTS5 reads bare -, *, ", ( and OR as operators, and ontology labels are full of them.
    assertEquals("\"type\"* \"2\"* \"diabetes\"*", SearchIndexStore.toPrefixMatch("type-2 diabetes"));
    assertEquals("\"or\"*", SearchIndexStore.toPrefixMatch("OR"));
    assertEquals("", SearchIndexStore.toPrefixMatch("  -  "));
    assertFalse(SearchIndexStore.toPrefixMatch("a\"b").contains("a\"b"));
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
