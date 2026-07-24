package org.metadatacenter.terms.ingest;

/**
 * Metadata for one BioPortal ontology submission (a single version of an ontology).
 *
 * {@code format} is BioPortal's {@code hasOntologyLanguage} (e.g. {@code OWL}, {@code OBO},
 * {@code SKOS}, {@code UMLS}); it selects the extraction rule.
 */
public record Submission(int submissionId, String version, String released, String format) {}
