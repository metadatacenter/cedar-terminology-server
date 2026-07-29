package org.metadatacenter.terms.domainObjects;

import java.util.List;

/**
 * The vocabulary difference between two versions of an ontology in the local store: concept and
 * subsumption-edge counts before/after with additions and removals, how many concepts became
 * obsolete, and a capped sample of the added/removed concept IRIs. This is what a template pinned to
 * an older version would see has changed in a newer one.
 */
public record VersionDiff(
    String fromVersion,
    String toVersion,
    int conceptsBefore,
    int conceptsAfter,
    int conceptsAdded,
    int conceptsRemoved,
    int edgesBefore,
    int edgesAfter,
    int edgesAdded,
    int edgesRemoved,
    int newlyObsoleted,
    List<String> sampleAddedConcepts,
    List<String> sampleRemovedConcepts,
    String summary) {
}
