package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validates a {@link SnapshotStore} against the ontology it was built from, and re-derives the
 * transitive closure independently to check the store's materialization. Works for both OWL/OBO
 * ({@code rdfs:subClassOf}) and SKOS ({@code skos:broader}/{@code narrower}) sources.
 *
 * This is a correctness oracle that does not depend on BioPortal: it recomputes the expected concept
 * and edge sets straight from the source axioms (the same rule the matching extractor applies) and
 * compares them to what the store holds, then computes the transitive closure of the store's edges
 * in plain Java and compares it to the store's precomputed closure. Discrepancies in either
 * direction are reported, along with any cycles found.
 */
public class SnapshotValidator {

  /** Findings from validating one snapshot. Lists are capped for readability; counts are exact. */
  public record Report(
      int ontologyClasses,
      int storeConcepts,
      List<String> conceptsMissingFromStore,
      List<String> conceptsExtraInStore,
      int ontologyEdges,
      int storeEdges,
      List<String> edgesMissingFromStore,
      List<String> edgesExtraInStore,
      int storeClosurePairs,
      int recomputedClosurePairs,
      List<String> closureMissingFromStore,
      List<String> closureExtraInStore,
      List<String> cycles) {

    public boolean isValid() {
      return conceptsMissingFromStore.isEmpty() && conceptsExtraInStore.isEmpty()
          && edgesMissingFromStore.isEmpty() && edgesExtraInStore.isEmpty()
          && closureMissingFromStore.isEmpty() && closureExtraInStore.isEmpty()
          && cycles.isEmpty();
    }

    public String summary() {
      return String.format(
          "classes: ontology=%d store=%d (missing=%d extra=%d); edges: ontology=%d store=%d (missing=%d extra=%d);"
              + " closure: store=%d recomputed=%d (missing=%d extra=%d); cycles=%d; valid=%b",
          ontologyClasses, storeConcepts, conceptsMissingFromStore.size(), conceptsExtraInStore.size(),
          ontologyEdges, storeEdges, edgesMissingFromStore.size(), edgesExtraInStore.size(),
          storeClosurePairs, recomputedClosurePairs, closureMissingFromStore.size(), closureExtraInStore.size(),
          cycles.size(), isValid());
    }
  }

  private static final int SAMPLE_CAP = 20;

  private static final IRI SKOS_CONCEPT = IRI.create("http://www.w3.org/2004/02/skos/core#Concept");

  /** Expected concept and edge ("child\tparent") sets derived from the source ontology. */
  private record Expected(Set<String> concepts, Set<String> edges) {}

  /** Validates an OWL/OBO snapshot (hierarchy = named-class {@code rdfs:subClassOf}). */
  public Report validate(OWLOntology ont, SnapshotStore store) throws SQLException {
    return compare(deriveOwl(ont), store);
  }

  /** Validates a SKOS snapshot (hierarchy = {@code skos:broader}/{@code narrower}). */
  public Report validateSkos(OWLOntology ont, SnapshotStore store) throws SQLException {
    return validate(ont, store, HierarchyConfig.skos());
  }

  /** Validates a relation-based snapshot against a hierarchy configuration (SKOS, RxNorm isa, ...). */
  public Report validate(OWLOntology ont, SnapshotStore store, HierarchyConfig config) throws SQLException {
    return compare(deriveRelations(ont, config), store);
  }

  public Report validateFiles(File owl, SnapshotStore store) throws Exception {
    return validate(load(owl), store);
  }

  public Report validateSkosFiles(File skos, SnapshotStore store) throws Exception {
    return validateSkos(load(skos), store);
  }

  public Report validateFiles(File file, SnapshotStore store, HierarchyConfig config) throws Exception {
    return validate(load(file), store, config);
  }

  /* -------------------------------------------------------------------------------------------- */

