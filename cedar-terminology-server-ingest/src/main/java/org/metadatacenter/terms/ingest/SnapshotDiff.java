package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.SnapshotStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compares two snapshots of the same ontology (two versions) by concept IRI and by hierarchy edge.
 *
 * This is the mechanical basis for the "show the vocabulary diff, then re-pin" workflow: given the
 * snapshot a template is pinned to and a newer snapshot, it reports which concepts and which
 * subsumption edges were added or removed. Identity is the concept IRI, which is stable across
 * versions for IRI-based ontologies (e.g. OBO PURLs), so the diff is meaningful even across a
 * format change (OBO to OWL).
 */
public class SnapshotDiff {

  /** Concept- and edge-level differences from a base snapshot to a target snapshot. */
  public record Diff(
      int fromConcepts,
      int toConcepts,
      List<String> addedConcepts,
      List<String> removedConcepts,
      int fromEdges,
      int toEdges,
      List<String> addedEdges,
      List<String> removedEdges) {

    public String summary() {
      return String.format(
          "concepts: %d -> %d (+%d -%d); edges: %d -> %d (+%d -%d)",
          fromConcepts, toConcepts, addedConcepts.size(), removedConcepts.size(),
          fromEdges, toEdges, addedEdges.size(), removedEdges.size());
    }
  }

  public Diff diff(SnapshotStore from, SnapshotStore to) throws SQLException {
    Set<String> fromConcepts = new HashSet<>(from.allConceptIris());
    Set<String> toConcepts = new HashSet<>(to.allConceptIris());
    Set<String> fromEdges = edgeSet(from);
    Set<String> toEdges = edgeSet(to);
    return new Diff(
        fromConcepts.size(), toConcepts.size(),
        sortedDifference(toConcepts, fromConcepts),
        sortedDifference(fromConcepts, toConcepts),
        fromEdges.size(), toEdges.size(),
        sortedDifference(toEdges, fromEdges),
        sortedDifference(fromEdges, toEdges));
  }

  private static Set<String> edgeSet(SnapshotStore store) throws SQLException {
    Set<String> out = new HashSet<>();
    for (String[] e : store.allEdges()) {
      out.add(e[0] + " -> " + e[1]);
    }
    return out;
  }

  private static List<String> sortedDifference(Set<String> a, Set<String> b) {
    Set<String> out = new TreeSet<>(a);
    out.removeAll(b);
    return new ArrayList<>(out);
  }

  /** Usage: SnapshotDiff &lt;fromSnapshot.sqlite&gt; &lt;toSnapshot.sqlite&gt;. */
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: SnapshotDiff <fromSnapshot.sqlite> <toSnapshot.sqlite>");
      System.exit(2);
    }
    try (SnapshotStore from = SnapshotStore.openFile(args[0]);
         SnapshotStore to = SnapshotStore.openFile(args[1])) {
      Diff d = new SnapshotDiff().diff(from, to);
      System.out.println(d.summary());
      printSample("added concepts", d.addedConcepts());
      printSample("removed concepts", d.removedConcepts());
      printSample("added edges", d.addedEdges());
      printSample("removed edges", d.removedEdges());
    }
  }

  private static void printSample(String label, List<String> items) {
    if (items.isEmpty()) {
      return;
    }
    System.out.println(label + " (" + items.size() + ", showing up to 15):");
    items.stream().limit(15).forEach(s -> System.out.println("  " + s));
  }
}
