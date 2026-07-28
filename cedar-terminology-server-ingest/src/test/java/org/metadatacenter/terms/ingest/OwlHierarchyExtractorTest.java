package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds a small OWL ontology in memory and verifies extraction:
 *
 * <pre>
 *   animal (root)        pet (root)
 *      |                   |
 *   mammal                 |
 *    /   \                 |
 *  cat   dog --------------+   (dog subClassOf mammal AND pet)
 *
 *  plus:  dog subClassOf (part_of some animal)   <-- anonymous, must be ignored
 * </pre>
 */
public class OwlHierarchyExtractorTest {

  private static final String BASE = "http://ex/";

  private SnapshotStore store;
  private OWLOntology ont;

  private static IRI iri(String s) {
    return IRI.create(BASE + s);
  }

  @BeforeEach
  public void setUp() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    ont = m.createOntology(IRI.create(BASE + "test"));

    OWLClass animal = df.getOWLClass(iri("animal"));
    OWLClass mammal = df.getOWLClass(iri("mammal"));
    OWLClass cat = df.getOWLClass(iri("cat"));
    OWLClass dog = df.getOWLClass(iri("dog"));
    OWLClass pet = df.getOWLClass(iri("pet"));
    OWLObjectProperty partOf = df.getOWLObjectProperty(iri("part_of"));

