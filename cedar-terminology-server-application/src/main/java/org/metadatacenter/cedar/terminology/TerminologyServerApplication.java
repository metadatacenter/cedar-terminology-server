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
import org.metadatacenter.config.LocalStoreConfig;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.ITerminologyService;
import org.metadatacenter.terms.RoutingTerminologyService;
import org.metadatacenter.terms.SqliteTerminologyService;
import org.metadatacenter.terms.TerminologyService;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.util.HttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TerminologyServerApplication extends CedarMicroserviceApplication<TerminologyServerConfiguration> {

  private static final Logger log = LoggerFactory.getLogger(TerminologyServerApplication.class);

  // Optional overrides for the local-store config, primarily for tests and IDE run configs. They
  // deliberately avoid the "cedar." prefix: CedarConfig claims "cedar.*" system properties as
  // Dropwizard config overrides and applies them to every config it builds (main, search, rules),
  // so a "cedar.terminology.*" property breaks the search/rules configs. The declarative source is
  // terminology.localStore in cedar-main.yml; these properties, when set, take precedence.
  static final String PROP_CATALOG_PATH = "terminologyStore.catalogPath";
  static final String PROP_LOCAL_ONTOLOGIES = "terminologyStore.localOntologies";

  protected static ITerminologyService terminologyService;

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

    TerminologyService bioPortalService =
        new TerminologyService(cedarConfig.getTerminologyConfig().getBioPortal().getBasePath(),
            cedarConfig.getTerminologyConfig().getBioPortal().getConnectTimeout(),
            cedarConfig.getTerminologyConfig().getBioPortal().getSocketTimeout());
    // Route each request to a local (version-aware) backend when the ontology is served locally,
    // else BioPortal. The local backend is enabled only when a catalog path and a non-empty
    // allowlist are configured; otherwise every request is served by BioPortal (behavior unchanged).
    terminologyService = buildTerminologyService(bioPortalService);
    AbstractTerminologyServerResource.injectTerminologyService(terminologyService);
    // Initialize cache (note that this must be done after initializing the terminologyService)
    // When running the application on testing mode, the cache is loaded from the files stored into the test
    // resources folder
    Cache.init(isTestMode());
  }

  /**
   * Builds the terminology service from {@code terminology.localStore} configuration. When a
   * catalog path and a non-empty ontology list are configured, routed requests for those ingested
   * ontologies are served from the local SQLite store, falling back to BioPortal for everything
   * else. Otherwise, or on any failure opening the catalog, the service is BioPortal-only.
   */
  private ITerminologyService buildTerminologyService(TerminologyService bioPortalService) {
    LocalStoreConfig localStore = cedarConfig.getTerminologyConfig().getLocalStore();
    String catalogPath = firstNonBlank(System.getProperty(PROP_CATALOG_PATH),
        localStore == null ? null : localStore.getCatalogPath());
    Set<String> localOntologies = parseAllowlist(firstNonBlank(System.getProperty(PROP_LOCAL_ONTOLOGIES),
        localStore == null ? null : localStore.getLocalOntologies()));
    if (catalogPath == null || catalogPath.isBlank() || localOntologies.isEmpty()) {
      log.info("Local terminology store disabled; serving all ontologies via BioPortal");
      return new RoutingTerminologyService(bioPortalService);
    }
    try {
      CatalogStore catalog = CatalogStore.openFile(catalogPath);
      CatalogSnapshotProvider provider = new CatalogSnapshotProvider(catalog, localOntologies);
      SqliteTerminologyService local = new SqliteTerminologyService(provider);
      log.info("Local terminology store enabled from {} for ontologies {}", catalogPath, localOntologies);
      return new RoutingTerminologyService(bioPortalService, local, local::isAvailable);
    } catch (Exception e) {
      log.error("Failed to enable local terminology store from {}; serving via BioPortal only",
          catalogPath, e);
      return new RoutingTerminologyService(bioPortalService);
    }
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a;
    }
    return b;
  }

  private static Set<String> parseAllowlist(String csv) {
    if (csv == null || csv.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
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
