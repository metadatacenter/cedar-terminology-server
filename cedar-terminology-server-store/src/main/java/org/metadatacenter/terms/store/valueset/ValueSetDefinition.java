package org.metadatacenter.terms.store.valueset;

import java.util.List;

/**
 * How a value set's membership is defined. Three kinds:
 *
 * <ul>
 *   <li>{@code EXTENSIONAL} — an explicit list of version-pinned concepts.</li>
 *   <li>{@code DESCENDANTS} — intensional: all descendants of a concept in a pinned snapshot
 *       (optionally including the concept itself), resolved from the snapshot's closure.</li>
 *   <li>{@code RELATION} — intensional: all concepts related to a target by a predicate in a
 *       pinned snapshot, e.g. drugs whose {@code has_ingredient} is aspirin.</li>
 * </ul>
 *
 * Every kind is pinned to a specific snapshot version, so expansion is reproducible.
 */
public record ValueSetDefinition(
    Kind kind,
    String snapshotVersionId,
    String rootIri,
    boolean includeRoot,
    String predicate,
    String objectIri,
    List<PinnedConcept> members) {

  public enum Kind {DESCENDANTS, RELATION, EXTENSIONAL}

  public static ValueSetDefinition descendants(String snapshotVersionId, String rootIri, boolean includeRoot) {
    return new ValueSetDefinition(Kind.DESCENDANTS, snapshotVersionId, rootIri, includeRoot, null, null, List.of());
  }

  public static ValueSetDefinition relation(String snapshotVersionId, String predicate, String objectIri) {
    return new ValueSetDefinition(Kind.RELATION, snapshotVersionId, null, false, predicate, objectIri, List.of());
  }

  public static ValueSetDefinition extensional(List<PinnedConcept> members) {
    return new ValueSetDefinition(Kind.EXTENSIONAL, null, null, false, null, null, List.copyOf(members));
  }
}
