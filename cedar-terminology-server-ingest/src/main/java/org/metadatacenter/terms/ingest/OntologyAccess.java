package org.metadatacenter.terms.ingest;

/**
 * BioPortal access metadata for an ontology, used to keep restricted/licensed content out of the
 * local store.
 *
 * {@code viewingRestriction} is BioPortal's access-control field ("public", "private",
 * "licensed"). Ingestion proceeds only for public ontologies; an explicit non-public value causes
 * ingestion to be refused before anything is downloaded. (BioPortal also enforces this server-side
 * by returning 403 on the download of restricted content, so this is a defense-in-depth check.)
 */
public record OntologyAccess(String viewingRestriction, String license) {

  /** True unless BioPortal explicitly marks the ontology as non-public (restricted/licensed). */
  public boolean isPublic() {
    return viewingRestriction == null
        || viewingRestriction.isBlank()
        || "public".equalsIgnoreCase(viewingRestriction);
  }
}
