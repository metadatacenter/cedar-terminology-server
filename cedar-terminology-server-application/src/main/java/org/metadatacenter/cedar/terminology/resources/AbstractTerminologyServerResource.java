package org.metadatacenter.cedar.terminology.resources;

import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
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
