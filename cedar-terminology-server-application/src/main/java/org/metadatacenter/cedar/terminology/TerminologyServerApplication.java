package org.metadatacenter.cedar.terminology;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.health.TerminologyServerHealthCheck;
import org.metadatacenter.cedar.terminology.resources.AbstractResource;
import org.metadatacenter.cedar.terminology.resources.IndexResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.ClassResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.OntologyResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.RelationResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.SearchResource;
import org.metadatacenter.cedar.util.dw.CedarDropwizardApplicationUtil;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.AuthorizationKeycloakAndApiKeyResolver;
import org.metadatacenter.server.security.IAuthorizationResolver;
import org.metadatacenter.server.security.KeycloakDeploymentProvider;

public class TerminologyServerApplication extends Application<TerminologyServerConfiguration> {


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
    // Inject configuration
    AbstractResource.injectCedarConfig(CedarConfig.getInstance());
    // Initialize cache (note that this must be done after injecting the configuration)
    Cache.init();
  }

  @Override
  public void run(TerminologyServerConfiguration configuration, Environment environment) {

    final IndexResource index = new IndexResource();

    environment.jersey().register(index);
    // Register resources
    environment.jersey().register(new SearchResource());
    environment.jersey().register(new ClassResource());
    environment.jersey().register(new OntologyResource());
    environment.jersey().register(new RelationResource());

    final TerminologyServerHealthCheck healthCheck = new TerminologyServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);

    CedarDropwizardApplicationUtil.setupEnvironment(environment);

  }
}
