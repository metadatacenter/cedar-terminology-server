package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.store.SnapshotDiff;
import org.metadatacenter.terms.store.SnapshotStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Diff semantics at the store level, focused on obsoletion as a tracked transition (distinct from
 * removal).
 */
public class SnapshotDiffTest {

  @Test
  public void obsoletionIsATransitionNotARemoval() throws Exception {
    try (SnapshotStore from = SnapshotStore.openInMemory();
         SnapshotStore to = SnapshotStore.openInMemory()) {
      from.initSchema();
      from.addConcept("A", "A");
      from.addConcept("B", "B");
      from.materialize();

      to.initSchema();
      to.addConcept("A", "A");
      to.addConcept("B", "B (obsolete)", true, "C"); // B obsoleted, replaced by C
      to.addConcept("C", "C");
      to.materialize();

      SnapshotDiff.Diff d = new SnapshotDiff().diff(from, to);

      assertTrue( d.addedConcepts().contains("C"),"C should be added");
      // B is still present (deprecated), so it is not "removed" ...
      assertFalse( d.removedConcepts().contains("B"),"B should not be reported as removed");
      // ... it is reported as a tracked obsoletion, with its replacement.
      assertTrue( d.newlyObsoleted().contains("B => C"),"B => C obsoletion should be reported");
    }
  }

  @Test
  public void reportsEveryIdentityBearingChange() throws Exception {
    try (SnapshotStore from = SnapshotStore.openInMemory();
         SnapshotStore to = SnapshotStore.openInMemory()) {
      from.initSchema();
      from.addConcept("A", "old label", true, "B");
      from.addConcept("B", "B");
      from.addEdge("B", "A", "old-parent-predicate");
      from.addRelation("A", "old-relation", "B");
      from.materialize();

      to.initSchema();
      to.addConcept("A", "new label", false, null);
      to.addConcept("B", "B");
      to.addEdge("B", "A", "new-parent-predicate");
      to.addRelation("A", "new-relation", "B");
      to.materialize();

      SnapshotDiff.Diff diff = new SnapshotDiff().diff(from, to);

      assertEquals(List.of("A"), diff.changedConcepts());
      assertEquals(1, diff.addedEdges().size());
      assertEquals(1, diff.removedEdges().size());
      assertEquals(1, diff.addedRelations().size());
      assertEquals(1, diff.removedRelations().size());
      assertTrue(diff.newlyObsoleted().isEmpty(), "becoming active is a change, not an obsoletion");
    }
  }
}