    m.addAxiom(ont, df.getOWLSubClassOfAxiom(mammal, animal));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(cat, mammal));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(dog, mammal));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(dog, pet));
    // Anonymous superclass — must NOT become a hierarchy edge:
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(dog, df.getOWLObjectSomeValuesFrom(partOf, animal)));
    // Labels:
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), dog.getIRI(), df.getOWLLiteral("Dog")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), cat.getIRI(), df.getOWLLiteral("Cat")));
    // UMLS/TTL style: animal is labeled only with skos:prefLabel (fallback); mammal carries both,
    // and rdfs:label must win.
    OWLAnnotationProperty skosPref =
        df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel"));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(skosPref, animal.getIRI(), df.getOWLLiteral("Animal")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), mammal.getIRI(), df.getOWLLiteral("Mammal")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(skosPref, mammal.getIRI(), df.getOWLLiteral("Mammal SKOS")));

    store = SnapshotStore.openInMemory();
    store.initSchema();
  }

  @AfterEach
  public void tearDown() throws Exception {
    store.close();
  }

  @Test
  public void extract_countsClassesAndNamedEdgesOnly() throws Exception {
    OwlHierarchyExtractor.Result r = new OwlHierarchyExtractor().extract(ont, store);
    assertEquals(5, r.classCount());  // animal, mammal, cat, dog, pet (part_of is a property, not a class)
    assertEquals(4, r.edgeCount());   // the anonymous part_of restriction is excluded
  }

  @Test
  public void hierarchyIsCorrectAfterExtraction() throws Exception {
    new OwlHierarchyExtractor().extract(ont, store);

    assertEquals(List.of(BASE + "animal", BASE + "pet"), store.roots());
    assertEquals(List.of(BASE + "mammal"), store.children(BASE + "animal"));
    assertEquals(List.of(BASE + "cat", BASE + "dog"), store.children(BASE + "mammal"));
    assertEquals(List.of(BASE + "mammal", BASE + "pet"), store.parents(BASE + "dog"));
    assertEquals(List.of(BASE + "cat", BASE + "dog", BASE + "mammal"), store.descendants(BASE + "animal"));
  }

  @Test
  public void anonymousSuperclassDoesNotCreateAConcept() throws Exception {
    new OwlHierarchyExtractor().extract(ont, store);
    // dog's only parents are the two named classes; no synthetic restriction node
    assertEquals(2, store.parents(BASE + "dog").size());
    assertTrue(store.contains(BASE + "dog"));
  }

  @Test
  public void labelsAreCaptured() throws Exception {
    new OwlHierarchyExtractor().extract(ont, store);
    assertEquals("Dog", store.prefLabel(BASE + "dog").orElseThrow());
    assertEquals("Cat", store.prefLabel(BASE + "cat").orElseThrow());
    // skos:prefLabel is used when there is no rdfs:label (UMLS/TTL ontologies label this way);
    assertEquals("Animal", store.prefLabel(BASE + "animal").orElseThrow());
    // but rdfs:label wins when a concept carries both.
    assertEquals("Mammal", store.prefLabel(BASE + "mammal").orElseThrow());
  }

  @Test
  public void genusOfDefinitionBecomesParent() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "defs"));
    OWLClass assay = df.getOWLClass(iri("assay"));
    OWLClass input = df.getOWLClass(iri("input"));
    OWLObjectProperty hasInput = df.getOWLObjectProperty(iri("has_input"));

    // Defined class: mhcAssay ≡ assay AND (has_input some input)  — genus 'assay'.
    OWLClass mhcAssay = df.getOWLClass(iri("mhcAssay"));
    m.addAxiom(o, df.getOWLEquivalentClassesAxiom(mhcAssay,
        df.getOWLObjectIntersectionOf(assay, df.getOWLObjectSomeValuesFrom(hasInput, input))));
    // Genus-differentia via subClassOf-of-intersection: fancyAssay ⊑ assay AND (has_input some input).
    OWLClass fancyAssay = df.getOWLClass(iri("fancyAssay"));
    m.addAxiom(o, df.getOWLSubClassOfAxiom(fancyAssay,
        df.getOWLObjectIntersectionOf(assay, df.getOWLObjectSomeValuesFrom(hasInput, input))));
    // Plain equivalence between two named classes must NOT create a subsumption edge.
    OWLClass synonymA = df.getOWLClass(iri("synonymA"));
    OWLClass synonymB = df.getOWLClass(iri("synonymB"));
    m.addAxiom(o, df.getOWLEquivalentClassesAxiom(synonymA, synonymB));

    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s);
      assertEquals(List.of(BASE + "assay"), s.parents(BASE + "mhcAssay"));
      assertEquals(List.of(BASE + "assay"), s.parents(BASE + "fancyAssay"));
      // The restriction filler 'input' is not a parent, and the class is a descendant of the genus.
      assertFalse(s.parents(BASE + "mhcAssay").contains(BASE + "input"));
      assertTrue(s.descendants(BASE + "assay").contains(BASE + "mhcAssay"));
      // Named-to-named equivalence is not a subsumption edge.
      assertTrue(s.parents(BASE + "synonymA").isEmpty());
      assertTrue(s.parents(BASE + "synonymB").isEmpty());
    }
  }

  @Test
  public void subClassOfOwlThingBecomesARootNotAnEdge() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "thing"));
    OWLClass top = df.getOWLClass(iri("top"));
    OWLClass sub = df.getOWLClass(iri("sub"));
    m.addAxiom(o, df.getOWLSubClassOfAxiom(top, df.getOWLThing())); // explicit top -> root
    m.addAxiom(o, df.getOWLSubClassOfAxiom(sub, top));             // sub under top

    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s);
      // owl:Thing is neither a concept nor an edge; top is the sole root, sub is a descendant.
      assertFalse(s.contains("http://www.w3.org/2002/07/owl#Thing"));
      assertEquals(List.of(BASE + "top"), s.roots());
      assertTrue(s.descendants(BASE + "top").contains(BASE + "sub"));
    }
  }

  @Test
  public void capturesDeprecationAndReplacedBy() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "obs"));
    OWLClass oldTerm = df.getOWLClass(iri("oldterm"));
    OWLClass newTerm = df.getOWLClass(iri("newterm"));
    m.addAxiom(o, df.getOWLDeclarationAxiom(oldTerm));
    m.addAxiom(o, df.getOWLDeclarationAxiom(newTerm));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getOWLDeprecated(), oldTerm.getIRI(), df.getOWLLiteral(true)));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(
        df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0100001")),
        oldTerm.getIRI(), iri("newterm")));

    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s);
      SnapshotStore.ConceptMeta old = s.allConceptMeta().stream()
          .filter(c -> c.iri().equals(BASE + "oldterm")).findFirst().orElseThrow();
      assertTrue(old.obsolete());
      assertEquals(BASE + "newterm", old.replacedBy());
      SnapshotStore.ConceptMeta neu = s.allConceptMeta().stream()
          .filter(c -> c.iri().equals(BASE + "newterm")).findFirst().orElseThrow();
      assertFalse(neu.obsolete());
    }
  }
}
