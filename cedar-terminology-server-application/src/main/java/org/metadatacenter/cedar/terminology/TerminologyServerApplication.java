package org.metadatacenter.cedar.terminology;

import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.health.TerminologyServerHealthCheck;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.resources.IndexResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.*;
import org.metadatacenter.cedar.util.dw.CedarDropwizardApplicationUtil;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.terms.TerminologyService;

public class TerminologyServerApplication extends Application<TerminologyServerConfiguration> {

  private static CedarConfig cedarConfig;
  private static TerminologyService terminologyService;

  public static void main(String[] args) throws Exception {
    new TerminologyServerApplication().run(args);
  }

  @Override
  public String getName() {
    return "terminology-server";
  }

  @Override
  public void initialize(Bootstrap<TerminologyServerConfiguration> bootstrap) {
    CedarDropwizardApplicationUtil.setupKeycloak();
    cedarConfig = CedarConfig.getInstance();
    terminologyService = new TerminologyService(cedarConfig.getTerminologyConfig().getBioPortal().getBasePath(),
        cedarConfig.getTerminologyConfig().getBioPortal().getConnectTimeout(),
        cedarConfig.getTerminologyConfig().getBioPortal().getSocketTimeout());
    AbstractTerminologyServerResource.injectTerminologyService(terminologyService);
    // Initialize cache (note that this must be done after initializing the terminologyService)
    Cache.init();
  }

  @Override
  public void run(TerminologyServerConfiguration configuration, Environment environment) {

    final IndexResource index = new IndexResource();

    environment.jersey().register(index);
    // Register resources
    environment.jersey().register(new SearchResource(cedarConfig));
    environment.jersey().register(new ClassResource(cedarConfig));
    environment.jersey().register(new OntologyResource(cedarConfig));
    environment.jersey().register(new RelationResource(cedarConfig));
    environment.jersey().register(new ValueSetCollectionResource(cedarConfig));
    environment.jersey().register(new ValueSetResource(cedarConfig));
    environment.jersey().register(new ValueResource(cedarConfig));

    final TerminologyServerHealthCheck healthCheck = new TerminologyServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);

    CedarDropwizardApplicationUtil.setupEnvironment(environment);

  }
}
