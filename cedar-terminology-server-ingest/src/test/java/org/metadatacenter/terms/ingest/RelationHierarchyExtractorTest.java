package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the RxNorm-shaped case: an isa hierarchy plus retained compositional relations
 * (has_ingredient) captured as Level-1 typed relations.
 */
public class RelationHierarchyExtractorTest {

  private static final String EX = "http://ex/rx/";
  private static final String PREF_LABEL = "http://www.w3.org/2004/02/skos/core#prefLabel";

  @Test
  public void retainsCompositionalRelationsAlongsideHierarchy() throws Exception {
    IRI isa = IRI.create(EX + "isa");
    IRI hasIngredient = IRI.create(EX + "has_ingredient");
    IRI prefLabel = IRI.create(PREF_LABEL);
    HierarchyConfig cfg = new HierarchyConfig(Set.of(isa), Set.of(), prefLabel, "subsumption", true);

    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(EX + "scheme"));
    OWLAnnotationProperty isaP = df.getOWLAnnotationProperty(isa);
    OWLAnnotationProperty hiP = df.getOWLAnnotationProperty(hasIngredient);
    OWLAnnotationProperty labelP = df.getOWLAnnotationProperty(prefLabel);

    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(isaP, IRI.create(EX + "drug"), IRI.create(EX + "drugClass")));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(hiP, IRI.create(EX + "drug"), IRI.create(EX + "aspirin")));
    for (String c : new String[]{"drug", "drugClass", "aspirin"}) {
      m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(labelP, IRI.create(EX + c), df.getOWLLiteral(c, "en")));
    }

    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new RelationHierarchyExtractor(cfg).extract(o, s);

      // isa gives the hierarchy edge
      assertEquals(List.of(EX + "drug"), s.children(EX + "drugClass"));

      // has_ingredient is retained as a typed relation, queryable both ways
      List<String[]> rels = s.relationsFrom(EX + "drug");
      assertEquals(1, rels.size());
      assertEquals(EX + "has_ingredient", rels.get(0)[0]);
      assertEquals(EX + "aspirin", rels.get(0)[1]);
      assertEquals(List.of(EX + "drug"), s.subjectsWith(EX + "has_ingredient", EX + "aspirin"));
    }
  }
}
