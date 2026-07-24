package org.metadatacenter.terms;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
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
}
