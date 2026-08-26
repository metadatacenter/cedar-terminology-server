package org.metadatacenter.terms;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.domainObjects.VersionTriple;
import org.metadatacenter.terms.store.SnapshotStore;
import org.metadatacenter.terms.store.SearchIndexStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The adapter over a hand-populated in-memory snapshot (the same DAG used by the store tests),
 * exposed under the ontology acronym "EX".
 */
public class SqliteTerminologyServiceTest {

  private static final String EX = "EX";
  private SnapshotStore store;
  private SqliteTerminologyService service;

  private static String iri(String s) {
    return "http://ex/" + s;
  }

  @BeforeEach
  public void setUp() throws Exception {
    store = SnapshotStore.openInMemory();
    store.initSchema();
    for (String[] c : new String[][]{
        {"thing", "Thing"}, {"animal", "Animal"}, {"mammal", "Mammal"},
        {"cat", "Cat"}, {"dog", "Dog"}, {"pet", "Pet"}}) {
      store.addConcept(iri(c[0]), c[1]);
    }
    store.addEdge(iri("animal"), iri("thing"), "rdfs:subClassOf");
    store.addEdge(iri("mammal"), iri("animal"), "rdfs:subClassOf");
    store.addEdge(iri("cat"), iri("mammal"), "rdfs:subClassOf");
    store.addEdge(iri("dog"), iri("mammal"), "rdfs:subClassOf");
    store.addEdge(iri("pet"), iri("thing"), "rdfs:subClassOf");
    store.addEdge(iri("dog"), iri("pet"), "rdfs:subClassOf");
    store.materialize();

    SqliteTerminologyService.SnapshotProvider provider =
        ontology -> EX.equals(ontology) ? Optional.of(store) : Optional.empty();
    service = new SqliteTerminologyService(provider);
  }

  @AfterEach
  public void tearDown() throws Exception {
    store.close();
  }

  private static List<String> ids(List<OntologyClass> classes) {
    return classes.stream().map(OntologyClass::getId).collect(Collectors.toList());
  }

  @Test
  public void isAvailable_reflectsProvider() {
    assertTrue(service.isAvailable(EX));
    assertFalse(service.isAvailable("NOPE"));
    assertFalse(service.isAvailable(null));
  }

  @Test
  public void findClass_mapsIdIriLabelAndHasChildren() throws Exception {
    OntologyClass dog = service.findClass(iri("dog"), EX, null);
    assertEquals("dog", dog.getId());
    assertEquals(iri("dog"), dog.getLdId());
    assertEquals("Dog", dog.getPrefLabel());
    assertEquals(EX, dog.getOntology());
    assertFalse(dog.getHasChildren());

    OntologyClass mammal = service.findClass(iri("mammal"), EX, null);
    assertTrue(mammal.getHasChildren());
  }

  @Test
  public void findClass_unknownConceptIsNull() throws Exception {
    assertNull(service.findClass(iri("unicorn"), EX, null));
  }

  @Test
  public void getRootClasses_returnsTop() throws Exception {
    assertEquals(List.of("thing"), ids(service.getRootClasses(EX, false, null)));
  }

  @Test
  public void getClassChildren_arePagedDirectChildren() throws Exception {
    PagedResults<OntologyClass> kids = service.getClassChildren(iri("mammal"), EX, 1, 50, null);
    assertEquals(Integer.valueOf(2), kids.getTotalCount());
    assertEquals(List.of("cat", "dog"), ids(kids.getCollection()));
  }

  @Test
  public void getClassDescendants_areTransitive() throws Exception {
    PagedResults<OntologyClass> desc = service.getClassDescendants(iri("thing"), EX, 1, 50, null);
    assertEquals(List.of("animal", "cat", "dog", "mammal", "pet"), ids(desc.getCollection()));
  }

  @Test
  public void getClassParents_includeMultipleInheritance() throws Exception {
    assertEquals(List.of("mammal", "pet"), ids(service.getClassParents(iri("dog"), EX, null)));
  }

  @Test
  public void unavailableOntologyThrows() {
    assertThrows(UnsupportedOperationException.class, () -> service.findClass(iri("dog"), "NOPE", null));
  }

  @Test
  public void unsupportedOperationsThrow() {
    assertThrows(UnsupportedOperationException.class, () -> service.getClassTree(iri("dog"), EX, false, null));
    assertThrows(UnsupportedOperationException.class, () -> service.findProperty(iri("dog"), EX, null));
  }

