package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.store.SnapshotDiff;
import org.metadatacenter.terms.store.SnapshotStore;

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
}