  private static Expected deriveOwl(OWLOntology ont) {
    Set<String> concepts = new HashSet<>();
    for (OWLClass cls : ont.getClassesInSignature()) {
      if (!cls.isOWLThing() && !cls.isOWLNothing()) {
        concepts.add(cls.getIRI().toString());
      }
    }
    Set<String> edges = new HashSet<>();
    for (OWLSubClassOfAxiom ax : ont.getAxioms(AxiomType.SUBCLASS_OF)) {
      OWLClassExpression sub = ax.getSubClass();
      OWLClassExpression sup = ax.getSuperClass();
      if (sub.isAnonymous() || sup.isAnonymous()) {
        continue;
      }
      OWLClass c = sub.asOWLClass();
      OWLClass p = sup.asOWLClass();
      if (c.isOWLThing() || c.isOWLNothing() || p.isOWLThing() || p.isOWLNothing()) {
        continue;
      }
      edges.add(c.getIRI() + "\t" + p.getIRI());
    }
    return new Expected(concepts, edges);
  }

  private static Expected deriveRelations(OWLOntology ont, HierarchyConfig config) {
    Set<String> concepts = new HashSet<>();
    Set<String> edges = new HashSet<>();
    for (OWLAnnotationAssertionAxiom ax : ont.getAxioms(AxiomType.ANNOTATION_ASSERTION)) {
      if (!(ax.getSubject() instanceof IRI subject)) {
        continue;
      }
      IRI prop = ax.getProperty().getIRI();
      OWLAnnotationValue value = ax.getValue();
      if (config.broaderPredicates().contains(prop) && value instanceof IRI parent) {
        edges.add(subject + "\t" + parent);
        concepts.add(subject.toString());
        concepts.add(parent.toString());
      } else if (config.narrowerPredicates().contains(prop) && value instanceof IRI child) {
        edges.add(child + "\t" + subject);
        concepts.add(subject.toString());
        concepts.add(child.toString());
      } else if (prop.equals(config.labelPredicate()) && value instanceof OWLLiteral) {
        concepts.add(subject.toString());
      }
    }
    for (OWLObjectPropertyAssertionAxiom ax : ont.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
      if (ax.getProperty().isAnonymous() || !ax.getSubject().isNamed() || !ax.getObject().isNamed()) {
        continue;
      }
      IRI prop = ax.getProperty().asOWLObjectProperty().getIRI();
      String subj = ax.getSubject().asOWLNamedIndividual().getIRI().toString();
      String obj = ax.getObject().asOWLNamedIndividual().getIRI().toString();
      if (config.broaderPredicates().contains(prop)) {
        edges.add(subj + "\t" + obj);
        concepts.add(subj);
        concepts.add(obj);
      } else if (config.narrowerPredicates().contains(prop)) {
        edges.add(obj + "\t" + subj);
        concepts.add(subj);
        concepts.add(obj);
      }
    }
    for (OWLClassAssertionAxiom ax : ont.getAxioms(AxiomType.CLASS_ASSERTION)) {
      if (!ax.getClassExpression().isAnonymous()
          && ax.getClassExpression().asOWLClass().getIRI().equals(SKOS_CONCEPT)
          && ax.getIndividual().isNamed()) {
        concepts.add(ax.getIndividual().asOWLNamedIndividual().getIRI().toString());
      }
    }
    return new Expected(concepts, edges);
  }

  /** Compares an expected model to the store, and independently rechecks the closure. */
  private Report compare(Expected expected, SnapshotStore store) throws SQLException {
    Set<String> storeConcepts = new HashSet<>(store.allConceptIris());
    List<String[]> edgePairs = store.allEdges();
    Set<String> storeEdges = new HashSet<>();
    for (String[] e : edgePairs) {
      storeEdges.add(e[0] + "\t" + e[1]);
    }

    Set<String> cycles = new TreeSet<>();
    Set<String> recomputedClosure = recomputeClosure(storeConcepts, edgePairs, cycles);
    Set<String> storeClosure = store.allClosurePairs();

    return new Report(
        expected.concepts().size(), storeConcepts.size(),
        sample(difference(expected.concepts(), storeConcepts)),
        sample(difference(storeConcepts, expected.concepts())),
        expected.edges().size(), storeEdges.size(),
        sample(difference(expected.edges(), storeEdges)),
        sample(difference(storeEdges, expected.edges())),
        storeClosure.size(), recomputedClosure.size(),
        sample(difference(recomputedClosure, storeClosure)),
        sample(difference(storeClosure, recomputedClosure)),
        sample(cycles));
  }

