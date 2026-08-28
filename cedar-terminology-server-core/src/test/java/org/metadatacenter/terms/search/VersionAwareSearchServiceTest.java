package org.metadatacenter.terms.search;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.search.SearchRequest.SourceSelector;
import org.metadatacenter.terms.search.SearchRequest.VersionSelector;
import org.metadatacenter.terms.search.SearchResponse.BranchHit;
import org.metadatacenter.terms.search.SearchResponse.ClassHit;
import org.metadatacenter.terms.search.SearchResponse.OntologyHit;
import org.metadatacenter.terms.search.SearchResponse.SourceBlock;
import org.metadatacenter.terms.search.SearchResponse.TypeResults;
import org.metadatacenter.terms.search.SearchResponse.ValueSetHit;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SearchIndexStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Version-aware search over a catalog holding two versions of an ontology and one value-set
 * collection.
 *
 * The fixture is deliberately small enough to assert exact results: what a source block says about
 * the version that answered is as much the subject of these tests as the hits are.
 */
public class VersionAwareSearchServiceTest {

  private static final String BASE = "http://ex/";
  private static final String VS = "http://vs/";

  private Path tempDir;
  private CatalogStore catalog;
  private CatalogSnapshotProvider provider;
  private VersionAwareSearchService service;

  @BeforeEach
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("version-aware-search-test");

    // v1: a small mammal tree. "Cat" carries a French label and a synonym, so a search can be shown
    // reaching a concept through a name that is not the one displayed.
    Path v1 = tempDir.resolve("EX_v1.sqlite");
    try (SnapshotStore store = SnapshotStore.openFile(v1.toString())) {
      store.initSchema();
      store.addConcept(BASE + "thing", "Thing");
      store.addConcept(BASE + "animal", "Animal");
      store.addConcept(BASE + "mammal", "Mammal");
      store.addConcept(BASE + "cat", "Cat");
      store.addConcept(BASE + "dog", "Dog");
      store.addConcept(BASE + "hound", "Hound", true, BASE + "dog");
      store.addEdge(BASE + "animal", BASE + "thing", "rdfs:subClassOf");
      store.addEdge(BASE + "mammal", BASE + "animal", "rdfs:subClassOf");
      store.addEdge(BASE + "cat", BASE + "mammal", "rdfs:subClassOf");
      store.addEdge(BASE + "dog", BASE + "mammal", "rdfs:subClassOf");
      store.addLabels(List.of(
          new SnapshotStore.LabelRow(BASE + "cat", "rdfs:label", "fr", "chat domestique"),
          new SnapshotStore.LabelRow(BASE + "cat", "oboInOwl:hasExactSynonym", "en", "housecat")));
      store.addDefinitions(List.of(
          new SnapshotStore.DefinitionRow(BASE + "mammal", "IAO:0000115", "en", "A warm-blooded animal."),
          new SnapshotStore.DefinitionRow(BASE + "cat", "IAO:0000115", "en", "A small domesticated feline.")));
      store.materialize();
    }

    // v2 adds a wolf under mammal, so the two versions answer the same query differently.
    Path v2 = tempDir.resolve("EX_v2.sqlite");
    try (SnapshotStore store = SnapshotStore.openFile(v2.toString())) {
      store.initSchema();
      store.addConcept(BASE + "thing", "Thing");
      store.addConcept(BASE + "animal", "Animal");
      store.addConcept(BASE + "mammal", "Mammal");
      store.addConcept(BASE + "cat", "Cat");
      store.addConcept(BASE + "dog", "Dog");
      store.addConcept(BASE + "wolf", "Wolf");
      store.addEdge(BASE + "animal", BASE + "thing", "rdfs:subClassOf");
      store.addEdge(BASE + "mammal", BASE + "animal", "rdfs:subClassOf");
      store.addEdge(BASE + "cat", BASE + "mammal", "rdfs:subClassOf");
      store.addEdge(BASE + "dog", BASE + "mammal", "rdfs:subClassOf");
      store.addEdge(BASE + "wolf", BASE + "mammal", "rdfs:subClassOf");
      store.materialize();
    }

