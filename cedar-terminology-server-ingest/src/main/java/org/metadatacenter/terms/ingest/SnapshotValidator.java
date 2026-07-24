package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
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
 * Validates a {@link SnapshotStore} against the OWL ontology it was built from, and re-derives the
 * transitive closure independently to check the store's materialization.
 *
 * This is a correctness oracle that does not depend on BioPortal: it recomputes the expected named
 * {@code subClassOf} edge set straight from the axioms (the same rule the extractor applies) and
 * compares it to what the store holds, then computes the transitive closure of the store's edges in
 * plain Java and compares it to the store's precomputed closure. Discrepancies in either direction
 * are reported, along with any cycles found.
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

  public Report validate(OWLOntology ont, SnapshotStore store) throws SQLException {
    // Expected concepts and edges, derived directly from the ontology (extractor's rule).
    Set<String> expectedConcepts = new HashSet<>();
    for (OWLClass cls : ont.getClassesInSignature()) {
      if (!cls.isOWLThing() && !cls.isOWLNothing()) {
        expectedConcepts.add(cls.getIRI().toString());
      }
    }
    Set<String> expectedEdges = new HashSet<>();
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
      expectedEdges.add(edgeKey(c.getIRI().toString(), p.getIRI().toString()));
    }

    // What the store actually holds.
    Set<String> storeConcepts = new HashSet<>(store.allConceptIris());
    Set<String> storeEdges = new HashSet<>();
    List<String[]> edgePairs = store.allEdges();
    for (String[] e : edgePairs) {
      storeEdges.add(edgeKey(e[0], e[1]));
    }

    // Independent transitive closure of the store's edges.
    Set<String> cycles = new TreeSet<>();
    Set<String> recomputedClosure = recomputeClosure(storeConcepts, edgePairs, cycles);
    Set<String> storeClosure = store.allClosurePairs();

    return new Report(
        expectedConcepts.size(), storeConcepts.size(),
        sample(difference(expectedConcepts, storeConcepts)),
        sample(difference(storeConcepts, expectedConcepts)),
        expectedEdges.size(), storeEdges.size(),
        sample(difference(expectedEdges, storeEdges)),
        sample(difference(storeEdges, expectedEdges)),
        storeClosure.size(), recomputedClosure.size(),
        sample(difference(recomputedClosure, storeClosure)),
        sample(difference(storeClosure, recomputedClosure)),
        sample(cycles));
  }

  public Report validateFiles(File owl, SnapshotStore store) throws Exception {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLOntology ont = manager.loadOntologyFromOntologyDocument(owl);
    return validate(ont, store);
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

  private static String edgeKey(String child, String parent) {
    return child + '\t' + parent;
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

  /** Usage: SnapshotValidator &lt;owlFile&gt; &lt;snapshotFile&gt;. Exits non-zero if invalid. */
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: SnapshotValidator <owlFile> <snapshotFile>");
      System.exit(2);
    }
    try (SnapshotStore store = SnapshotStore.openFile(args[1])) {
      Report r = new SnapshotValidator().validateFiles(new File(args[0]), store);
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
