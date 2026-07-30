package org.metadatacenter.terms.store;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @BeforeEach
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

  @AfterEach
  public void tearDown() throws Exception {
    store.close();
  }

  @Test
  public void roots_areConceptsWithNoParent() throws Exception {
    assertEquals(List.of("thing"), store.roots());
  }

  @Test
  public void pruneDeadEndImportRoots_dropsUnlabeledForeignLeavesButKeepsEntryPoints() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      // The ontology's own labeled root (namespace carries the acronym token).
      s.addConcept("http://ex.org/ONT#Root", "Root");
      // An unresolved-import dangling reference: unlabeled, foreign ID-space, no children.
      s.addConcept("http://purl.obolibrary.org/obo/CHEBI_1", null);
      // An unlabeled foreign class that IS a real entry point — a labeled own class hangs under it.
      s.addConcept("http://purl.obolibrary.org/obo/BFO_1", null);
      s.addConcept("http://ex.org/ONT#Sub", "Sub");
      s.addEdge("http://ex.org/ONT#Sub", "http://purl.obolibrary.org/obo/BFO_1", "rdfs:subClassOf");
      s.materialize();

      // All three parentless classes are roots before pruning.
      assertTrue(s.roots().contains("http://purl.obolibrary.org/obo/CHEBI_1"));
      assertEquals(3, s.roots().size());

      int pruned = s.pruneDeadEndImportRoots("ONT");

      assertEquals(1, pruned, "only the dead-end CHEBI reference is pruned");
      List<String> roots = s.roots();
      assertFalse(roots.contains("http://purl.obolibrary.org/obo/CHEBI_1"), "dead-end import ref dropped");
      assertTrue(roots.contains("http://ex.org/ONT#Root"), "labeled own root kept");
      assertTrue(roots.contains("http://purl.obolibrary.org/obo/BFO_1"), "entry point that leads to labeled content kept");
      // The concept itself survives — only the root entry was removed.
      assertTrue(s.contains("http://purl.obolibrary.org/obo/CHEBI_1"));
    }
  }

  @Test
  public void pruneDeadEndImportRoots_neverEmptiesAnOntology() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      // A label-less but structured ontology: every class is unlabeled and foreign, so every root
      // would otherwise be pruned. The guard must keep them so the tree stays browsable.
      s.addConcept("http://purl.obolibrary.org/obo/CHEBI_1", null);
      s.addConcept("http://purl.obolibrary.org/obo/CHEBI_2", null);
      s.addEdge("http://purl.obolibrary.org/obo/CHEBI_2", "http://purl.obolibrary.org/obo/CHEBI_1", "rdfs:subClassOf");
      s.materialize();
      assertEquals(1, s.roots().size()); // CHEBI_1 is the only root

      int pruned = s.pruneDeadEndImportRoots("ONT");

      assertEquals(0, pruned, "guard prevents pruning the ontology to zero roots");
      assertEquals(1, s.roots().size(), "the sole root is preserved");
    }
  }

  @Test
  public void fillMissingLabelsFromIri_usesVerbatimFragmentForUnlabeledConcepts() throws Exception {
    assertEquals("Alcoholic_Hallucinosis",
        SnapshotStore.labelFromIri("http://ontology.apa.org/apaonto/x.owl#Alcoholic_Hallucinosis"));
    assertEquals("3DRadiotherapyPlanning", SnapshotStore.labelFromIri("http://www.ifomis.org/acgt/1.0#3DRadiotherapyPlanning"));
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("http://x.org/o#Alcoholic_Hallucinosis", null);  // unlabeled
      s.addConcept("http://x.org/o#Hypersomnia", "");               // empty label
      s.addConcept("http://x.org/o#Real", "Real Label");            // labeled — must be left alone

      int n = s.fillMissingLabelsFromIri();

      assertEquals(2, n);
      assertEquals("Alcoholic_Hallucinosis", s.prefLabel("http://x.org/o#Alcoholic_Hallucinosis").orElseThrow());
      assertEquals("Hypersomnia", s.prefLabel("http://x.org/o#Hypersomnia").orElseThrow());
      assertEquals("Real Label", s.prefLabel("http://x.org/o#Real").orElseThrow(), "existing label untouched");
    }
  }

  @Test
  public void pruneDeadEndImportRoots_ownNamespaceIsNotTheDominantImport() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      // An import-heavy ontology (acronym matches no namespace): the dominant ID-space is an import
      // (GO), the ontology's own namespace is a minority. own-namespace detection must fall back to
      // the dominant NON-IMPORT space (the own one), not the raw dominant, so the own root is kept
      // and only the foreign dead-ends are pruned.
      for (int i = 1; i <= 5; i++) {
        s.addConcept("http://purl.obolibrary.org/obo/GO_000000" + i, null); // 5 foreign dead-ends
      }
      s.addConcept("http://example.org/myont#Top", null); // 1 own root (unlabeled, but OWN)
      s.materialize();
      assertEquals(6, s.roots().size());

      int pruned = s.pruneDeadEndImportRoots("MYONT"); // token "MYONT" matches no namespace -> fallback

      assertEquals(5, pruned, "the 5 imported GO dead-ends are pruned");
      assertEquals(java.util.List.of("http://example.org/myont#Top"), s.roots(),
          "the own-namespace root is kept even though it is the minority and unlabeled");
    }
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

  /* Label search — the primitive behind the picker's class search and type-ahead. */

  private static List<String> iris(List<SnapshotStore.Concept> concepts) {
    List<String> out = new ArrayList<>();
    for (SnapshotStore.Concept c : concepts) {
      out.add(c.iri());
    }
    return out;
  }

  @Test
  public void searchByLabel_containsMatchIsCaseInsensitiveAndShortestFirst() throws Exception {
    // Labels containing "a": Animal, Mammal, Cat -> ordered shortest-label first (Cat), then by label.
    assertEquals(List.of("cat", "animal", "mammal"), iris(store.searchByLabel("a", false, 0)));
    assertEquals(List.of("cat", "animal", "mammal"), iris(store.searchByLabel("A", false, 0)));
  }

  @Test
  public void searchByLabel_prefixOnlyAnchorsAtLabelStart() throws Exception {
    assertEquals(List.of("mammal"), iris(store.searchByLabel("mam", true, 0)));
    // A mid-label substring does not match in prefix mode.
    assertEquals(List.of(), iris(store.searchByLabel("ammal", true, 0)));
    // ...but does in contains mode.
    assertTrue(iris(store.searchByLabel("ammal", false, 0)).contains("mammal"));
  }

  @Test
  public void searchByLabel_respectsLimit() throws Exception {
    assertEquals(1, store.searchByLabel("a", false, 1).size());
  }

  @Test
  public void searchByLabel_escapesLikeMetacharacters() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("p1", "50% off");
      s.addConcept("p2", "50 off");
      s.materialize();
      // "%" is matched literally, not as a wildcard, so "50 off" is not a hit.
      assertEquals(List.of("p1"), iris(s.searchByLabel("50%", false, 0)));
    }
  }

  @Test
  public void searchByLabelUnderRoot_restrictsToTheBranch() throws Exception {
    // Under mammal (descendants cat, dog — the root mammal is excluded); labels containing "a": Cat.
    assertEquals(List.of("cat"), iris(store.searchByLabelUnderRoot("mammal", "a", false, 0)));
    // "pet" is outside the mammal branch.
    assertEquals(List.of(), iris(store.searchByLabelUnderRoot("mammal", "pet", false, 0)));
  }

  @Test
  public void branchExcludesRootEvenWithACycle() throws Exception {
    // A broader/narrower cycle (some SKOS vocabularies have them) makes a node reach itself through
    // the closure; a branch must still never return its own root.
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("a", "A");
      s.addConcept("b", "B");
      s.addEdge("b", "a", "hierarchy");   // b under a
      s.addEdge("a", "b", "hierarchy");   // cycle: a under b
      s.materialize();
      assertEquals(List.of("b"), iris(s.searchByLabelUnderRoot("a", "", false, 0)));
    }
  }

  @Test
  public void browseIncludesUnlabeledDescendants_butSearchDoesNot() throws Exception {
    // Some ontologies carry a correct hierarchy but no labels (e.g. GALEN). An empty-query browse
    // must still enumerate label-less descendants; a real search term must not match them.
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("root", "Root");
      s.addConcept("labeled", "Labeled child");
      s.addConcept("unlabeled", null);           // no label
      s.addEdge("labeled", "root", "rdfs:subClassOf");
      s.addEdge("unlabeled", "root", "rdfs:subClassOf");
      s.materialize();

      List<String> browse = iris(s.searchByLabelUnderRoot("root", "", false, 0));
      assertEquals(2, browse.size());
      assertTrue(browse.contains("labeled") && browse.contains("unlabeled"));
      // A real term matches only the labeled child; the label-less one cannot match.
      assertEquals(List.of("labeled"), iris(s.searchByLabelUnderRoot("root", "Labeled", false, 0)));
    }
  }

  @Test
  public void allConceptsDetailed_returnsEveryConceptOrderedByIri() throws Exception {
    assertEquals(List.of("animal", "cat", "dog", "mammal", "pet", "thing"),
        iris(store.allConceptsDetailed()));
  }

  /* Roots — BioPortal's rule: a non-obsolete class with no named parent. Verified against the roots
   * goldens: the parentless set reproduces BioPortal's /classes/roots (e.g. DOID's 15). owl:Thing is
   * never materialized, so a class asserting subClassOf owl:Thing is simply parentless and is a root;
   * an explicit owl:Thing declaration does not distinguish roots. */

  @Test
  public void roots_areNonObsoleteParentlessClasses() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("top", "Top");                     // parentless -> root
      s.addConcept("obsTop", "ObsTop", true, null);   // parentless but obsolete -> not a root
      s.addConcept("alsoTop", "AlsoTop");             // parentless -> also a root
      s.addConcept("child", "Child");
      s.addEdge("child", "top", "rdfs:subClassOf");
      s.materialize();
      assertEquals(List.of("alsoTop", "top"), s.roots());   // both parentless non-obsolete; obsTop excluded
      assertTrue(s.descendants("top").contains("child"));
    }
  }

  @Test
  public void roots_areParentlessRegardlessOfEdgePredicate() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("a", "A");
      s.addConcept("b", "B");
      s.addEdge("b", "a", "isa"); // a is parentless -> root; b has a parent -> not
      s.materialize();
      assertEquals(List.of("a"), s.roots());
    }
  }
}
