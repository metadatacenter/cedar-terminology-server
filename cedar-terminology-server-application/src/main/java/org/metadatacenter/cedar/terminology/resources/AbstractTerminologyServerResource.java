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
}