  @Test
  public void getClassChildren_paginates() throws Exception {
    // mammal has two children: cat, dog (ordered by iri)
    PagedResults<OntologyClass> page1 = service.getClassChildren(iri("mammal"), EX, 1, 1, null);
    assertEquals(Integer.valueOf(2), page1.getTotalCount());
    assertEquals(Integer.valueOf(2), page1.getPageCount());
    assertEquals(List.of("cat"), ids(page1.getCollection()));

    PagedResults<OntologyClass> page2 = service.getClassChildren(iri("mammal"), EX, 2, 1, null);
    assertEquals(List.of("dog"), ids(page2.getCollection()));
  }

  /* search + enumeration */

  private static List<String> ldIds(List<SearchResult> results) {
    return results.stream().map(SearchResult::getLdId).collect(Collectors.toList());
  }

  private PagedResults<SearchResult> classSearch(String q, int page, int pageSize) throws Exception {
    return service.search(q, List.of("classes"), List.of(EX), false, null, null, 0, page, pageSize,
        false, false, null, null);
  }

  @Test
  public void search_matchesLabelsInOntologyShortestFirst() throws Exception {
    // Labels containing "a": Cat (shortest), then Animal, Mammal. Results carry the class IRI as @id.
    assertEquals(List.of(iri("cat"), iri("animal"), iri("mammal")), ldIds(classSearch("a", 1, 50).getCollection()));
    // Result fields match the BioPortal SearchResult shaping.
    SearchResult first = classSearch("a", 1, 50).getCollection().get(0);
    assertEquals("cat", first.getId());
    assertEquals("Cat", first.getPrefLabel());
    assertEquals("OntologyClass", first.getType());
  }

  @Test
  public void search_paginatesWithBioPortalSemantics() throws Exception {
    PagedResults<SearchResult> p1 = classSearch("a", 1, 1);
    assertEquals(List.of(iri("cat")), ldIds(p1.getCollection()));
    assertEquals(Integer.valueOf(3), p1.getTotalCount());
    assertEquals(Integer.valueOf(3), p1.getPageCount());
    assertEquals(Integer.valueOf(1), p1.getPageSize()); // items on this page, not the requested size
    assertNull(p1.getPrevPage());
    assertEquals(Integer.valueOf(2), p1.getNextPage());

    PagedResults<SearchResult> p2 = classSearch("a", 2, 1);
    assertEquals(List.of(iri("animal")), ldIds(p2.getCollection()));
    assertEquals(Integer.valueOf(1), p2.getPrevPage());
    assertEquals(Integer.valueOf(3), p2.getNextPage());
  }

  @Test
  public void search_emptyMatchUsesBioPortalEmptyContract() throws Exception {
    PagedResults<SearchResult> r = classSearch("zzz", 1, 50);
    assertTrue(r.getCollection().isEmpty());
    assertEquals(Integer.valueOf(0), r.getTotalCount());
    assertEquals(Integer.valueOf(0), r.getPageCount());
    assertEquals(Integer.valueOf(0), r.getPageSize());
  }

  @Test
  public void search_branchScopedRestrictsToSubtree() throws Exception {
    // Within the mammal branch (descendants cat + dog; the root mammal is excluded), labels
    // containing "a": Cat.
    PagedResults<SearchResult> r = service.search("a", List.of("classes"), null, false, EX, iri("mammal"),
        0, 1, 50, false, false, null, null);
    assertEquals(List.of(iri("cat")), ldIds(r.getCollection()));
  }

  @Test
  public void search_nonClassScopeIsNotServedLocally() {
    assertThrows(UnsupportedOperationException.class, () -> service.search("a", List.of("value_sets"),
        List.of(EX), false, null, null, 0, 1, 50, false, false, null, null));
    // "all" would also need value sets, so it is not served locally either.
    assertThrows(UnsupportedOperationException.class, () -> service.search("a", List.of("all"),
        List.of(EX), false, null, null, 0, 1, 50, false, false, null, null));
  }

  @Test
  public void search_multiSourceIsNotServedLocally() {
    assertThrows(UnsupportedOperationException.class, () -> service.search("a", List.of("classes"),
        List.of(EX, "OTHER"), false, null, null, 0, 1, 50, false, false, null, null));
  }

