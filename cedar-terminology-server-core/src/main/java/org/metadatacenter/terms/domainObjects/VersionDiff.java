package org.metadatacenter.terms.domainObjects;

import java.util.List;

/**
 * The vocabulary difference between two versions of an ontology in the local store: concept,
 * subsumption-edge, and typed-relation counts before/after with additions, removals, and
 * content changes. This is what a template pinned to an older version would see has changed in a
 * newer one.
 */
public record VersionDiff(
    String fromVersion,
    String toVersion,
    int conceptsBefore,
    int conceptsAfter,
    int conceptsAdded,
    int conceptsRemoved,
    int conceptsChanged,
    int edgesBefore,
    int edgesAfter,
    int edgesAdded,
    int edgesRemoved,
    int relationsBefore,
    int relationsAfter,
    int relationsAdded,
    int relationsRemoved,
    int newlyObsoleted,
    List<String> sampleAddedConcepts,
    List<String> sampleRemovedConcepts,
    List<String> sampleChangedConcepts,
    String summary) {
}
