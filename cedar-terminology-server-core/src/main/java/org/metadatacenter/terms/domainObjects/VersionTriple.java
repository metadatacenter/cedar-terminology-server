package org.metadatacenter.terms.domainObjects;

/**
 * The version triple that identifies a pinned vocabulary state:
 * <ul>
 *   <li>{@code id} — the content hash of the ingested snapshot. <b>Identity</b>: resolution uses only
 *       this, so a pin is reproducible even when the declared version is stale or repeated.</li>
 *   <li>{@code effectiveDate} — when the state entered circulation: the source's publication (upload)
 *       date, with the ingest date as a fallback. <b>Ordering</b>: anchors {@code latest} and
 *       date-pins. A calendar date ({@code YYYY-MM-DD}); the source's timestamps are day-granular.</li>
 *   <li>{@code declaredVersion} — the source's self-declared version string. <b>Label</b> only: it may
 *       be empty, and it repeats across genuinely different uploads, so it never resolves on its
 *       own.</li>
 * </ul>
 *
 * This is what a published template stores to freeze a value constraint against ontology drift; a
 * draft template stores no triple and floats to {@code latest}. A branch, class, or value-set entry
 * inherits its ontology's triple — the snapshot is pinned per ontology, not per sub-entry.
 */
public record VersionTriple(String id, String effectiveDate, String declaredVersion) {
}
