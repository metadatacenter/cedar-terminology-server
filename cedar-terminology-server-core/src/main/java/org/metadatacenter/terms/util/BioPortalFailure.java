package org.metadatacenter.terms.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.ws.http.HTTPException;

/**
 * Records what BioPortal answered, and builds the exception that carries its status onward.
 *
 * <p>The terminology server relays BioPortal's HTTP status as its own: every DAO turns a non-200
 * into an {@link HTTPException} and each resource answers {@code Response.status(e.getStatusCode())}
 * with no body. Nothing wrote down what happened, so the only account of a BioPortal failure was
 * the status the client received — and the client cannot tell CEDAR's 401 from BioPortal's, or a
 * missing term from an expired key. When the server's own credential lapses, every lookup returns a
 * bodiless 401 about a credential the caller does not hold, and the server's log says nothing at all.
 *
 * <p>This does not change what the client receives. It leaves the evidence on the server, where the
 * operator can see which upstream call failed and how, which is what was missing.
 */
public final class BioPortalFailure {

  private static final Logger log = LoggerFactory.getLogger(BioPortalFailure.class);

  private BioPortalFailure() {
  }

  /**
   * Log BioPortal's answer and return the exception that relays its status.
   *
   * <p>Thrown rather than returned by the caller so the control flow stays where it was:
   * {@code throw BioPortalFailure.relay(statusCode, url)}.
   *
   * <p>A status below 400 is not a failure. Two provisional-class calls throw unconditionally and
   * reach here with the 204 that means they succeeded, so reporting every status as an error would
   * put a warning in the log for work that went through.
   */
  public static HTTPException relay(int statusCode, String url) {
    if (statusCode >= 400) {
      log.warn("BioPortal answered {} for {}; relaying that status to the caller", statusCode, url);
    } else {
      log.debug("BioPortal answered {} for {}", statusCode, url);
    }
    return new HTTPException(statusCode);
  }
}
