package org.metadatacenter.cedar.terminology;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.ElasticsearchConfig;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.AuthorizationKeycloakAndApiKeyResolver;
import org.metadatacenter.server.security.IAuthorizationResolver;
import org.metadatacenter.server.security.KeycloakDeploymentProvider;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.EnumSet;

import static org.eclipse.jetty.servlets.CrossOriginFilter.*;

public class TerminologyServerApplication extends Application<TerminologyServerConfiguration> {

  protected static CedarConfig cedarConfig;
  //protected static TerminologyService terminologyService;

  public static void main(String[] args) throws Exception {
    new TerminologyServerApplication().run(args);
  }

  @Override
  public String getName() {
    return "terminology-server";
  }

  @Override
  public void initialize(Bootstrap<TerminologyServerConfiguration> bootstrap) {
    // Init Keycloak
    KeycloakDeploymentProvider.getInstance();
    // Init Authorization Resolver
    IAuthorizationResolver authResolver = new AuthorizationKeycloakAndApiKeyResolver();
    Authorization.setAuthorizationResolver(authResolver);
    Authorization.setUserService(CedarDataServices.getUserService());

    cedarConfig = CedarConfig.getInstance();

//    ElasticsearchConfig esc = cedarConfig.getElasticsearchConfig();
//    valueRecommenderService = new ValueRecommenderService(
//        esc.getCluster(),
//        esc.getHost(),
//        esc.getIndex(),
//        esc.getType(),
//        esc.getTransportPort(),
//        esc.getSize());
//
//    ValueRecommenderResource.injectValueRecommenderService(valueRecommenderService);
  }

  @Override
  public void run(TerminologyServerConfiguration terminologyServerConfiguration, Environment environment) throws
      Exception {

  }

  // @Override
//  public void run(ValueRecommenderServerConfiguration configuration, Environment environment) {
//    final IndexResource index = new IndexResource();
//    environment.jersey().register(index);
//
//    environment.jersey().register(new ValueRecommenderResource());
//
//    final ValueRecommenderServerHealthCheck healthCheck = new ValueRecommenderServerHealthCheck();
//    environment.healthChecks().register("message", healthCheck);
//
//    environment.jersey().register(new CedarAssertionExceptionMapper());
//
//    // Enable CORS headers
//    final FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
//
//    // Configure CORS parameters
//    cors.setInitParameter(ALLOWED_ORIGINS_PARAM, "*");
//    cors.setInitParameter(ALLOWED_HEADERS_PARAM,
//        "X-Requested-With,Content-Type,Accept,Origin,Referer,User-Agent,Authorization");
//    cors.setInitParameter(ALLOWED_METHODS_PARAM, "OPTIONS,GET,PUT,POST,DELETE,HEAD,PATCH");
//
//    // Add URL mapping
//    cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
//
//  }
}
