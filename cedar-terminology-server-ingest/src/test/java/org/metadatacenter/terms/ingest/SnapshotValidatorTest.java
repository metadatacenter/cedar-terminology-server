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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SnapshotValidatorTest {

  private static final String BASE = "http://ex/";

  private OWLOntology ont;
  private SnapshotStore store;

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
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(dog, df.getOWLObjectSomeValuesFrom(partOf, animal)));

    store = SnapshotStore.openInMemory();
    store.initSchema();
    new OwlHierarchyExtractor().extract(ont, store);
  }

  @After
  public void tearDown() throws Exception {
    store.close();
  }

  @Test
  public void correctlyExtractedSnapshotIsValid() throws Exception {
    SnapshotValidator.Report r = new SnapshotValidator().validate(ont, store);
    assertTrue(r.summary(), r.isValid());
    assertEquals(r.recomputedClosurePairs(), r.storeClosurePairs());
    assertTrue(r.cycles().isEmpty());
  }

  @Test
  public void detectsEdgeAndClosureDrift() throws Exception {
    // Add an edge the OWL does not have, and do NOT re-materialize the closure.
    store.addEdge(BASE + "cat", BASE + "pet", "rdfs:subClassOf");

    SnapshotValidator.Report r = new SnapshotValidator().validate(ont, store);
    assertFalse(r.isValid());
    // The extra edge is flagged against the ontology...
    assertTrue(r.edgesExtraInStore().contains(BASE + "cat" + "\t" + BASE + "pet"));
    // ...and its transitive consequence is missing from the stale stored closure.
    assertTrue(r.closureMissingFromStore().contains(BASE + "pet" + "\t" + BASE + "cat"));
  }
}