  private static OWLOntology load(File file) throws Exception {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    return manager.loadOntologyFromOntologyDocument(file);
  }

  /** Ancestor->descendant closure of the given edges (edge = [child, parent]); records cyclic nodes. */
  private static Set<String> recomputeClosure(Set<String> concepts, List<String[]> edges, Set<String> cycles) {
    Map<String, List<String>> parentsOf = new HashMap<>();
    for (String[] e : edges) {
      parentsOf.computeIfAbsent(e[0], k -> new ArrayList<>()).add(e[1]);
    }
    Set<String> closure = new HashSet<>();
    for (String node : concepts) {
      Set<String> seen = new HashSet<>();
      Deque<String> stack = new ArrayDeque<>(parentsOf.getOrDefault(node, List.of()));
      while (!stack.isEmpty()) {
        String ancestor = stack.pop();
        if (ancestor.equals(node)) {
          cycles.add(node);
          continue;
        }
        if (!seen.add(ancestor)) {
          continue;
        }
        closure.add(ancestor + '\t' + node); // (ancestor, descendant=node)
        stack.addAll(parentsOf.getOrDefault(ancestor, List.of()));
      }
    }
    return closure;
  }

  private static Set<String> difference(Set<String> a, Set<String> b) {
    Set<String> out = new TreeSet<>(a);
    out.removeAll(b);
    return out;
  }

  private static List<String> sample(Set<String> set) {
    List<String> out = new ArrayList<>();
    for (String s : set) {
      if (out.size() >= SAMPLE_CAP) {
        break;
      }
      out.add(s);
    }
    return out;
  }

  /**
   * Usage: SnapshotValidator &lt;sourceFile&gt; &lt;snapshotFile&gt; [--skos | --broader &lt;IRI&gt; ...].
   * With {@code --broader}, validates a relation-based snapshot whose hierarchy is the given
   * predicate(s) (e.g. RxNorm's isa). Exits non-zero if invalid.
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: SnapshotValidator <sourceFile> <snapshotFile> [--skos | --broader <IRI> ...]");
      System.exit(2);
    }
    boolean skos = false;
    Set<IRI> broader = new HashSet<>();
    for (int i = 2; i < args.length; i++) {
      if ("--skos".equals(args[i])) {
        skos = true;
      } else if ("--broader".equals(args[i]) && i + 1 < args.length) {
        broader.add(IRI.create(args[++i]));
      }
    }
    try (SnapshotStore store = SnapshotStore.openFile(args[1])) {
      SnapshotValidator v = new SnapshotValidator();
      File source = new File(args[0]);
      Report r;
      if (!broader.isEmpty()) {
        HierarchyConfig config = new HierarchyConfig(broader, Set.of(),
            IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel"), "subsumption");
        r = v.validateFiles(source, store, config);
      } else if (skos) {
        r = v.validateSkosFiles(source, store);
      } else {
        r = v.validateFiles(source, store);
      }
      System.out.println(r.summary());
      if (!r.edgesMissingFromStore().isEmpty()) {
        System.out.println("edges missing from store (sample): " + r.edgesMissingFromStore());
      }
      if (!r.edgesExtraInStore().isEmpty()) {
        System.out.println("edges extra in store (sample): " + r.edgesExtraInStore());
      }
      if (!r.closureMissingFromStore().isEmpty()) {
        System.out.println("closure missing from store (sample): " + r.closureMissingFromStore());
      }
      if (!r.cycles().isEmpty()) {
        System.out.println("cycles (sample): " + r.cycles());
      }
      System.exit(r.isValid() ? 0 : 1);
    }
  }
}
