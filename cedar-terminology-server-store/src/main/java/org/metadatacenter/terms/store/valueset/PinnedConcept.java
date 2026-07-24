package org.metadatacenter.terms.store.valueset;

/**
 * A version-pinned concept reference: a concept IRI together with the content-hash id of the
 * snapshot it is pinned to. This is what makes a value-set member reproducible — it names a
 * concept-at-a-version, not just a concept.
 */
public record PinnedConcept(String versionId, String conceptIri) {}
