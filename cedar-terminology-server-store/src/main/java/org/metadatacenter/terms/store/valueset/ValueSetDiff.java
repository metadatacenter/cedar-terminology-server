package org.metadatacenter.terms.store.valueset;

import java.util.List;

/**
 * The change in a value set's membership between two definitions — typically the same intensional
 * rule re-pinned to a newer ontology snapshot. This is how a maintainer sees the semantic drift a
 * currency update introduces before accepting it.
 */
public record ValueSetDiff(int fromCount, int toCount, List<String> added, List<String> removed) {

  public String summary() {
    return String.format("members: %d -> %d (+%d -%d)", fromCount, toCount, added.size(), removed.size());
  }
}
