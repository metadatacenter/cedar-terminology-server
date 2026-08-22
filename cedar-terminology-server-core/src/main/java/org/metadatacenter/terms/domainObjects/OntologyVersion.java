package org.metadatacenter.terms.domainObjects;

/**
 * A version of an ontology available in the local, version-pinned store, carrying the full version
 * triple plus display context:
 * <ul>
 *   <li>{@code versionId} — the content hash that pins it reproducibly (the triple's {@code id}).</li>
 *   <li>{@code version} — the source's self-declared version string (the triple's
 *       {@code declaredVersion}); may be empty and repeats across uploads, so display only.</li>
 *   <li>{@code released} — the full source release timestamp, kept verbatim for provenance.</li>
 *   <li>{@code effectiveDate} — when this state entered circulation: {@code released}'s calendar day,
 *       or the ingest day when the source records no release. The triple's ordering anchor.</li>
 *   <li>{@code latest} — whether it is the current one.</li>
 * </ul>
 *
 * {@code versionId}/{@code version}/{@code effectiveDate} are the triple ({@code id}/
 * {@code declaredVersion}/{@code effectiveDate}); {@code released} and {@code version} are retained as
 * compatibility aliases for readers that predate the triple. BioPortal has no equivalent, so the
 * remote backend reports none.
 */
public record OntologyVersion(String versionId, String version, String released, String effectiveDate,
                              boolean latest) {
}