    // A value-set collection: one value set with three values beneath it.
    Path vs = tempDir.resolve("VSC_v1.sqlite");
    try (SnapshotStore store = SnapshotStore.openFile(vs.toString())) {
      store.initSchema();
      store.addConcept(VS + "analyte-class", "Analyte class");
      store.addConcept(VS + "protein", "Protein");
      store.addConcept(VS + "lipid", "Lipid");
      store.addConcept(VS + "nucleic-acid", "Nucleic acid");
      store.addEdge(VS + "protein", VS + "analyte-class", "skos:broader");
      store.addEdge(VS + "lipid", VS + "analyte-class", "skos:broader");
      store.addEdge(VS + "nucleic-acid", VS + "analyte-class", "skos:broader");
      store.materialize();
    }

    catalog = CatalogStore.openFile(tempDir.resolve("catalog.sqlite").toString());
    catalog.initSchema();
    catalog.upsertOntology(new CatalogStore.OntologyInfo("EX", "Example Mammal Ontology", null, "OWL"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("hash-v1", "EX", "1.0", "2025-01-01",
        "2025-01-02T00:00:00Z", "OWL", "subsumption", 6, 4, v1.toString(), "f1", "open"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("hash-v2", "EX", "2.0", "2025-06-01",
        "2025-06-02T00:00:00Z", "OWL", "subsumption", 6, 5, v2.toString(), "f2", "open"));
    catalog.setTag("EX", CatalogStore.TAG_LATEST, "hash-v2");
    catalog.setOntologyIri("EX", "http://ex", BASE);

    catalog.upsertOntology(new CatalogStore.OntologyInfo("VSC", "Example Value Sets", null, "OWL"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("hash-vs1", "VSC", "1.0", "2025-03-01",
        "2025-03-02T00:00:00Z", "OWL", "subsumption", 4, 3, vs.toString(), "f3", "open"));
    catalog.setTag("VSC", CatalogStore.TAG_LATEST, "hash-vs1");
    catalog.setOntologyKind("VSC", CatalogStore.KIND_VALUE_SET_COLLECTION);

    // NOTSERVED is in the catalog but not the allowlist; UNKNOWN is in neither.
    catalog.upsertOntology(new CatalogStore.OntologyInfo("NOTSERVED", "Absent Ontology", null, "OWL"));

    provider = new CatalogSnapshotProvider(catalog, Set.of("EX", "VSC"));
    service = new VersionAwareSearchService(provider);
  }

  @AfterEach
  public void tearDown() throws Exception {
    provider.close();
    if (tempDir != null) {
      try (var paths = Files.walk(tempDir)) {
        paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    }
  }

  private static SearchRequest request(String query, List<String> types, List<SourceSelector> sources) {
    return new SearchRequest(query, types, sources, null, 1, 20);
  }

  private static SourceSelector ex(String versionId) {
    return new SourceSelector(null, "EX", versionId == null ? null : new VersionSelector(versionId));
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> hits(SearchResponse response, String type) {
    return (List<T>) response.results().get(type).collection();
  }

  @Test
  public void latestAnswersUnlessAVersionIsPinned() throws Exception {
    SearchResponse latest = service.search(request("wolf", List.of("class"), List.of(ex(null))));
    assertEquals(1, latest.results().get("class").totalCount(), "v2 is latest and holds the wolf");

    SearchResponse pinned = service.search(request("wolf", List.of("class"), List.of(ex("hash-v1"))));
    assertEquals(0, pinned.results().get("class").totalCount(), "v1 predates the wolf");

    // The point of the endpoint: searching latest while pinning v1 would offer a term the pinned
    // version does not contain.
    assertEquals("hash-v1", pinned.sources().get(0).version().id());
    assertEquals("hash-v2", latest.sources().get(0).version().id());
  }

  @Test
  public void aServedSourceReportsTheSnapshotThatAnswered() throws Exception {
    SearchResponse response = service.search(request("cat", List.of("class"), List.of(ex(null))));
    SourceBlock block = response.sources().get(0);
    assertEquals("bioportal", block.sourceSystem(), "an absent sourceSystem means BioPortal");
    assertEquals("EX", block.sourceAcronym());
    assertEquals("Example Mammal Ontology", block.sourceName());
    assertEquals("http://ex", block.sourceIri());
    assertEquals(SourceBlock.SERVED_LOCAL, block.served());
    assertTrue(block.pinnable());
    assertEquals("hash-v2", block.version().id());
    assertEquals("2.0", block.version().declaredVersion());
    assertNull(block.reason());
  }

  @Test
  public void aPinThatTheStoreDoesNotHoldIsReportedWithoutFailingTheSearch() throws Exception {
    SearchResponse response = service.search(request("cat", List.of("class"),
        List.of(ex("hash-v1"), new SourceSelector(null, "VSC", new VersionSelector("no-such-hash")))));

    SourceBlock missing = response.sources().get(1);
    assertEquals(SourceBlock.SERVED_UNAVAILABLE, missing.served());
    assertFalse(missing.pinnable());
    assertEquals(SourceBlock.REASON_VERSION_NOT_HELD, missing.reason());
    assertEquals("no-such-hash", missing.requestedVersion().id());
    // The other source still answered: a search across sources has a partial answer worth returning.
    assertEquals(1, response.results().get("class").totalCount());
  }

  @Test
  public void aSourceMayBeNamedOnlyOnce() {
    // Two versions of one acronym would leave every hit from it unable to say which it came from,
    // because a hit carries the addressing pair and no version of its own.
    assertThrows(VersionAwareSearchService.BadSearchRequestException.class,
        () -> service.search(request("cat", List.of("class"),
            List.of(ex("hash-v1"), ex("hash-v2")))));
  }

  @Test
  public void anUnservedSourceIsReportedRatherThanSilentlyOmitted() throws Exception {
    SearchResponse response = service.search(request("cat", List.of("class"),
        List.of(ex(null), new SourceSelector(null, "NOTSERVED", null),
            new SourceSelector("agroportal", "UNKNOWN", null))));

    assertEquals(SourceBlock.REASON_SOURCE_NOT_SERVED, response.sources().get(1).reason());
    assertEquals(SourceBlock.REASON_SOURCE_UNKNOWN, response.sources().get(2).reason());
    // Never proxied to BioPortal, whatever the system: the results say nothing about these two, which
    // is exactly why they appear.
    assertEquals(SourceBlock.SERVED_UNAVAILABLE, response.sources().get(2).served());
    assertEquals("agroportal", response.sources().get(2).sourceSystem());
  }

  @Test
  public void aHitFoundThroughAnotherNameSaysWhatMatched() throws Exception {
    SearchResponse response = service.search(new SearchRequest("chat", List.of("class"),
        List.of(ex("hash-v1")), null, 1, 20));

    List<ClassHit> hits = hits(response, "class");
    assertEquals(1, hits.size());
    ClassHit cat = hits.get(0);
    assertEquals("Cat", cat.termLabel(), "the served label, not the one that matched");
    assertEquals(SearchResponse.MATCH_SYNONYM, cat.matchType());
    assertEquals("chat domestique", cat.matchedLabels().get(0).label());
    assertEquals("fr", cat.matchedLabels().get(0).language());
  }

  @Test
  public void aHitOnTheLabelItselfNeedsNoExplanation() throws Exception {
    List<ClassHit> hits = hits(service.search(request("Cat", List.of("class"), List.of(ex("hash-v1")))), "class");
    assertEquals(SearchResponse.MATCH_TERM_LABEL, hits.get(0).matchType());
    assertNull(hits.get(0).matchedLabels());
  }

  @Test
  public void langChoosesTheLabelShown() throws Exception {
    SearchResponse response = service.search(new SearchRequest("cat", List.of("class"),
        List.of(ex("hash-v1")), "fr", 1, 20));
    assertEquals("chat domestique", ((ClassHit) response.results().get("class").collection().get(0)).termLabel());
  }

  @Test
  public void anObsoleteTermIsShownMarkedAndDemoted() throws Exception {
    // "hound" is obsolete and replaced by "dog"; both match "ound"/"og" poorly, so query them together.
    List<ClassHit> hits = hits(service.search(request("o", List.of("class"), List.of(ex("hash-v1")))), "class");
    ClassHit hound = hits.stream().filter(h -> h.termIri().endsWith("hound")).findFirst().orElseThrow();
    assertTrue(hound.obsolete());
    assertEquals(BASE + "dog", hound.replacedBy().termIri());
    assertEquals("Dog", hound.replacedBy().termLabel());
    // Demoted: every live hit precedes it.
    assertEquals(hits.size() - 1, hits.indexOf(hound));
  }

  @Test
  public void branchesAreTheHitsThatHaveDescendants() throws Exception {
    SearchResponse response = service.search(request("a", List.of("class", "branch"), List.of(ex("hash-v1"))));
    List<BranchHit> branches = hits(response, "branch");

    assertTrue(response.results().get("class").totalCount() > branches.size(),
        "a branch is a class hit with something beneath it, so there are fewer");
    assertTrue(branches.stream().allMatch(b -> b.descendantCount() > 0));

    BranchHit mammal = branches.stream().filter(b -> b.termBaseIri().endsWith("mammal")).findFirst().orElseThrow();
    assertEquals(2, mammal.descendantCount(), "Cat and Dog");
    // The path is what separates one "mammal" from another, and runs root-first.
    assertEquals(List.of(BASE + "thing", BASE + "animal"),
        mammal.path().stream().map(SearchResponse.TermRef::termIri).toList());
    assertEquals(2, mammal.examples().size());
  }

  @Test
  public void ontologiesMatchOnNameAndAcronymRatherThanOnTheirContent() throws Exception {
    // Named for what it is: no ontology is called "cat", so the tab is empty even though terms match.
    assertEquals(0, service.search(request("cat", List.of("ontology"), List.of())).results()
        .get("ontology").totalCount());

    List<OntologyHit> byName = hits(service.search(request("mammal", List.of("ontology"), List.of())), "ontology");
    assertEquals(1, byName.size());
    assertEquals("EX", byName.get(0).sourceAcronym());
    assertEquals(SearchResponse.MATCH_SOURCE_NAME, byName.get(0).matchType());

    List<OntologyHit> byAcronym = hits(service.search(request("ex", List.of("ontology"), List.of())), "ontology");
    assertEquals(SearchResponse.MATCH_SOURCE_ACRONYM, byAcronym.get(0).matchType());
  }

  @Test
  public void anOntologyHitBringsItsSourceBlockWithIt() throws Exception {
    // The hit carries only the addressing pair, so without a block for it a caller that named no
    // sources is handed an acronym and no way to learn the ontology's name, IRI or version.
    SearchResponse response = service.search(request("mammal", List.of("ontology"), List.of()));
    assertEquals(1, response.results().get("ontology").totalCount());
    assertEquals(1, response.sources().size());
    SourceBlock block = response.sources().get(0);
    assertEquals("EX", block.sourceAcronym());
    assertEquals("Example Mammal Ontology", block.sourceName());
    assertEquals("hash-v2", block.version().id());
  }

  @Test
  public void theOntologyResultsCanIgnoreNamesAndRankByMatchesInstead() throws Exception {
    // Browsing and narrowing want different orders. A vocabulary named after the query leads when an
    // author is looking for one, and is the wrong thing to narrow to when it holds few of the terms.
    SearchRequest byMatches = new SearchRequest("mammal", List.of("ontology"), List.of(), null, 1, 20,
        null, SearchRequest.ORDER_BY_MATCHES);
    assertNotNull(withIndex().search(byMatches).results().get("ontology"));
    assertTrue(new SearchRequest("x", null, null, null, null, null, null,
        SearchRequest.ORDER_BY_MATCHES).ordersOntologiesByMatches());
    assertFalse(new SearchRequest("x", null, null, null, null, null).ordersOntologiesByMatches());
  }

  @Test
  public void ontologySearchNeedsNoSourceButEveryOtherTypeDoes() throws Exception {
    assertNotNull(service.search(request("mammal", List.of("ontology"), List.of())));
    assertThrows(VersionAwareSearchService.BadSearchRequestException.class,
        () -> service.search(request("cat", List.of("class"), List.of())));
  }

  @Test
  public void aValueSetIsFoundByItsOwnNameOrByOneOfItsValues() throws Exception {
    SourceSelector vsc = new SourceSelector(null, "VSC", null);

    List<ValueSetHit> byName = hits(service.search(request("analyte", List.of("valueSet"), List.of(vsc))), "valueSet");
    assertEquals(1, byName.size());
    assertEquals("Analyte class", byName.get(0).termBaseLabel());
    assertEquals(3, byName.get(0).termCount());
    assertEquals(SearchResponse.MATCH_TERM_BASE_LABEL, byName.get(0).matchType());

    List<ValueSetHit> byValue = hits(service.search(request("protein", List.of("valueSet"), List.of(vsc))), "valueSet");
    assertEquals(1, byValue.size(), "a matched value surfaces the value set that holds it");
    assertEquals(VS + "analyte-class", byValue.get(0).termBaseIri());
    assertEquals(SearchResponse.MATCH_MEMBER, byValue.get(0).matchType());
    assertEquals(VS + "protein", byValue.get(0).matchedTerms().get(0).termIri());
  }

  @Test
  public void everyRequestedTypeIsAnsweredAndTheOthersAreAbsent() throws Exception {
    SearchResponse response = service.search(request("a", List.of("class", "branch"), List.of(ex(null))));
    assertEquals(Set.of("branch", "class"), response.results().keySet());
    // Reported in the order the constraint spec lists the types, not the order they were asked for.
    assertEquals(List.of("branch", "class"), List.copyOf(response.results().keySet()));
  }

  /* ------------------------------------------------------------------------------------------------
   * Corpus-wide search, through the cross-snapshot index.
   * --------------------------------------------------------------------------------------------- */

  private VersionAwareSearchService withIndex() throws Exception {
    SearchIndexStore index = SearchIndexStore.openInMemory();
    index.initSchema();
    index.replaceOntology("EX", "hash-v2", "2026-08-13T00:00:00Z",
        List.of(new SearchIndexStore.IndexedTerm("EX", BASE + "mammal", "Mammal", false, null, true, 3,
                null, null, "A warm-blooded animal."),
            new SearchIndexStore.IndexedTerm("EX", BASE + "cat", "Cat", false, null, false, 0, null, null,
                "A small domesticated feline.")),
        java.util.Map.of(BASE + "cat",
            List.of(new SearchIndexStore.IndexedName("rdfs:label", "fr", "chat domestique"))));
    index.replaceOntology("OTHER", "hash-o1", "2026-08-13T00:00:00Z",
        List.of(new SearchIndexStore.IndexedTerm("OTHER", "http://other/cat", "Cat", false, null, false, 0)),
        java.util.Map.of());
    index.rebuildFullText();
    return new VersionAwareSearchService(provider, index);
  }

  @Test
  public void withAnIndexASearchNeedsNoSource() throws Exception {
    SearchResponse response = withIndex().search(request("cat", List.of("class"), List.of()));
    assertEquals(2, response.results().get("class").totalCount(), "across every indexed ontology");
    assertEquals(List.of("EX", "OTHER"),
        response.sources().stream().map(SourceBlock::sourceAcronym).sorted().toList());
  }

  @Test
  public void aCorpusWideResultReportsTheVersionTheIndexHolds() throws Exception {
    // Not the catalog's latest. An ontology re-ingested since the index was built was searched at the
    // older snapshot, and saying otherwise would credit results to a version that did not produce them.
    SearchIndexStore stale = SearchIndexStore.openInMemory();
    stale.initSchema();
    stale.replaceOntology("EX", "hash-v1", "2026-08-13T00:00:00Z",
        List.of(new SearchIndexStore.IndexedTerm("EX", BASE + "cat", "Cat", false, null, false, 0)),
        java.util.Map.of());
    stale.rebuildFullText();

    SearchResponse response = new VersionAwareSearchService(provider, stale)
        .search(request("cat", List.of("class"), List.of()));
    assertEquals("hash-v1", response.sources().get(0).version().id());
    assertEquals("1.0", response.sources().get(0).version().declaredVersion());
  }

  @Test
  public void aCorpusWideBranchCarriesItsCountButNotItsPath() throws Exception {
    List<BranchHit> branches = hits(withIndex().search(request("mammal", List.of("branch"), List.of())), "branch");
    assertEquals(1, branches.size());
    assertEquals(3, branches.get(0).descendantCount());
    // Absent rather than wrong: the path needs the snapshot, and narrowing to the source returns it.
    assertNull(branches.get(0).path());
    assertNull(branches.get(0).examples());
  }

  @Test
  public void aCorpusWideHitStillSaysWhatMatched() throws Exception {
    List<ClassHit> hits = hits(withIndex().search(request("chat", List.of("class"), List.of())), "class");
    assertEquals(1, hits.size());
    assertEquals("Cat", hits.get(0).termLabel());
    assertEquals(SearchResponse.MATCH_SYNONYM, hits.get(0).matchType());
    assertEquals("fr", hits.get(0).matchedLabels().get(0).language());
  }

  @Test
  public void valueSetsAreSearchedAcrossEveryCollectionTheCatalogKnows() throws Exception {
    // The index does not record which value set holds a value, so a corpus-wide request searches the
    // collections instead — a bounded set the author should not have to name.
    SearchResponse response = withIndex().search(request("analyte", List.of("valueSet"), List.of()));
    List<ValueSetHit> hits = hits(response, "valueSet");
    assertEquals(1, hits.size());
    assertEquals("Analyte class", hits.get(0).termBaseLabel());
    assertEquals("VSC", hits.get(0).sourceAcronym());
  }

  @Test
  public void narrowingToASourceKeepsTheIndexRatherThanChangingTheSearch() throws Exception {
    // Narrowing is the same search over less. It keeps the index's matching and paging, so an author
    // who names an ontology does not get subtly different results from the ones they just saw.
    SearchResponse narrowed = withIndex().search(
        request("cat", List.of("class"), List.of(new SourceSelector(null, "EX", null))));
    assertEquals(1, narrowed.results().get("class").totalCount());
    assertNotNull(narrowed.results().get("class").distinctLabelCount(),
        "the collapsed count comes from the index, so narrowing keeps it");
  }

  @Test
  public void aNamedSourceBehindTheIndexIsAnsweredFromItsSnapshot() throws Exception {
    // The index still holds v1 while the catalog serves v2. Answering the named source from the
    // index would credit v1's hits to the v2 block resolveSource built — the mis-attribution the
    // corpus-wide path handles by reporting the indexed version. A named source has the escape the
    // corpus does not: its snapshot.
    SearchIndexStore stale = SearchIndexStore.openInMemory();
    stale.initSchema();
    stale.replaceOntology("EX", "hash-v1", "2026-08-13T00:00:00Z",
        List.of(new SearchIndexStore.IndexedTerm("EX", BASE + "cat", "Cat", false, null, false, 0)),
        java.util.Map.of());
    stale.rebuildFullText();

    SearchResponse response = new VersionAwareSearchService(provider, stale)
        .search(request("wolf", List.of("class"), List.of(ex(null))));
    assertEquals(1, response.results().get("class").totalCount(),
        "the catalog serves v2, which holds the wolf; the stale index does not");
    assertEquals("hash-v2", response.sources().get(0).version().id(),
        "the block names the version that answered");
    assertNull(response.results().get("class").distinctLabelCount(),
        "the snapshot path counts hits, not labels");
  }

  @Test
  public void aNamedSourceAbsentFromTheIndexIsAnsweredFromItsSnapshot() throws Exception {
    // Served but never indexed. The index would return zero hits under a block saying the source
    // was searched at its current version — the silent wrong answer the blocks exist to prevent.
    SearchIndexStore other = SearchIndexStore.openInMemory();
    other.initSchema();
    other.replaceOntology("OTHER", "hash-o1", "2026-08-13T00:00:00Z",
        List.of(new SearchIndexStore.IndexedTerm("OTHER", "http://other/cat", "Cat", false, null, false, 0)),
        java.util.Map.of());
    other.rebuildFullText();

    SearchResponse response = new VersionAwareSearchService(provider, other)
        .search(request("cat", List.of("class"), List.of(ex(null))));
    assertEquals(1, response.results().get("class").totalCount(), "the snapshot holds the cat");
    assertEquals("hash-v2", response.sources().get(0).version().id());
  }

  @Test
  public void pinningAVersionLeavesTheIndexForTheSnapshot() throws Exception {
    // The index holds current versions and no others, so a pinned search has to read the snapshot.
    SearchResponse pinned = withIndex().search(
        request("wolf", List.of("class"), List.of(ex("hash-v1"))));
    assertEquals(0, pinned.results().get("class").totalCount(), "v1 predates the wolf");
    assertNull(pinned.results().get("class").distinctLabelCount(),
        "the snapshot path counts hits, not labels");
  }

  @Test
  public void aCorpusWideSearchNeedsSomethingToSearchFor() throws Exception {
    VersionAwareSearchService service = withIndex();
    assertThrows(VersionAwareSearchService.BadSearchRequestException.class,
        () -> service.search(request("", List.of("class"), List.of())));
  }

  @Test
  public void anUnknownTypeIsRefusedRatherThanIgnored() {
    assertThrows(VersionAwareSearchService.BadSearchRequestException.class,
        () -> service.search(request("cat", List.of("nonsense"), List.of(ex(null)))));
  }

  @Test
  public void pagingSlicesTheResultsAndReportsTheWholeCount() throws Exception {
    SearchRequest first = new SearchRequest("a", List.of("class"), List.of(ex(null)), null, 1, 2);
    TypeResults page1 = service.search(first).results().get("class");
    TypeResults page2 = service.search(new SearchRequest("a", List.of("class"), List.of(ex(null)), null, 2, 2))
        .results().get("class");

    assertEquals(2, page1.collection().size());
    assertEquals(page1.totalCount(), page2.totalCount());
    assertFalse(page1.countCapped(), "a small result set is counted, not capped");
    assertTrue(page1.totalCount() > 2);
  }

  @Test
  public void versionLatestMeansUnpinned() {
    assertNull(VersionSelector.of(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree("latest")));
    assertNull(VersionSelector.of(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree("")));
    assertEquals("abc", VersionSelector.of(
        new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(java.util.Map.of("id", "abc"))).id());
  }

  /* ---------------------------------------------------------------------------------------------
   * Where a term sits, and what it means.
   * --------------------------------------------------------------------------------------------- */

  /** The tree, or a failure the assertion names rather than an unhelpful empty. */
  private static HierarchyResponse treeOf(HierarchyLookup lookup) {
    if (lookup instanceof HierarchyLookup.Found found) {
      return found.hierarchy();
    }
    throw new AssertionError("expected a hierarchy, got " + lookup);
  }

  @Test
  public void aHierarchySaysWhatTheTermItselfMeansAndNotOnlyItsChildren() throws Exception {
    HierarchyResponse tree = treeOf(withIndex().hierarchy("EX", BASE + "mammal", null, 0));
    assertEquals("A warm-blooded animal.", tree.definition(),
        "the one term the response is about had nothing said about it");
  }

  @Test
  public void aPinnedHierarchySaysWhatTheTermMeantInThatRelease() throws Exception {
    HierarchyResponse tree = treeOf(service.hierarchy("EX", BASE + "mammal", "hash-v1", 0));
    assertEquals("A warm-blooded animal.", tree.definition());
    assertEquals("A small domesticated feline.", tree.children().get(0).definition());
  }

  /*
   * The three ways there is no tree, told apart.
   *
   * They were one empty answer, so a caller reported whichever it guessed at — and the picker
   * reported the store as holding no hierarchy for a term even when the release named did not
   * exist, a case in which no term had been looked at. ICO is the real example behind these: its
   * 2020 release predates its import of MONDO, so a term present in the three later releases is
   * genuinely absent from that one, which is a different thing to tell an author than either a
   * misspelled release or a term the store does not hold at all.
   */

  @Test
  public void aReleaseNothingAnswersToIsNotReportedAsAMissingTerm() throws Exception {
    HierarchyLookup lookup = service.hierarchy("EX", BASE + "mammal", "hash-nonesuch", 0);
    assertInstanceOf(HierarchyLookup.ReleaseNotHeld.class, lookup,
        "an unresolvable release was reported as something about the term");
  }

  @Test
  public void anAbbreviatedReleaseIdIsNotAReleaseTheStoreHolds() throws Exception {
    HierarchyLookup lookup = service.hierarchy("EX", BASE + "mammal", "hash", 0);
    assertInstanceOf(HierarchyLookup.ReleaseNotHeld.class, lookup,
        "a prefix of a release identifier resolved to a release, or was blamed on the term");
  }

  @Test
  public void aTermAbsentFromThePinnedReleaseSaysSoAndNamesTheRelease() throws Exception {
    HierarchyLookup lookup = service.hierarchy("EX", BASE + "nosuchterm", "hash-v1", 0);
    HierarchyLookup.TermNotInRelease absent = assertInstanceOf(HierarchyLookup.TermNotInRelease.class, lookup,
        "a term absent from a release the store holds was not reported as such");
    assertEquals("hash-v1", absent.versionId(), "the answer did not say which release was read");
  }

  @Test
  public void aTermTheIndexDoesNotHoldIsItsOwnAnswer() throws Exception {
    HierarchyLookup lookup = withIndex().hierarchy("EX", BASE + "nosuchterm", null, 0);
    assertInstanceOf(HierarchyLookup.TermNotInIndex.class, lookup);
  }
}