  @Test
  public void findAllClassesInOntology_enumeratesEveryClassOrderedByIri() throws Exception {
    PagedResults<OntologyClass> all = service.findAllClassesInOntology(EX, 1, 50, null);
    assertEquals(Integer.valueOf(6), all.getTotalCount());
    assertEquals(List.of("animal", "cat", "dog", "mammal", "pet", "thing"), ids(all.getCollection()));
  }

  @Test
  public void findAllClassesInOntology_unavailableOntologyThrows() {
    assertThrows(UnsupportedOperationException.class, () -> service.findAllClassesInOntology("NOPE", 1, 50, null));
  }

  /* integratedSearch — the CEE's single call. valueConstraints built via a field-visible mapper. */

  private static final ObjectMapper VC_MAPPER = new ObjectMapper()
      .setVisibility(new ObjectMapper().getVisibilityChecker().withFieldVisibility(JsonAutoDetect.Visibility.ANY));

  private PagedResults<SearchResult> integrated(Optional<String> q, String vcJson) throws Exception {
    return service.integratedSearch(q, VC_MAPPER.readValue(vcJson, ValueConstraints.class), 1, 50, null);
  }

  /* Answering from the index rather than the snapshot: when it happens, and what it must preserve. */

  /** The same six concepts, in an index, so the two paths can be asked the same question. */
  private SearchIndexStore indexOf(String acronym, String versionId) throws Exception {
    SearchIndexStore index = SearchIndexStore.openInMemory();
    index.initSchema();
    index.replaceOntology(acronym, versionId, "2026-08-26T00:00:00Z",
        List.of(new SearchIndexStore.IndexedTerm(acronym, iri("cat"), "Cat", false, null, false, 0),
            new SearchIndexStore.IndexedTerm(acronym, iri("dog"), "Dog", false, null, false, 0)),
        Map.of());
    index.rebuildFullText();
    return index;
  }

  private SqliteTerminologyService routing(SearchIndexStore index, String servedVersion, long above) {
    SqliteTerminologyService.SnapshotProvider provider = new SqliteTerminologyService.SnapshotProvider() {
      @Override
      public Optional<SnapshotStore> forOntology(String ontology) {
        return EX.equals(ontology) ? Optional.of(store) : Optional.empty();
      }

      @Override
      public Optional<VersionTriple> currentVersion(String ontology) {
        return EX.equals(ontology) ? Optional.of(new VersionTriple(servedVersion, null, null))
            : Optional.empty();
      }
    };
    return new SqliteTerminologyService(provider, index, above);
  }

  @Test
  public void anOntologyLargeEnoughIsAnsweredFromTheIndex() throws Exception {
    // The index holds two of the six concepts, so which store answered is visible in the answer.
    try (SearchIndexStore index = indexOf(EX, "v1")) {
      SqliteTerminologyService routed = routing(index, "v1", 1);
      // Mammal is in the snapshot and not in the index, so an empty answer proves the index answered.
      PagedResults<SearchResult> mammal = routed.integratedSearch(Optional.of("Mammal"),
          VC_MAPPER.readValue("{\"ontologies\":[{\"acronym\":\"EX\"}],\"branches\":[],"
              + "\"valueSets\":[],\"classes\":[]}", ValueConstraints.class), 1, 50, null);
      assertEquals(0, mammal.getCollection().size(), "the index has no Mammal, and it answered");
      PagedResults<SearchResult> r = routed.integratedSearch(Optional.of("Cat"),
          VC_MAPPER.readValue("{\"ontologies\":[{\"acronym\":\"EX\"}],\"branches\":[],"
              + "\"valueSets\":[],\"classes\":[]}", ValueConstraints.class), 1, 50, null);
      assertEquals("Cat", r.getCollection().get(0).getPrefLabel());
    }
  }

  @Test
  public void anOntologyBelowTheSizeKeepsItsSnapshot() throws Exception {
    // Below the line the snapshot is quicker, so it must still be the one asked.
    try (SearchIndexStore index = indexOf(EX, "v1")) {
      SqliteTerminologyService routed = routing(index, "v1", 1_000_000);
      PagedResults<SearchResult> r = routed.integratedSearch(Optional.empty(),
          VC_MAPPER.readValue("{\"ontologies\":[{\"acronym\":\"EX\"}],\"branches\":[],"
              + "\"valueSets\":[],\"classes\":[]}", ValueConstraints.class), 1, 50, null);
      assertEquals(Integer.valueOf(6), r.getTotalCount(), "all six, so the snapshot answered");
    }
  }

