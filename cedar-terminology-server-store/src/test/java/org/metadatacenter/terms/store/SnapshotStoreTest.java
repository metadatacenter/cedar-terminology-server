package org.metadatacenter.terms.store;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the snapshot store against a small synthetic DAG:
 *
 * <pre>
 *            thing
 *           /     \
 *      animal      pet
 *        |          |
 *      mammal       |
 *       /   \       |
 *      cat   dog ---+   (dog has two parents: mammal and pet)
 * </pre>
 */
public class SnapshotStoreTest {

  private SnapshotStore store;

  @Before
  public void setUp() throws Exception {
    store = SnapshotStore.openInMemory();
    store.initSchema();

    for (String[] c : new String[][]{
        {"thing", "Thing"}, {"animal", "Animal"}, {"mammal", "Mammal"},
        {"cat", "Cat"}, {"dog", "Dog"}, {"pet", "Pet"}}) {
      store.addConcept(c[0], c[1]);
    }

    store.addEdge("animal", "thing", "rdfs:subClassOf");
    store.addEdge("mammal", "animal", "rdfs:subClassOf");
    store.addEdge("cat", "mammal", "rdfs:subClassOf");
    store.addEdge("dog", "mammal", "rdfs:subClassOf");
    store.addEdge("pet", "thing", "rdfs:subClassOf");
    store.addEdge("dog", "pet", "rdfs:subClassOf");

    store.materialize();
  }

  @After
  public void tearDown() throws Exception {
    store.close();
  }

  @Test
  public void roots_areConceptsWithNoParent() throws Exception {
    assertEquals(List.of("thing"), store.roots());
  }

  @Test
  public void children_areDirectSubclassesOnly() throws Exception {
    assertEquals(List.of("animal", "pet"), store.children("thing"));
    assertEquals(List.of("cat", "dog"), store.children("mammal"));
    assertEquals(List.of(), store.children("cat"));
  }

  @Test
  public void parents_includeMultipleInheritance() throws Exception {
    assertEquals(List.of("mammal", "pet"), store.parents("dog"));
    assertEquals(List.of("thing"), store.parents("animal"));
  }

  @Test
  public void descendants_areTransitive() throws Exception {
    assertEquals(List.of("animal", "cat", "dog", "mammal", "pet"), store.descendants("thing"));
    assertEquals(List.of("cat", "dog"), store.descendants("mammal"));
    assertEquals(List.of(), store.descendants("cat"));
  }

  @Test
  public void ancestors_areTransitiveAcrossBothParents() throws Exception {
    // dog reaches thing via both mammal->animal and pet
    assertEquals(List.of("animal", "mammal", "pet", "thing"), store.ancestors("dog"));
  }

  @Test
  public void subsumes_reflectsClosure() throws Exception {
    assertTrue(store.subsumes("thing", "dog"));
    assertTrue(store.subsumes("animal", "dog"));
    assertTrue(store.subsumes("pet", "dog"));
    assertFalse(store.subsumes("pet", "cat"));
    assertFalse(store.subsumes("dog", "mammal")); // not an ancestor
  }

  @Test
  public void contains_andPrefLabel() throws Exception {
    assertTrue(store.contains("dog"));
    assertFalse(store.contains("unicorn"));
    assertEquals("Dog", store.prefLabel("dog").orElseThrow());
    assertTrue(store.prefLabel("unicorn").isEmpty());
  }

  @Test
  public void materialize_terminatesOnCycle() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("a", "A");
      s.addConcept("b", "B");
      s.addEdge("a", "b", "isa");
      s.addEdge("b", "a", "isa"); // cycle: a <-> b
      s.materialize();            // must terminate rather than loop forever
      assertTrue(s.subsumes("a", "b"));
      assertTrue(s.subsumes("b", "a"));
      // a cyclic node is its own ancestor, which is how callers can detect the cycle
      assertTrue(s.ancestors("a").contains("a"));
    }
  }

  @Test
  public void emptySnapshotIsUsable() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.materialize();
      assertTrue(s.roots().isEmpty());
      assertTrue(s.children("anything").isEmpty());
      assertFalse(s.contains("anything"));
    }
  }

  @Test
  public void relations_forwardAndReverseLookups() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("drug", "Drug");
      s.addConcept("aspirin", "Aspirin");
      s.addRelation("drug", "has_ingredient", "aspirin");
      s.addRelation("drug", "has_ingredient", "ghost"); // object not a concept -> ignored
      List<String[]> from = s.relationsFrom("drug");
      assertEquals(1, from.size());
      assertEquals("has_ingredient", from.get(0)[0]);
      assertEquals("aspirin", from.get(0)[1]);
      assertEquals(List.of("drug"), s.subjectsWith("has_ingredient", "aspirin"));
    }
  }

  @Test
  public void addRelationsBatchIgnoresNonConcepts() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("a", "A");
      s.addConcept("b", "B");
      s.addRelations(List.of(new String[]{"a", "rel", "b"}, new String[]{"a", "rel", "missing"}));
      assertEquals(List.of("a"), s.subjectsWith("rel", "b"));
      assertTrue(s.subjectsWith("rel", "missing").isEmpty());
    }
  }
}
