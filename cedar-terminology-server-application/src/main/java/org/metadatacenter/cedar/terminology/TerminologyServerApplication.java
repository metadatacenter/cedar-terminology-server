package org.metadatacenter.cedar.terminology;

import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.health.TerminologyServerHealthCheck;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.resources.IndexResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.*;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceApplication;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.terms.TerminologyService;

import static org.metadatacenter.cedar.terminology.utils.Constants.TEST_APP_NAME;

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
  public void initializeApp(Bootstrap<TerminologyServerConfiguration> bootstrap) {
    terminologyService = new TerminologyService(cedarConfig.getTerminologyConfig().getBioPortal().getBasePath(),
        cedarConfig.getTerminologyConfig().getBioPortal().getConnectTimeout(),
        cedarConfig.getTerminologyConfig().getBioPortal().getSocketTimeout());
    AbstractTerminologyServerResource.injectTerminologyService(terminologyService);
    // Initialize cache (note that this must be done after initializing the terminologyService)
    // When running the application on testing mode, the cache is loaded from the files stored into the test
    // resources folder
    boolean testMode = false;
    if (bootstrap.getApplication().getName().equals(TEST_APP_NAME)) {
      testMode = true;
    }
    Cache.init(testMode);
  }

  @Override
  public void runApp(TerminologyServerConfiguration configuration, Environment environment) {

    final IndexResource index = new IndexResource(cedarConfig);

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
  }
}
