package org.metadatacenter.cedar.terminology.resources;

import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.LocalStoreTestProperties;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.environment.CedarEnvironmentSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A server started without a catalog answers the versioned routes 503, saying no local store is configured.
 * <p>
 * The search service an earlier start opened is held in a static field. Surefire runs every class of the module in
 * one JVM, so a start without a catalog that did not reset it served the store of whichever class ran before, and
 * these two answered 200 after {@code LocalStoreResourceTest}.
 */
@Tag("integration")
public class NoCatalogSearchTest {

  static {
    LocalStoreTestProperties.clear();
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_STOP_PORT", "0");
    // Nothing here reads BioPortal; an address that refuses connections keeps it so.
    environment.put("CEDAR_BIOPORTAL_REST_BASE", "http://127.0.0.1:1/");
    CedarEnvironmentSource.setOverride(environment);
  }

  private static final DropwizardTestSupport<TerminologyServerConfiguration> SERVER =
      new DropwizardTestSupport<>(TerminologyServerApplication.class, ResourceHelpers.resourceFilePath("test-config.yml"));
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  @BeforeAll
  static void start() throws Exception {
    SERVER.before();
  }

  @AfterAll
  static void stop() {
    SERVER.after();
  }

  @Test
  void theVersionedSearchSaysThatNoCatalogIsConfigured() throws Exception {
    HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(
            URI.create("http://localhost:" + SERVER.getLocalPort() + "/search"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(
            "{\"query\":\"melanoma\",\"types\":[\"class\"],\"sources\":[{\"sourceAcronym\":\"LOCALTEST\"}]}"))
        .build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(503, response.statusCode(), response.body());
    assertTrue(response.body().contains("no catalog is configured"), response.body());
  }

  @Test
  void theHierarchySaysThatNoCatalogIsConfigured() throws Exception {
    HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder(
            URI.create("http://localhost:" + SERVER.getLocalPort()
                + "/search/hierarchy?sourceAcronym=LOCALTEST&termIri=http%3A%2F%2Flocaltest%2Fcancer&versionId=v2"))
        .GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(503, response.statusCode(), response.body());
    assertTrue(response.body().contains("no catalog is configured"), response.body());
  }
}
