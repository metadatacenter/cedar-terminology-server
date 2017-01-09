package org.metadatacenter.cedar.terminology.resources;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.terms.TerminologyService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

public abstract class AbstractResource {

  protected
  @Context
  UriInfo uriInfo;

  protected
  @Context
  HttpServletRequest request;

  protected
  @Context
  HttpServletResponse response;

  protected static String apiKey;
  protected static int defaultPageSize;
  public static TerminologyService terminologyService;


  public static void injectCedarConfig(CedarConfig cedarConfig) {
    AbstractResource.apiKey = cedarConfig.getTerminologyConfig().getBioPortal().getApiKey();
    AbstractResource.defaultPageSize = cedarConfig.getTerminologyConfig().getBioPortal().getDefaultPageSize();
    AbstractResource.terminologyService = new TerminologyService(cedarConfig.getTerminologyConfig()
        .getBioPortal().getBasePath(),
        cedarConfig.getTerminologyConfig().getBioPortal().getConnectTimeout(),
        cedarConfig.getTerminologyConfig().getBioPortal().getSocketTimeout());
  }

}