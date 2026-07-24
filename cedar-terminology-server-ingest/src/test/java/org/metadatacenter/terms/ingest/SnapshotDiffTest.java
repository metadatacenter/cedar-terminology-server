package org.metadatacenter.terms.ingest;

import org.junit.Test;
import org.metadatacenter.terms.store.SnapshotStore;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

      assertTrue("C should be added", d.addedConcepts().contains("C"));
      // B is still present (deprecated), so it is not "removed" ...
      assertFalse("B should not be reported as removed", d.removedConcepts().contains("B"));
      // ... it is reported as a tracked obsoletion, with its replacement.
      assertTrue("B => C obsoletion should be reported", d.newlyObsoleted().contains("B => C"));
    }
  }
}
