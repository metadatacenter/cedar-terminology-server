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
  public void childrenByLabel_ordersBeforeItLimits() throws Exception {
    // A parent with more children than a caller will ask for. The IRIs run counter to the labels, so
    // a query that limited by IRI order and left the caller to sort what came back would return the
    // wrong set: ABD's "Disease" has 280 children, and the 50 that came back skipped "African horse
    // sickness" while showing "African swine fever" two rows on, which reads as a term the release
    // does not have.
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("http://ex/parent", "Parent");
      for (int i = 0; i < 30; i++) {
        String iri = String.format("http://ex/c%02d", i);
        s.addConcept(iri, String.format("term %02d", 29 - i));
        s.addEdge(iri, "http://ex/parent", "rdfs:subClassOf");
      }
      s.materialize();

      List<SnapshotStore.LabelledConcept> first = s.childrenByLabel("http://ex/parent", 3);
      assertEquals(List.of("term 00", "term 01", "term 02"),
          first.stream().map(SnapshotStore.LabelledConcept::prefLabel).toList());
      // The count is of every child, not of the ones handed over, so a caller can say what it is
      // holding back rather than presenting a subset as the whole.
      assertEquals(30, s.childCount("http://ex/parent"));
      assertEquals(30, s.childrenByLabel("http://ex/parent", 100).size());
    }
  }

  @Test
  public void roots_areConceptsWithNoParent() throws Exception {
    assertEquals(List.of("thing"), store.roots());
  }

  @Test
  public void normalizedContentHash_ignoresIngestOrderAndInternalIds() throws Exception {
    // Same content ingested in a different order (different internal row ids) hashes identically:
    // the canonical form is over IRIs, not row ids.
    String h1, h2;
    try (SnapshotStore a = SnapshotStore.openInMemory()) {
      a.initSchema();
      a.addConcept("http://ex/z", "Zed");
      a.addConcept("http://ex/a", "Ay");
      a.addEdge("http://ex/z", "http://ex/a", "rdfs:subClassOf");
      a.materialize();
      h1 = a.normalizedContentHash(true);
    }
    try (SnapshotStore b = SnapshotStore.openInMemory()) {
      b.initSchema();
      b.addConcept("http://ex/a", "Ay"); // reverse insert order
      b.addConcept("http://ex/z", "Zed");
      b.addEdge("http://ex/z", "http://ex/a", "rdfs:subClassOf");
      b.materialize();
      h2 = b.normalizedContentHash(true);
    }
    assertEquals(h1, h2);
  }

  @Test
  public void normalizedContentHash_labelChangeMovesFullButNotStructure() throws Exception {
    String structBefore, structAfter, fullBefore, fullAfter;
    try (SnapshotStore a = SnapshotStore.openInMemory()) {
      a.initSchema();
      a.addConcept("http://ex/c", "cancer");
      a.materialize();
      structBefore = a.normalizedContentHash(false);
      fullBefore = a.normalizedContentHash(true);
    }
    try (SnapshotStore b = SnapshotStore.openInMemory()) {
      b.initSchema();
      b.addConcept("http://ex/c", "Cancer"); // only the label differs
      b.materialize();
      structAfter = b.normalizedContentHash(false);
      fullAfter = b.normalizedContentHash(true);
    }
    assertEquals(structBefore, structAfter, "structure-only hash ignores a label change");
    assertFalse(fullBefore.equals(fullAfter), "full hash moves when a label changes");
  }

  @Test
  public void normalizedContentHash_structureChangeMovesBoth() throws Exception {
    String s1, s2;
    try (SnapshotStore a = SnapshotStore.openInMemory()) {
      a.initSchema();
      a.addConcept("http://ex/p", "P");
      a.addConcept("http://ex/c", "C");
      a.materialize();
      s1 = a.normalizedContentHash(false);
    }
    try (SnapshotStore b = SnapshotStore.openInMemory()) {
      b.initSchema();
      b.addConcept("http://ex/p", "P");
      b.addConcept("http://ex/c", "C");
      b.addEdge("http://ex/c", "http://ex/p", "rdfs:subClassOf"); // an added edge is real content
      b.materialize();
      s2 = b.normalizedContentHash(false);
    }
    assertFalse(s1.equals(s2), "adding a subsumption edge changes the structure hash");
  }

  @Test
  public void dominantOwnIdspace_picksOwnSpaceOverBulkImport() throws Exception {
    // OBI-like: mostly imported CHEBI_ concepts, a minority of its own OBI_ terms. Acronym-keying
    // must resolve the own space, not the more frequent import — this is the canonical-iri source.
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      for (int i = 0; i < 20; i++) {
        s.addConcept("http://purl.obolibrary.org/obo/CHEBI_" + i, "chebi " + i); // bulk import (majority)
      }
      s.addConcept("http://purl.obolibrary.org/obo/OBI_1", "assay");
      s.addConcept("http://purl.obolibrary.org/obo/OBI_2", "material");
      s.materialize();

      assertEquals("http://purl.obolibrary.org/obo/OBI_", s.dominantOwnIdspace("OBI").orElseThrow());
    }
  }

  @Test
  public void dominantOwnIdspace_nonOboNamespace() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("http://purl.bioontology.org/ontology/MESH/D000001", "Calcimycin");
      s.addConcept("http://purl.bioontology.org/ontology/MESH/D000002", "Temefos");
      s.materialize();

      assertEquals("http://purl.bioontology.org/ontology/MESH/", s.dominantOwnIdspace("MESH").orElseThrow());
    }
  }

  @Test
  public void dominantOwnIdspace_emptySnapshotIsEmpty() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.materialize();
      assertTrue(s.dominantOwnIdspace("ANY").isEmpty());
    }
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

  @Test
  public void labelTable_roundTripsAndKeysOnConcept() throws Exception {
    store.addLabels(List.of(
        new SnapshotStore.LabelRow("dog", "rdfs:label", "en", "Dog"),
        new SnapshotStore.LabelRow("dog", "rdfs:label", "fr", "Chien"),
        new SnapshotStore.LabelRow("dog", "skos:altLabel", "", "canine"),
        new SnapshotStore.LabelRow("dog", "rdfs:label", "en", "Dog"),   // duplicate: dropped by PK
        new SnapshotStore.LabelRow("no-such-iri", "rdfs:label", "en", "X"))); // no concept: ignored

    List<SnapshotStore.LabelEntry> dog = store.labels("dog");
    assertEquals(3, dog.size(), "duplicate dropped, foreign-concept row ignored");
    assertTrue(dog.contains(new SnapshotStore.LabelEntry("rdfs:label", "en", "Dog")));
    assertTrue(dog.contains(new SnapshotStore.LabelEntry("rdfs:label", "fr", "Chien")));
    assertTrue(dog.contains(new SnapshotStore.LabelEntry("skos:altLabel", "", "canine")));
    assertEquals(3, store.labelCount());
    assertEquals(3, store.allLabels().size());
  }

  @Test
  public void labels_doNotParticipateInContentIdentity() throws Exception {
    // The label table is out of identity by construction: adding names must move neither the
    // structure-only nor the label-sensitive content hash.
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.addConcept("http://ex/c", "cancer");
      s.materialize();
      String structBefore = s.normalizedContentHash(false);
      String fullBefore = s.normalizedContentHash(true);
      s.addLabels(List.of(
          new SnapshotStore.LabelRow("http://ex/c", "rdfs:label", "en", "cancer"),
          new SnapshotStore.LabelRow("http://ex/c", "rdfs:label", "de", "Krebs"),
          new SnapshotStore.LabelRow("http://ex/c", "oboInOwl:hasExactSynonym", "en", "malignant neoplasm")));
      assertEquals(structBefore, s.normalizedContentHash(false), "labels do not affect structure hash");
      assertEquals(fullBefore, s.normalizedContentHash(true), "labels do not affect the label-sensitive hash");
    }
  }

  @Test
  public void synonyms_returnsOnlySynonymScopesDistinctAndOrdered() throws Exception {
    store.addLabels(List.of(
        new SnapshotStore.LabelRow("dog", "rdfs:label", "en", "Dog"),                 // label proper — excluded
        new SnapshotStore.LabelRow("dog", "skos:prefLabel", "en", "Dog"),             // excluded
        new SnapshotStore.LabelRow("dog", "skos:altLabel", "en", "canine"),
        new SnapshotStore.LabelRow("dog", "oboInOwl:hasExactSynonym", "en", "domestic dog"),
        new SnapshotStore.LabelRow("dog", "oboInOwl:hasExactSynonym", "fr", "chien domestique"),
        new SnapshotStore.LabelRow("dog", "skos:altLabel", "en", "canine")));         // duplicate value
    assertEquals(List.of("canine", "chien domestique", "domestic dog"), store.synonyms("dog"));
    assertTrue(store.synonyms("cat").isEmpty());
  }

  @Test
  public void searchByLabel_matchesAnyLanguageAndSynonym() throws Exception {
    store.addLabels(List.of(
        new SnapshotStore.LabelRow("dog", "rdfs:label", "fr", "chien"),               // French label
        new SnapshotStore.LabelRow("dog", "oboInOwl:hasExactSynonym", "en", "canine"))); // synonym
    assertTrue(matches("Dog"), "served pref_label still matches");
    assertTrue(matches("chien"), "a non-English label matches");
    assertTrue(matches("canine"), "a synonym matches");
    assertEquals(1, store.searchByLabel("chien", false, 0).size(), "one concept, not duplicated");
    assertTrue(store.searchByLabel("no-such-term", false, 0).isEmpty());
  }

  private boolean matches(String q) throws Exception {
    return store.searchByLabel(q, false, 0).stream().anyMatch(c -> c.iri().equals("dog"));
  }

  @Test
  public void labelInLang_selectsLanguageWithExactnessAndPropertyPreference() throws Exception {
    store.addLabels(List.of(
        new SnapshotStore.LabelRow("dog", "rdfs:label", "en", "Dog"),
        new SnapshotStore.LabelRow("dog", "rdfs:label", "fr", "Chien"),
        new SnapshotStore.LabelRow("dog", "skos:prefLabel", "fr", "chien (skos)"),
        new SnapshotStore.LabelRow("dog", "rdfs:label", "fr-CA", "Chien quebecois"),
        new SnapshotStore.LabelRow("cat", "rdfs:label", "fr-CA", "Chat")));
    assertEquals("Chien", store.labelInLang("dog", "fr").orElseThrow());        // exact fr, rdfs over skos
    assertEquals("Chien quebecois", store.labelInLang("dog", "fr-CA").orElseThrow());
    assertEquals("Dog", store.labelInLang("dog", "en").orElseThrow());
    assertEquals("Chat", store.labelInLang("cat", "fr").orElseThrow());         // fr matches the fr-CA variant
    assertTrue(store.labelInLang("dog", "de").isEmpty());                        // absent language -> empty (fallback)
    assertTrue(store.labelInLang("dog", null).isEmpty());
  }
}
