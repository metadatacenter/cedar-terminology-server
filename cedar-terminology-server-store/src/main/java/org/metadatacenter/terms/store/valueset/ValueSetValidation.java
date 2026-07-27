package org.metadatacenter.terms.store.valueset;

import java.util.List;

/**
 * The health of a value set's members against a target ontology snapshot: how many are still
 * active, which became obsolete (with their replacement IRI when known), and which are gone
 * entirely. Answers "is this value set still valid against the current ontology version, and what
 * would need re-curating if I move it forward?"
 */
public record ValueSetValidation(int total, int active, List<String> obsoleted, List<String> removed) {

  public boolean isClean() {
    return obsoleted.isEmpty() && removed.isEmpty();
  }

  public String summary() {
    return String.format("members: %d (active %d, obsoleted %d, removed %d); clean=%b",
        total, active, obsoleted.size(), removed.size(), isClean());
  }
}
