package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;

/**
 * Extracts a subsumption hierarchy from an OWL (or OBO-in-OWL) ontology into a {@link SnapshotStore}.
 *
 * The extraction rule is deliberately narrow, matching how a terminology server renders an OWL
 * tree: a hierarchy edge is an {@code rdfs:subClassOf} axiom between two <em>named</em> classes.
 * Anonymous superclasses (restrictions such as OBO {@code part_of some X}, intersections, unions)
 * are skipped, so compositional relations do not pollute the {@code is_a} tree. {@code owl:Thing}
 * and {@code owl:Nothing} are not materialized, so classes with no named superclass become roots.
 *
 * Only asserted subsumption is used; no reasoner is invoked. Concept labels come from
 * {@code rdfs:label}. Obsolete flags and richer annotations (definitions, synonyms) are not yet
 * captured; see the hierarchy-extractor design note.
 */
public class OwlHierarchyExtractor {

  private static final Logger log = LoggerFactory.getLogger(OwlHierarchyExtractor.class);

  /** Counts produced by an extraction run. */
  public record Result(int classCount, int edgeCount) {}

  /**
   * Extracts the hierarchy from an already-loaded ontology into the store and materializes the
   * closure. The store should be freshly initialized ({@link SnapshotStore#initSchema()}).
   */
  /** OBO "term replaced by": links an obsolete term to its successor. */
  private static final IRI TERM_REPLACED_BY = IRI.create("http://purl.obolibrary.org/obo/IAO_0100001");

  public Result extract(OWLOntology ont, SnapshotStore store) throws SQLException {
    OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
    OWLAnnotationProperty rdfsLabel = df.getRDFSLabel();
    OWLAnnotationProperty deprecated = df.getOWLDeprecated();
    OWLAnnotationProperty replacedBy = df.getOWLAnnotationProperty(TERM_REPLACED_BY);

    int classCount = 0;
    for (OWLClass cls : ont.getClassesInSignature()) {
      if (cls.isOWLThing() || cls.isOWLNothing()) {
        continue;
      }
      store.addConcept(cls.getIRI().toString(), label(cls, ont, rdfsLabel),
          isDeprecated(cls, ont, deprecated), replacedBy(cls, ont, replacedBy));
      classCount++;
    }

    int edgeCount = 0;
    for (OWLSubClassOfAxiom ax : ont.getAxioms(AxiomType.SUBCLASS_OF)) {
      OWLClassExpression sub = ax.getSubClass();
      OWLClassExpression sup = ax.getSuperClass();
      if (sub.isAnonymous() || sup.isAnonymous()) {
        continue; // named-superclass edges only
      }
      OWLClass child = sub.asOWLClass();
      OWLClass parent = sup.asOWLClass();
      if (child.isOWLThing() || child.isOWLNothing() || parent.isOWLThing() || parent.isOWLNothing()) {
        continue;
      }
      store.addEdge(child.getIRI().toString(), parent.getIRI().toString(), "rdfs:subClassOf");
      edgeCount++;
    }

    store.materialize();
    log.info("Extracted {} classes and {} subClassOf edges", classCount, edgeCount);
    return new Result(classCount, edgeCount);
  }

  /** Loads an ontology from a file and extracts it into the store. */
  public Result extractFromFile(File file, SnapshotStore store)
      throws OWLOntologyCreationException, SQLException {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLOntology ont = manager.loadOntologyFromOntologyDocument(file);
    return extract(ont, store);
  }

  private static String label(OWLClass cls, OWLOntology ont, OWLAnnotationProperty rdfsLabel) {
    for (OWLAnnotation a : EntitySearcher.getAnnotations(cls, ont, rdfsLabel)) {
      if (a.getValue() instanceof OWLLiteral literal) {
        return literal.getLiteral();
      }
    }
    return null;
  }

  private static boolean isDeprecated(OWLClass cls, OWLOntology ont, OWLAnnotationProperty deprecated) {
    for (OWLAnnotation a : EntitySearcher.getAnnotations(cls, ont, deprecated)) {
      if (a.getValue() instanceof OWLLiteral literal && literal.isBoolean() && literal.parseBoolean()) {
        return true;
      }
    }
    return false;
  }

  private static String replacedBy(OWLClass cls, OWLOntology ont, OWLAnnotationProperty replacedBy) {
    for (OWLAnnotation a : EntitySearcher.getAnnotations(cls, ont, replacedBy)) {
      OWLAnnotationValue value = a.getValue();
      if (value instanceof IRI iri) {
        return iri.toString();
      }
      if (value instanceof OWLLiteral literal) {
        return literal.getLiteral();
      }
    }
    return null;
  }
}
