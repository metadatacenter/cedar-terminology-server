package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds a small SKOS vocabulary in memory (skos:broader/narrower as annotation assertions with
 * IRI values, the way real SKOS files present it) and verifies the extractor's hierarchy, labels,
 * and obsolescence handling.
 *
 * <pre>
 *        root        echo   (roots)
 *         |
 *         B
 *       / | \
 *      A  C  D
 *  A: deprecated, replaced by C.  D reached via B skos:narrower D.
 * </pre>
 */
public class SkosHierarchyExtractorTest {

  private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
  private static final String DCT = "http://purl.org/dc/terms/";
  private static final String BASE = "http://ex/skos/";

  private SnapshotStore store;
  private OWLOntology ont;

  private static IRI c(String s) {
    return IRI.create(BASE + s);
  }

  @BeforeEach
  public void setUp() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    ont = m.createOntology(IRI.create(BASE + "scheme"));

    OWLAnnotationProperty broader = df.getOWLAnnotationProperty(IRI.create(SKOS + "broader"));
    OWLAnnotationProperty narrower = df.getOWLAnnotationProperty(IRI.create(SKOS + "narrower"));
    OWLAnnotationProperty prefLabel = df.getOWLAnnotationProperty(IRI.create(SKOS + "prefLabel"));
    OWLAnnotationProperty isReplacedBy = df.getOWLAnnotationProperty(IRI.create(DCT + "isReplacedBy"));

    // Hierarchy via broader (child -> parent) and one narrower (parent -> child).
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(broader, c("A"), c("B")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(broader, c("C"), c("B")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(broader, c("B"), c("root")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(narrower, c("B"), c("D"))); // D is a child of B

    // Labels (English preferred over other languages).
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(prefLabel, c("A"), df.getOWLLiteral("Alpha", "en")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(prefLabel, c("A"), df.getOWLLiteral("Alfa", "de")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(prefLabel, c("B"), df.getOWLLiteral("Beta", "en")));

    // A is obsolete, replaced by C.
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(df.getOWLDeprecated(), c("A"), df.getOWLLiteral(true)));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(isReplacedBy, c("A"), c("C")));

    // A concept typed skos:Concept with no broader/narrower (a standalone root).
    m.addAxiom(ont, df.getOWLClassAssertionAxiom(
        df.getOWLClass(IRI.create(SKOS + "Concept")), df.getOWLNamedIndividual(c("echo"))));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(prefLabel, c("echo"), df.getOWLLiteral("Echo", "en")));

    store = SnapshotStore.openInMemory();
    store.initSchema();
    new SkosHierarchyExtractor().extract(ont, store);
  }

  @AfterEach
  public void tearDown() throws Exception {
    store.close();
  }

  @Test
  public void broaderAndNarrowerBecomeChildToParentEdges() throws Exception {
    // children of B are A, C (broader) and D (narrower inverse)
    assertEquals(List.of(BASE + "A", BASE + "C", BASE + "D"), store.children(BASE + "B"));
    assertEquals(List.of(BASE + "B"), store.parents(BASE + "A"));
  }

  @Test
  public void rootsAreConceptsWithNoBroader() throws Exception {
    // "root" (top of the broader chain) and "echo" (typed skos:Concept, no broader)
    assertEquals(List.of(BASE + "echo", BASE + "root"), store.roots());
  }

  @Test
  public void descendantsAreTransitive() throws Exception {
    assertEquals(List.of(BASE + "A", BASE + "B", BASE + "C", BASE + "D"), store.descendants(BASE + "root"));
  }

  @Test
  public void prefersEnglishPrefLabel() throws Exception {
    assertEquals("Alpha", store.prefLabel(BASE + "A").orElseThrow());
    assertEquals("Echo", store.prefLabel(BASE + "echo").orElseThrow());
  }

  @Test
  public void capturesObsoleteAndReplacedBy() throws Exception {
    SnapshotStore.ConceptMeta a = store.allConceptMeta().stream()
        .filter(x -> x.iri().equals(BASE + "A")).findFirst().orElseThrow();
    assertTrue(a.obsolete());
    assertEquals(BASE + "C", a.replacedBy());
  }
}
