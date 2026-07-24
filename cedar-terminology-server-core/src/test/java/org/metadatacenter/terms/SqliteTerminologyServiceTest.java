package org.metadatacenter.terms;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.store.SnapshotStore;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

  @Before
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

  @After
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
    // Within the mammal branch (mammal + cat + dog), labels containing "a": Cat, Mammal.
    PagedResults<SearchResult> r = service.search("a", List.of("classes"), null, false, EX, iri("mammal"),
        0, 1, 50, false, false, null, null);
    assertEquals(List.of(iri("cat"), iri("mammal")), ldIds(r.getCollection()));
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
}
