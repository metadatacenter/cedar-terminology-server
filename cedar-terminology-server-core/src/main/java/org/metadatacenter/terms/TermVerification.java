package org.metadatacenter.terms;

/** A semantic check is separate from structural validity and from the caller's save policy. */
public record TermVerification(String status, String code, String message, String iri) {
  public static TermVerification unavailable(String iri, String message) {
    return new TermVerification("unavailable", "semanticUnavailable", message, iri);
  }
}