  @Test
  public void anIndexHoldingAnotherReleaseIsNotAsked() throws Exception {
    // A re-ingest can move the served version before the index catches up. Answering from the older
    // one would attribute terms to a release that did not produce them.
    try (SearchIndexStore index = indexOf(EX, "v1")) {
      SqliteTerminologyService routed = routing(index, "v2", 1);
      PagedResults<SearchResult> r = routed.integratedSearch(Optional.empty(),
          VC_MAPPER.readValue("{\"ontologies\":[{\"acronym\":\"EX\"}],\"branches\":[],"
              + "\"valueSets\":[],\"classes\":[]}", ValueConstraints.class), 1, 50, null);
      assertEquals(Integer.valueOf(6), r.getTotalCount(), "the snapshot answered, not the stale index");
    }
  }

  @Test
  public void aRequestNamingALanguageKeepsTheSnapshot() throws Exception {
    // The index keeps one label a term. Answering a lang request from it would return the default
    // label and say nothing about having done so, which is the multilingual path silently absent.
    try (SearchIndexStore index = indexOf(EX, "v1")) {
      SqliteTerminologyService routed = routing(index, "v1", 1);
      // Mammal is in the snapshot and not in the index, so finding it proves which store answered.
      PagedResults<SearchResult> r = routed.integratedSearch(Optional.of("Mammal"),
          VC_MAPPER.readValue("{\"ontologies\":[{\"acronym\":\"EX\"}],\"branches\":[],"
              + "\"valueSets\":[],\"classes\":[]}", ValueConstraints.class), 1, 50, null, "fr");
      assertEquals(1, r.getCollection().size(), "answered where the languages are");
      assertEquals("Mammal", r.getCollection().get(0).getPrefLabel());
    }
  }

  @Test
  public void aRoutedPageHoldsNoMoreThanItWasAskedFor() throws Exception {
    // The index pages by distinct label and returns every term carrying them, which is more rows
    // than a caller asked for and more than its own page count describes.
    try (SearchIndexStore index = indexOf(EX, "v1")) {
      SqliteTerminologyService routed = routing(index, "v1", 1);
      PagedResults<SearchResult> r = routed.integratedSearch(Optional.of("o"),
          VC_MAPPER.readValue("{\"ontologies\":[{\"acronym\":\"EX\"}],\"branches\":[],"
              + "\"valueSets\":[],\"classes\":[]}", ValueConstraints.class), 1, 1, null);
      assertTrue(r.getCollection().size() <= 1, "a page of one holds at most one");
    }
  }

  @Test
  public void integratedSearch_singleOntologyEmptyTextEnumerates() throws Exception {
    PagedResults<SearchResult> r = integrated(Optional.empty(),
        "{\"ontologies\":[{\"acronym\":\"EX\"}],\"branches\":[],\"valueSets\":[],\"classes\":[]}");
    assertEquals(Integer.valueOf(6), r.getTotalCount());
    assertEquals(6, r.getCollection().size());
  }

  @Test
  public void integratedSearch_singleOntologyWithTextSearches() throws Exception {
    PagedResults<SearchResult> r = integrated(Optional.of("a"), "{\"ontologies\":[{\"acronym\":\"EX\"}]}");
    assertEquals(List.of(iri("cat"), iri("animal"), iri("mammal")), ldIds(r.getCollection()));
  }

  @Test
  public void integratedSearch_singleBranchRestrictsToSubtree() throws Exception {
    // The branch is the root's descendants (cat, dog); the root mammal is excluded. "a" matches Cat.
    PagedResults<SearchResult> r = integrated(Optional.of("a"),
        "{\"branches\":[{\"acronym\":\"EX\",\"uri\":\"" + iri("mammal") + "\"}]}");
    assertEquals(List.of(iri("cat")), ldIds(r.getCollection()));
  }

