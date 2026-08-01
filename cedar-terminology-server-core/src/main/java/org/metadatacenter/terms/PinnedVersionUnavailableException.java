package org.metadatacenter.terms;

/**
 * Thrown when a value constraint pins an explicit vocabulary version that the local snapshot backend
 * cannot serve (the ontology is not served locally, or that exact snapshot is absent).
 *
 * <p>This is deliberately <em>not</em> an {@link UnsupportedOperationException}. The router treats
 * {@code UnsupportedOperationException} as "the local backend does not implement this call" and answers
 * from the remote adapter (BioPortal). But BioPortal serves the <em>current</em> content of an ontology,
 * not a historical snapshot — so downgrading a pinned request to remote would silently break a frozen
 * read, resolving terms against latest instead of the pinned version. A pin that cannot be honored must
 * fail loud; the router therefore must never catch this exception and fall back.
 */
public class PinnedVersionUnavailableException extends RuntimeException {

  public PinnedVersionUnavailableException(String message) {
    super(message);
  }
}
