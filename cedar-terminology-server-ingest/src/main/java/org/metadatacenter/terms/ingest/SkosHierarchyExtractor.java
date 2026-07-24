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
 * Extracts a subsumption hierarchy from a SKOS vocabulary into a {@link SnapshotStore}.
 *
 * The hierarchy edge is {@code skos:broader}: {@code A skos:broader B} means B is broader than A,
 * so B is A's parent (child A to parent B). {@code skos:narrower} is treated as its inverse and
 * {@code skos:broaderTransitive} like {@code broader}; {@code skos:related} (associative) is
 * ignored. Concepts are the SKOS concepts (subjects/objects of these relations, plus anything typed
 * {@code skos:Concept}); labels come from {@code skos:prefLabel}, preferring English. Obsolescence
 * is {@code owl:deprecated}, with the successor from {@code dct:isReplacedBy}.
 *
 * SKOS files typically use these as plain RDF triples with IRI objects (not declared OWL object
 * properties), so OWLAPI surfaces them as annotation assertions; object-property assertions are
 * also handled defensively.
 */
public class SkosHierarchyExtractor implements HierarchyExtractor {

  private static final Logger log = LoggerFactory.getLogger(SkosHierarchyExtractor.class);

  private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
  private static final IRI SKOS_BROADER = IRI.create(SKOS + "broader");
  private static final IRI SKOS_BROADER_TRANSITIVE = IRI.create(SKOS + "broaderTransitive");
  private static final IRI SKOS_NARROWER = IRI.create(SKOS + "narrower");
  private static final IRI SKOS_PREF_LABEL = IRI.create(SKOS + "prefLabel");
  private static final IRI SKOS_CONCEPT = IRI.create(SKOS + "Concept");
  private static final IRI DCT_IS_REPLACED_BY = IRI.create("http://purl.org/dc/terms/isReplacedBy");
  private static final IRI OWL_DEPRECATED = IRI.create("http://www.w3.org/2002/07/owl#deprecated");

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

      if (prop.equals(SKOS_BROADER) || prop.equals(SKOS_BROADER_TRANSITIVE)) {
        if (value instanceof IRI parent) {
          addEdge(edges, concepts, s, parent.toString());
        }
      } else if (prop.equals(SKOS_NARROWER)) {
        if (value instanceof IRI child) {
          addEdge(edges, concepts, child.toString(), s);
        }
      } else if (prop.equals(SKOS_PREF_LABEL)) {
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

    // Defensive: some SKOS files declare skos:broader/narrower as object properties.
    for (OWLObjectPropertyAssertionAxiom ax : ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
      if (ax.getProperty().isAnonymous() || !ax.getSubject().isNamed() || !ax.getObject().isNamed()) {
        continue;
      }
      IRI prop = ax.getProperty().asOWLObjectProperty().getIRI();
      String subj = ax.getSubject().asOWLNamedIndividual().getIRI().toString();
      String obj = ax.getObject().asOWLNamedIndividual().getIRI().toString();
      if (prop.equals(SKOS_BROADER) || prop.equals(SKOS_BROADER_TRANSITIVE)) {
        addEdge(edges, concepts, subj, obj);
      } else if (prop.equals(SKOS_NARROWER)) {
        addEdge(edges, concepts, obj, subj);
      }
    }

    // Anything explicitly typed skos:Concept is a concept, even if it has no broader/narrower.
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
      store.addEdge(edge.substring(0, tab), edge.substring(tab + 1), "skos:broader");
      edgeCount++;
    }

    store.materialize();
    log.info("Extracted {} SKOS concepts and {} broader/narrower edges", classCount, edgeCount);
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
