package org.metadatacenter.terms.domainObjects;

/**
 * A version of an ontology available in the local, version-pinned store: the content-hash
 * {@code versionId} that pins it reproducibly, the ontology's self-declared {@code version}, when it
 * was {@code released}, and whether it is the current ({@code latest}) one. BioPortal has no
 * equivalent, so the remote backend reports none.
 */
public record OntologyVersion(String versionId, String version, String released, boolean latest) {
}
