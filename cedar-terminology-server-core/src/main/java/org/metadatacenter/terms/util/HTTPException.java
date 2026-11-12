package org.metadatacenter.terms.util;

/**
 * An HTTP exchange that failed, with the status code that answers for it.
 *
 * <p>A DAO throws it with the status BioPortal returned, through {@link BioPortalFailure#relay},
 * and the service throws it with a status of its own for a request it rejects. Each resource
 * answers with {@link #getStatusCode()}.
 */
public class HTTPException extends RuntimeException {

  private final int statusCode;

  public HTTPException(int statusCode) {
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
