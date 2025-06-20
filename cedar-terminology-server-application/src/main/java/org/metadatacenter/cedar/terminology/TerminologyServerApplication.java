package org.metadatacenter.cedar.terminology;

import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.health.TerminologyServerHealthCheck;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.resources.IndexResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.*;
import org.metadatacenter.cedar.terminology.utils.logging.LogResponseFilter;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceApplication;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.terms.TerminologyService;
import org.metadatacenter.terms.util.HttpClientFactory;

public class TerminologyServerApplication extends CedarMicroserviceApplication<TerminologyServerConfiguration> {

  protected static TerminologyService terminologyService;

  public static void main(String[] args) throws Exception {
    new TerminologyServerApplication().run(args);
  }

  @Override
  protected ServerName getServerName() {
    return ServerName.TERMINOLOGY;
  }

  @Override
  protected void initializeWithBootstrap(Bootstrap<TerminologyServerConfiguration> bootstrap, CedarConfig cedarConfig) {
  }

  public boolean isTestMode() {
    return false;
  }

  @Override
  public void initializeApp() {
    // Force the HttpClientFactory static block to run and build the shared client:
    HttpClientFactory.client();

    terminologyService = new TerminologyService(cedarConfig.getTerminologyConfig().getBioPortal().getBasePath(),
        cedarConfig.getTerminologyConfig().getBioPortal().getConnectTimeout(),
        cedarConfig.getTerminologyConfig().getBioPortal().getSocketTimeout());
    AbstractTerminologyServerResource.injectTerminologyService(terminologyService);
    // Initialize cache (note that this must be done after initializing the terminologyService)
    // When running the application on testing mode, the cache is loaded from the files stored into the test
    // resources folder
    Cache.init(isTestMode());
  }

  @Override
  public void runApp(TerminologyServerConfiguration configuration, Environment environment) {

    final IndexResource index = new IndexResource(cedarConfig);

    environment.jersey().register(index);
    // Register resources
    environment.jersey().register(new SearchResource(cedarConfig));
    environment.jersey().register(new IntegratedSearchResource(cedarConfig));
    environment.jersey().register(new IntegratedRetrieveResource(cedarConfig));
    environment.jersey().register(new ClassResource(cedarConfig));
    environment.jersey().register(new OntologyResource(cedarConfig));
    environment.jersey().register(new RelationResource(cedarConfig));
    environment.jersey().register(new ValueSetCollectionResource(cedarConfig));
    environment.jersey().register(new ValueSetResource(cedarConfig));
    environment.jersey().register(new ValueResource(cedarConfig));
    environment.jersey().register(new PropertyResource(cedarConfig));

    // Register logging filters

    //environment.jersey().register(new LogRequestFilter());
    environment.jersey().register(new LogResponseFilter());

    final TerminologyServerHealthCheck healthCheck = new TerminologyServerHealthCheck();
    environment.healthChecks().register("message", healthCheck);
  }

}
