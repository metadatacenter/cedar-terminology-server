package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts a subsumption hierarchy from an OWL (or OBO-in-OWL) ontology into a {@link SnapshotStore}.
 *
 * A hierarchy edge is drawn to each <em>named</em> superclass, matching how BioPortal renders an
 * OWL tree. Two asserted forms contribute:
 * <ul>
 *   <li>a plain {@code rdfs:subClassOf} to a named class;</li>
 *   <li>the named <em>genus</em> of a definition — a named class appearing as a conjunct of an
 *       {@code owl:intersectionOf}, whether that intersection is the superclass of a
 *       {@code subClassOf} axiom or the definiens of an {@code owl:equivalentClass} axiom. This is
 *       the ubiquitous OBO genus-differentia pattern (e.g. {@code assay measuring X ≡ assay and
 *       (has_specified_input some X)}); the genus {@code assay} is the parent.</li>
 * </ul>
 * Restriction fillers (the {@code X} in {@code part_of some X}) are not edges, so compositional
 * relations do not pollute the {@code is_a} tree. {@code owl:Thing} and {@code owl:Nothing} are not
 * materialized, so classes with no named superclass become roots.
 *
 * Only asserted axioms are read; no reasoner is invoked (the genus is taken structurally from the
 * intersection, not inferred). Concept labels come from {@code rdfs:label}. See the
 * hierarchy-extractor design note.
 */
public class OwlHierarchyExtractor implements HierarchyExtractor {

  private static final Logger log = LoggerFactory.getLogger(OwlHierarchyExtractor.class);

  /** OBO "term replaced by": links an obsolete term to its successor. */
  private static final IRI TERM_REPLACED_BY = IRI.create("http://purl.obolibrary.org/obo/IAO_0100001");

  /**
   * Extracts the hierarchy from an already-loaded ontology into the store and materializes the
   * closure. The store should be freshly initialized ({@link SnapshotStore#initSchema()}).
   */
  @Override
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
    // Asserted rdfs:subClassOf. A named superclass is a parent; a named genus inside an
    // intersection superclass is too.
    for (OWLSubClassOfAxiom ax : ont.getAxioms(AxiomType.SUBCLASS_OF)) {
      OWLClassExpression sub = ax.getSubClass();
      if (sub.isAnonymous()) {
        continue;
      }
      OWLClass child = sub.asOWLClass();
      if (child.isOWLThing() || child.isOWLNothing()) {
        continue;
      }
      OWLClassExpression sup = ax.getSuperClass();
      if (!sup.isAnonymous() && sup.asOWLClass().isOWLThing()) {
        // An explicit "subClassOf owl:Thing" is a top-level declaration, not a hierarchy edge. Record
        // it so a declared root is distinguished from a bare, parentless imported reference.
        store.declareThingSubclass(child.getIRI().toString());
        continue;
      }
      edgeCount += addParents(store, child, namedParents(sup), "rdfs:subClassOf");
    }
    // Defined classes: the named genus of an equivalentClass intersection (genus-differentia).
    for (OWLEquivalentClassesAxiom ax : ont.getAxioms(AxiomType.EQUIVALENT_CLASSES)) {
      List<OWLClassExpression> exprs = new ArrayList<>(ax.getClassExpressions());
      for (OWLClassExpression e : exprs) {
        if (e.isAnonymous()) {
          continue;
        }
        OWLClass child = e.asOWLClass();
        if (child.isOWLThing() || child.isOWLNothing()) {
          continue;
        }
        for (OWLClassExpression definiens : exprs) {
          // Only take the genus from an anonymous definition (an intersection); a named class on
          // the other side is an equivalence, not a subsumption, and is not an edge.
          if (definiens != e && definiens.isAnonymous()) {
            edgeCount += addParents(store, child, namedParents(definiens), "equivalentClass:genus");
          }
        }
      }
    }

    store.materialize();
    log.info("Extracted {} classes and {} hierarchy edges", classCount, edgeCount);
    return new Result(classCount, edgeCount);
  }

  /** Loads an ontology from a file and extracts it into the store. */
  @Override
  public Result extractFromFile(File file, SnapshotStore store)
      throws OWLOntologyCreationException, SQLException {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    // Ignore owl:imports we can't resolve: we extract each ontology's OWN asserted
    // hierarchy, and many BioPortal ontologies import external IRIs that are
    // unreachable offline — otherwise OWLAPI throws UnloadableImportException.
    OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
        .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
    OWLOntology ont = manager.loadOntologyFromOntologyDocument(new FileDocumentSource(file), config);
    return extract(ont, store);
  }

  /**
   * The named superclasses contributed by an expression: the class itself if it is named, or the
   * named conjuncts (the genus) if it is an {@code owl:intersectionOf}. Anything else (a bare
   * restriction, a union) contributes nothing. {@code owl:Thing}/{@code owl:Nothing} are excluded.
   */
  private static List<OWLClass> namedParents(OWLClassExpression expr) {
    List<OWLClass> parents = new ArrayList<>();
    if (!expr.isAnonymous()) {
      addNamed(parents, expr);
    } else if (expr instanceof OWLObjectIntersectionOf intersection) {
      for (OWLClassExpression operand : intersection.getOperands()) {
        if (!operand.isAnonymous()) {
          addNamed(parents, operand);
        }
      }
    }
    return parents;
  }

  private static void addNamed(List<OWLClass> parents, OWLClassExpression named) {
    OWLClass c = named.asOWLClass();
    if (!c.isOWLThing() && !c.isOWLNothing()) {
      parents.add(c);
    }
  }

  private static int addParents(SnapshotStore store, OWLClass child, List<OWLClass> parents, String sourcePred)
      throws SQLException {
    int added = 0;
    for (OWLClass parent : parents) {
      if (!parent.equals(child)) {
        store.addEdge(child.getIRI().toString(), parent.getIRI().toString(), sourcePred);
        added++;
      }
    }
    return added;
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
