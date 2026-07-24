package org.metadatacenter.terms.ingest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

  @Before
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

    store = SnapshotStore.openInMemory();
    store.initSchema();
  }

  @After
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
  }
}
