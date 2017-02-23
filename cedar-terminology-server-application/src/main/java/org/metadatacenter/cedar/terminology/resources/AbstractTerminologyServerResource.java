package org.metadatacenter.cedar.terminology.resources;

import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.terms.TerminologyService;

public abstract class AbstractTerminologyServerResource extends CedarMicroserviceResource {

  protected static String apiKey;
  protected static int defaultPageSize;
  public static TerminologyService terminologyService;

  protected AbstractTerminologyServerResource(CedarConfig cedarConfig) {
    super(cedarConfig);
    this.apiKey = cedarConfig.getTerminologyConfig().getBioPortal().getApiKey();
    this.defaultPageSize = cedarConfig.getTerminologyConfig().getBioPortal().getDefaultPageSize();
  }

  public static void injectTerminologyService(TerminologyService terminologyService) {
    AbstractTerminologyServerResource.terminologyService = terminologyService;
  }
}