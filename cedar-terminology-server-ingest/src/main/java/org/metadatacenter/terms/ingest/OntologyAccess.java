package org.metadatacenter.terms.ingest;

/**
 * BioPortal access metadata for an ontology, used to keep restricted/licensed content out of the
 * local store.
 *
 * {@code viewingRestriction} is BioPortal's access-control field ("public", "private",
 * "licensed"). Ingestion proceeds only for public ontologies; an explicit non-public value causes
 * ingestion to be refused before anything is downloaded. (BioPortal also enforces this server-side
 * by returning 403 on the download of restricted content, so this is a defense-in-depth check.)
 *
 * <p>{@code name} is the ontology's human-readable title (BioPortal's {@code name}), which the
 * catalog stores as the display name shown in the ontology picker. It is null when the source has
 * no title to offer (OBO Foundry, a direct URL), in which case the ingest falls back to the acronym.
 */
public record OntologyAccess(String viewingRestriction, String license, String name) {

  /**
   * Back-compat for sources with no per-ontology display name (OBO Foundry, direct URL, and test
   * stubs): the name is left null, and the ingest falls back to the acronym.
   */
  public OntologyAccess(String viewingRestriction, String license) {
    this(viewingRestriction, license, null);
  }

  /** True unless BioPortal explicitly marks the ontology as non-public (restricted/licensed). */
  public boolean isPublic() {
    return viewingRestriction == null
        || viewingRestriction.isBlank()
        || "public".equalsIgnoreCase(viewingRestriction);
  }
}
