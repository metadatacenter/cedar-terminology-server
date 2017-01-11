package org.metadatacenter.cedar.terminology.resources;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.terms.TerminologyService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

public abstract class AbstractTerminologyServerResource {

  protected
  @Context
  UriInfo uriInfo;

  protected
  @Context
  HttpServletRequest request;

  protected
  @Context
  HttpServletResponse response;

  protected final CedarConfig cedarConfig;
  protected static String apiKey;
  protected static int defaultPageSize;
  public static TerminologyService terminologyService;

  protected AbstractTerminologyServerResource(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
    this.apiKey = cedarConfig.getTerminologyConfig().getBioPortal().getApiKey();
    this.defaultPageSize = cedarConfig.getTerminologyConfig().getBioPortal().getDefaultPageSize();
  }

  public static void injectTerminologyService(TerminologyService terminologyService) {
    AbstractTerminologyServerResource.terminologyService = terminologyService;
  }
}