  @Test
  public void integratedSearch_decodesPercentEncodedBranchUri() throws Exception {
    // Some ontologies (e.g. GDMT) store the branch root percent-encoded; it must be decoded to match.
    String encoded = java.net.URLEncoder.encode(iri("mammal"), java.nio.charset.StandardCharsets.UTF_8);
    PagedResults<SearchResult> r = integrated(Optional.of("a"),
        "{\"branches\":[{\"acronym\":\"EX\",\"uri\":\"" + encoded + "\"}]}");
    assertEquals(List.of(iri("cat")), ldIds(r.getCollection()));
  }

  @Test
  public void integratedSearch_enumeratedClassesFilteredAndSortedByLabel() throws Exception {
    String json = "{\"classes\":["
        + "{\"uri\":\"" + iri("zebra") + "\",\"prefLabel\":\"Zebra\",\"type\":\"OntologyClass\",\"source\":\"EX\"},"
        + "{\"uri\":\"" + iri("ant") + "\",\"prefLabel\":\"Ant\",\"type\":\"OntologyClass\",\"source\":\"EX\"}]}";
    assertEquals(List.of(iri("ant"), iri("zebra")), ldIds(integrated(Optional.empty(), json).getCollection()));
    assertEquals(List.of(iri("ant")), ldIds(integrated(Optional.of("ant"), json).getCollection()));
  }

  @Test
  public void integratedSearch_valueSetEnumeratesChildrenOfTheValueSetClass() throws Exception {
    // A value set's values are the children of the value-set class in its collection snapshot (EX
    // stands in as the collection here). mammal's children are cat and dog, sorted by preferred label.
    PagedResults<SearchResult> r = integrated(Optional.empty(),
        "{\"valueSets\":[{\"vsCollection\":\"EX\",\"uri\":\"" + iri("mammal") + "\"}]}");
    assertEquals(List.of(iri("cat"), iri("dog")), ldIds(r.getCollection()));
    // With a query, values are filtered by preferred-label substring.
    PagedResults<SearchResult> filtered = integrated(Optional.of("Cat"),
        "{\"valueSets\":[{\"vsCollection\":\"EX\",\"uri\":\"" + iri("mammal") + "\"}]}");
    assertEquals(List.of(iri("cat")), ldIds(filtered.getCollection()));
  }

  @Test
  public void integratedSearch_ontologyServedAtPinnedVersion() throws Exception {
    // A provider with two versions of EX: latest has 6 concepts, "v-old" has only mammal. The
    // constraint's version selects which the enumeration serves — reproducible regardless of latest.
    SnapshotStore old = SnapshotStore.openInMemory();
    old.initSchema();
    old.addConcept(iri("mammal"), "Mammal");
    old.materialize();
    try {
      SqliteTerminologyService.SnapshotProvider versioned = new SqliteTerminologyService.SnapshotProvider() {
        @Override
        public Optional<SnapshotStore> forOntology(String o) {
          return EX.equals(o) ? Optional.of(store) : Optional.empty();
        }

        @Override
        public Optional<SnapshotStore> forOntology(String o, String v) {
          if (!EX.equals(o)) {
            return Optional.empty();
          }
          return "v-old".equals(v) ? Optional.of(old) : Optional.of(store);
        }
      };
      SqliteTerminologyService svc = new SqliteTerminologyService(versioned);
      assertEquals(Integer.valueOf(6), svc.integratedSearch(Optional.empty(),
          VC_MAPPER.readValue("{\"ontologies\":[{\"acronym\":\"EX\"}]}", ValueConstraints.class),
          1, 50, null).getTotalCount());
      assertEquals(Integer.valueOf(1), svc.integratedSearch(Optional.empty(),
          VC_MAPPER.readValue("{\"ontologies\":[{\"acronym\":\"EX\",\"version\":\"v-old\"}]}", ValueConstraints.class),
          1, 50, null).getTotalCount());
    } finally {
      old.close();
    }
  }

  @Test
  public void integratedSearch_valueSetFromUnavailableCollectionThrows() {
    // A collection not served locally throws, so the router falls back to BioPortal.
    assertThrows(UnsupportedOperationException.class,
        () -> integrated(Optional.empty(), "{\"valueSets\":[{\"vsCollection\":\"MISSING\",\"uri\":\"x\"}]}"));
  }

  @Test
  public void integratedSearch_multiOntologyNotServedLocally() {
    assertThrows(UnsupportedOperationException.class,
        () -> integrated(Optional.of("x"), "{\"ontologies\":[{\"acronym\":\"EX\"},{\"acronym\":\"OTHER\"}]}"));
  }
}
