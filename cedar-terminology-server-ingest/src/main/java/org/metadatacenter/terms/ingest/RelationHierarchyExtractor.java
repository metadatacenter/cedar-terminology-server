package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Extracts a subsumption hierarchy from a vocabulary whose hierarchy is expressed as plain RDF
 * triples between resources (annotation assertions with IRI values), configured by a
 * {@link HierarchyConfig}: broader predicates give child-to-parent edges, narrower predicates their
 * inverse. This covers SKOS ({@code skos:broader}/{@code narrower}) and UMLS-style vocabularies with
 * an {@code isa} backbone (e.g. RxNorm) — BioPortal serializes both the same way.
 *
 * Concepts are the endpoints of those relations plus anything typed {@code skos:Concept} or
 * carrying the configured label predicate; labels prefer English. Obsolescence is
 * {@code owl:deprecated} with a successor from {@code dct:isReplacedBy}. Object-property assertions
 * of the configured predicates are also handled defensively.
 */
public class RelationHierarchyExtractor implements HierarchyExtractor {

  private static final Logger log = LoggerFactory.getLogger(RelationHierarchyExtractor.class);

  private static final IRI SKOS_CONCEPT = IRI.create("http://www.w3.org/2004/02/skos/core#Concept");
  private static final IRI DCT_IS_REPLACED_BY = IRI.create("http://purl.org/dc/terms/isReplacedBy");
  private static final IRI OWL_DEPRECATED = IRI.create("http://www.w3.org/2002/07/owl#deprecated");

  private final HierarchyConfig config;

  public RelationHierarchyExtractor(HierarchyConfig config) {
    this.config = config;
  }

  /** Convenience factory for standard SKOS. */
  public static RelationHierarchyExtractor skos() {
    return new RelationHierarchyExtractor(HierarchyConfig.skos());
  }

  @Override
  public Result extract(OWLOntology ont, SnapshotStore store) throws SQLException {
    Set<String> concepts = new LinkedHashSet<>();
    Map<String, String> prefEn = new HashMap<>();
    Map<String, String> prefAny = new HashMap<>();
    Set<String> deprecated = new HashSet<>();
    Map<String, String> replacedBy = new HashMap<>();
    Set<String> edges = new LinkedHashSet<>(); // "child\tparent"

    for (OWLAnnotationAssertionAxiom ax : ont.getAxioms(AxiomType.ANNOTATION_ASSERTION)) {
      if (!(ax.getSubject() instanceof IRI subject)) {
        continue;
      }
      String s = subject.toString();
      IRI prop = ax.getProperty().getIRI();
      OWLAnnotationValue value = ax.getValue();

      if (config.broaderPredicates().contains(prop)) {
        if (value instanceof IRI parent) {
          addEdge(edges, concepts, s, parent.toString());
        }
      } else if (config.narrowerPredicates().contains(prop)) {
        if (value instanceof IRI child) {
          addEdge(edges, concepts, child.toString(), s);
        }
      } else if (prop.equals(config.labelPredicate())) {
        if (value instanceof OWLLiteral literal) {
          concepts.add(s);
          recordLabel(prefEn, prefAny, s, literal);
        }
      } else if (prop.equals(DCT_IS_REPLACED_BY)) {
        if (value instanceof IRI r) {
          replacedBy.put(s, r.toString());
        }
      } else if (prop.equals(OWL_DEPRECATED)) {
        if (value instanceof OWLLiteral literal && literal.isBoolean() && literal.parseBoolean()) {
          deprecated.add(s);
        }
      }
    }

    // Defensive: some vocabularies declare the relation predicates as object properties.
    for (OWLObjectPropertyAssertionAxiom ax : ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
      if (ax.getProperty().isAnonymous() || !ax.getSubject().isNamed() || !ax.getObject().isNamed()) {
        continue;
      }
      IRI prop = ax.getProperty().asOWLObjectProperty().getIRI();
      String subj = ax.getSubject().asOWLNamedIndividual().getIRI().toString();
      String obj = ax.getObject().asOWLNamedIndividual().getIRI().toString();
      if (config.broaderPredicates().contains(prop)) {
        addEdge(edges, concepts, subj, obj);
      } else if (config.narrowerPredicates().contains(prop)) {
        addEdge(edges, concepts, obj, subj);
      }
    }

    for (OWLClassAssertionAxiom ax : ont.getAxioms(AxiomType.CLASS_ASSERTION)) {
      if (!ax.getClassExpression().isAnonymous()
          && ax.getClassExpression().asOWLClass().getIRI().equals(SKOS_CONCEPT)
          && ax.getIndividual().isNamed()) {
        concepts.add(ax.getIndividual().asOWLNamedIndividual().getIRI().toString());
      }
    }

    int classCount = 0;
    for (String iri : concepts) {
      String label = prefEn.getOrDefault(iri, prefAny.get(iri));
      store.addConcept(iri, label, deprecated.contains(iri), replacedBy.get(iri));
      classCount++;
    }
    int edgeCount = 0;
    for (String edge : edges) {
      int tab = edge.indexOf('\t');
      store.addEdge(edge.substring(0, tab), edge.substring(tab + 1), "hierarchy");
      edgeCount++;
    }

    store.materialize();
    log.info("Extracted {} concepts and {} hierarchy edges (predicates {})",
        classCount, edgeCount, config.broaderPredicates());
    return new Result(classCount, edgeCount);
  }

  @Override
  public Result extractFromFile(File file, SnapshotStore store) throws Exception {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLOntology ont = manager.loadOntologyFromOntologyDocument(file);
    return extract(ont, store);
  }

  private static void addEdge(Set<String> edges, Set<String> concepts, String child, String parent) {
    edges.add(child + '\t' + parent);
    concepts.add(child);
    concepts.add(parent);
  }

  private static void recordLabel(Map<String, String> prefEn, Map<String, String> prefAny,
                                  String concept, OWLLiteral literal) {
    prefAny.putIfAbsent(concept, literal.getLiteral());
    if (literal.hasLang() && literal.getLang().toLowerCase().startsWith("en")) {
      prefEn.putIfAbsent(concept, literal.getLiteral());
    }
  }
}
