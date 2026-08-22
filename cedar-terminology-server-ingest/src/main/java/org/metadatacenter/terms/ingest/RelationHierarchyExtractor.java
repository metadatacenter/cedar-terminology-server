package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
    Labels en = new Labels();
    Labels any = new Labels();
    Set<String> deprecated = new HashSet<>();
    Map<String, String> replacedBy = new HashMap<>();
    Set<String> edges = new LinkedHashSet<>(); // "child\tparent"
    List<String[]> relations = new ArrayList<>(); // [subject, predicate, object] when retainRelations
    List<SnapshotStore.LabelRow> labelRows = new ArrayList<>(); // every name literal, all languages
    List<SnapshotStore.DefinitionRow> definitionRows = new ArrayList<>();

    for (OWLAnnotationAssertionAxiom ax : ont.getAxioms(AxiomType.ANNOTATION_ASSERTION)) {
      if (!(ax.getSubject() instanceof IRI subject)) {
        continue;
      }
      String s = subject.toString();
      IRI prop = ax.getProperty().getIRI();
      OWLAnnotationValue value = ax.getValue();

      // Preserve every language variant of every name (labels + synonyms) alongside the single served
      // pref_label the dispatch below picks from the configured label predicate. Additive; a row is
      // kept below only if its subject turns out to be a concept.
      if (value instanceof OWLLiteral defLiteral) {
        String curie = DefinitionProperties.curieFor(prop);
        String text = curie == null ? null : defLiteral.getLiteral().trim();
        if (text != null && !text.isEmpty()) {
          definitionRows.add(new SnapshotStore.DefinitionRow(s, curie, defLiteral.getLang(), text));
        }
      }

      if (value instanceof OWLLiteral nameLiteral) {
        String curie = LabelProperties.curieFor(prop);
        // A list packed into one literal becomes one name per entry, so each is findable on its own.
        String name = curie == null ? null : Names.nameOf(nameLiteral.getLiteral());
        if (name != null) {
          labelRows.add(new SnapshotStore.LabelRow(s, curie, nameLiteral.getLang(), name));
          for (String rest : Names.restOf(nameLiteral.getLiteral())) {
            labelRows.add(new SnapshotStore.LabelRow(s, curie, nameLiteral.getLang(), rest));
          }
        }
      }

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
          recordLabel(any, en, s, literal);
        }
      } else if (prop.equals(DCT_IS_REPLACED_BY)) {
        if (value instanceof IRI r) {
          replacedBy.put(s, r.toString());
        }
      } else if (prop.equals(OWL_DEPRECATED)) {
        if (value instanceof OWLLiteral literal && literal.isBoolean() && literal.parseBoolean()) {
          deprecated.add(s);
        }
      } else if (config.retainRelations() && value instanceof IRI object) {
        // Any other IRI-valued predicate is a candidate Level-1 relation (kept below if both
        // endpoints turn out to be concepts).
        relations.add(new String[]{s, prop.toString(), object.toString()});
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
      } else if (config.retainRelations()) {
        relations.add(new String[]{subj, prop.toString(), obj});
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
      String label = en.getOrDefault(iri, any.get(iri));
      store.addConcept(iri, label, deprecated.contains(iri), replacedBy.get(iri));
      classCount++;
    }
    // Insert after the concepts exist; addLabels joins on concept IRI, so name literals of a subject
    // that did not become a concept are dropped, exactly as its edges/relations would be.
    store.addLabels(labelRows);
    store.addDefinitions(definitionRows);
    int edgeCount = 0;
    for (String edge : edges) {
      int tab = edge.indexOf('\t');
      store.addEdge(edge.substring(0, tab), edge.substring(tab + 1), "hierarchy");
      edgeCount++;
    }

    if (config.retainRelations() && !relations.isEmpty()) {
      List<String[]> kept = new ArrayList<>();
      for (String[] triple : relations) {
        if (concepts.contains(triple[0]) && concepts.contains(triple[2])) {
          kept.add(triple);
        }
      }
      store.addRelations(kept);
      log.info("Retained {} Level-1 relations (of {} IRI-valued candidates)", kept.size(), relations.size());
    }

    store.materialize();
    log.info("Extracted {} concepts and {} hierarchy edges (predicates {})",
        classCount, edgeCount, config.broaderPredicates());
    return new Result(classCount, edgeCount);
  }

  @Override
  public Result extractFromFile(File file, SnapshotStore store) throws Exception {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    // Ignore unresolvable owl:imports (see OwlHierarchyExtractor) — we extract only
    // this ontology's own relation-based hierarchy.
    OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
        .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
    OWLOntology ont = manager.loadOntologyFromOntologyDocument(new FileDocumentSource(file), config);
    return extract(ont, store);
  }

  private static void addEdge(Set<String> edges, Set<String> concepts, String child, String parent) {
    edges.add(child + '\t' + parent);
    concepts.add(child);
    concepts.add(parent);
  }

  private static void recordLabel(Labels any, Labels en, String concept, OWLLiteral literal) {
    // A blank literal is not a label, and neither is a list packed into one literal. Taking a blank
    // leaves the concept unlabeled as far as everything downstream is concerned, and it then draws
    // the IRI-fragment fallback; a list reaches a reader as its entries run together on one line.
    String name = Names.nameOf(literal.getLiteral());
    if (name == null) {
      return;
    }
    boolean list = Names.hasBreak(literal.getLiteral());
    any.offer(concept, name, list);
    if (literal.hasLang() && literal.getLang().toLowerCase().startsWith("en")) {
      en.offer(concept, name, list);
    }
  }

  /**
   * The best label seen so far for each concept, under one language preference.
   *
   * First literal wins, except that a name displaces a list: where a concept asserts both a plain
   * label and a list packed into one literal, the plain one is the name the source meant — whichever
   * order the two are read in, which is not fixed.
   */
  private static final class Labels {
    private final Map<String, String> held = new HashMap<>();
    private final Set<String> fromList = new HashSet<>();

    void offer(String concept, String name, boolean list) {
      if (!held.containsKey(concept)) {
        held.put(concept, name);
        if (list) {
          fromList.add(concept);
        }
      } else if (!list && fromList.remove(concept)) {
        held.put(concept, name);
      }
    }

    String get(String concept) {
      return held.get(concept);
    }

    String getOrDefault(String concept, String fallback) {
      return held.getOrDefault(concept, fallback);
    }
  }
}
