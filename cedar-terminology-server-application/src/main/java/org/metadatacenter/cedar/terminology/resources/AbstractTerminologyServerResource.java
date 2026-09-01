package org.metadatacenter.cedar.terminology.resources;

import jakarta.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.terms.ITerminologyService;

public abstract class AbstractTerminologyServerResource extends CedarMicroserviceResource {

  public static ITerminologyService terminologyService;
  protected static String apiKey;
  protected static int defaultPageSize;

  protected AbstractTerminologyServerResource(CedarConfig cedarConfig) {
    super(cedarConfig);
    apiKey = cedarConfig.getTerminologyConfig().getBioPortal().getApiKey();
    defaultPageSize = cedarConfig.getTerminologyConfig().getBioPortal().getDefaultPageSize();
  }

  public static void injectTerminologyService(ITerminologyService terminologyService) {
    AbstractTerminologyServerResource.terminologyService = terminologyService;
  }

  /**
   * Resolves a page size a caller may have spelled either way.
   *
   * <p>This server spells the same parameter both {@code page_size} and {@code pageSize}, on adjacent
   * routes. JAX-RS binds an unmatched name to the default silently, so a client that learned one
   * spelling got the default from every route using the other, with nothing to say why. Both are
   * accepted everywhere now; {@code page_size} is the documented spelling, matching the shared constant
   * files, and wins when a caller sends both.
   *
   * <p>Zero means absent: the routes declare no {@code @DefaultValue}, so an unbound int arrives as 0,
   * which is the sentinel every one of them already tested for.
   */
  /**
   * Renders a BioPortal failure this server is relaying, with a body saying so.
   *
   * <p>Thirty-three catch blocks answered {@code Response.status(e.getStatusCode()).build()} and nothing
   * else, so a caller received a bare status with no indication that it came from BioPortal rather than
   * from CEDAR. The worst case is the server's own API key expiring: the client gets a 401 about a
   * credential it does not hold and has no way to act on.
   *
   * <p>Not every upstream status is the caller's answer. A 401 or 403 from BioPortal means the key this
   * server holds was refused, which the caller can do nothing about and must not be told to retry
   * authentication over; an upstream 5xx is an outage. Both become 502, which is what a gateway says
   * when the service behind it failed. A 404 is relayed: the term really was not found, and that is the
   * caller's answer. A 400 is relayed too, since it usually reflects a parameter the caller supplied.
   */
  protected static Response relayedBioPortalFailure(HTTPException e) {
    int upstreamStatus = e.getStatusCode();
    boolean upstreamFault = upstreamStatus == 401 || upstreamStatus == 403 || upstreamStatus >= 500;

    CedarResponseStatus status;
    String explanation;
    if (upstreamFault) {
      status = CedarResponseStatus.BAD_GATEWAY;
      explanation = upstreamStatus == 401 || upstreamStatus == 403
          ? "BioPortal refused the API key this server holds, which is a CEDAR configuration problem "
              + "rather than anything the caller can authenticate its way past."
          : "BioPortal failed with " + upstreamStatus + ".";
    } else {
      status = CedarResponseStatus.fromStatusCode(upstreamStatus);
      if (status == null) {
        status = CedarResponseStatus.BAD_GATEWAY;
      }
      explanation = "BioPortal answered " + upstreamStatus + ", which is the caller's answer.";
    }

    return CedarResponse.status(status)
        .errorKey(CedarErrorKey.UPSTREAM_SERVER_ERROR)
        .errorMessage(explanation)
        .parameter("upstreamStatusCode", upstreamStatus)
        .parameter("upstreamService", "BioPortal")
        .build();
  }

  protected static int resolvePageSize(int pageSize, int pageSizeAlias) {
    if (pageSize > 0) {
      return pageSize;
    }
    if (pageSizeAlias > 0) {
      return pageSizeAlias;
    }
    return defaultPageSize;
  }
}
