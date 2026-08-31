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
   * <p>The status is unchanged here, so this adds a body and breaks nobody. Relaying an upstream 401 as
   * CEDAR's own is a separate correction, because changing the status changes what a client receives.
   */
  protected static Response relayedBioPortalFailure(HTTPException e) {
    int upstreamStatus = e.getStatusCode();
    CedarResponseStatus status = CedarResponseStatus.fromStatusCode(upstreamStatus);
    if (status == null) {
      status = CedarResponseStatus.INTERNAL_SERVER_ERROR;
    }
    return CedarResponse.status(status)
        .errorKey(CedarErrorKey.UPSTREAM_SERVER_ERROR)
        .errorMessage("BioPortal answered " + upstreamStatus + ". CEDAR is relaying that status; the "
            + "condition is upstream, and a credential named in it is CEDAR's own rather than the caller's.")
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
