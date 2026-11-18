package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.test.TestAuthUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A registry listing shorter than BioPortal's real one is a failed load, not an answer. A cold or rate-limited
 * BioPortal has listed only the value-set collections, and serving that made the picker name ontologies by their bare
 * acronyms. The server refuses a listing below {@link Cache#MIN_EXPECTED_ONTOLOGIES} with a 502 and serves one at the
 * floor. A local-only server is exempt, since its short list is the whole catalog; {@code EquivalenceTest} lists its
 * three ontologies.
 */
@Tag("integration")
public class ShortOntologyRegistryTest {

  private static final FakeBioPortal BIOPORTAL = startBioPortal();

  static {
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "0");
    environment.putAll(BIOPORTAL.environment());
    CedarEnvironmentSource.setOverride(environment);
  }

  private static final DropwizardTestSupport<TerminologyServerConfiguration> SERVER =
      new DropwizardTestSupport<>(TerminologyServerApplication.class, ResourceHelpers.resourceFilePath("test-config.yml"));
  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper JSON = new ObjectMapper();
  private static String authHeader;

  @BeforeAll
  public static void setUp() throws Exception {
    SERVER.before();
    CedarConfig cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_TERMINOLOGY));
    TestAuthUtil.installInMemoryUserService(cedarConfig);
    authHeader = TestAuthUtil.getTestUser1AuthHeader(cedarConfig);
  }

  @AfterAll
  public static void tearDown() {
    SERVER.after();
    BIOPORTAL.close();
  }

  @Test
  void aListingOneShortOfTheFloorIsRefused() throws Exception {
    BIOPORTAL.listFillers(Cache.MIN_EXPECTED_ONTOLOGIES - 1 - FakeBioPortal.ONTOLOGIES);
    HttpResponse<String> response = ontologies();
    assertEquals(502, response.statusCode(), response.body());
    JsonNode error = JSON.readTree(response.body());
    assertTrue(error.path("message").asText().contains("came back with only " + (Cache.MIN_EXPECTED_ONTOLOGIES - 1)
        + " entries"), response.body());
  }

  @Test
  void aListingAtTheFloorIsServed() throws Exception {
    BIOPORTAL.listFillers(Cache.MIN_EXPECTED_ONTOLOGIES - FakeBioPortal.ONTOLOGIES);
    HttpResponse<String> response = ontologies();
    assertEquals(200, response.statusCode(), response.body());
    assertEquals(Cache.MIN_EXPECTED_ONTOLOGIES, JSON.readTree(response.body()).size(), response.body());
  }

  private static HttpResponse<String> ontologies() throws IOException, InterruptedException {
    return CLIENT.send(HttpRequest.newBuilder(URI.create("http://localhost:" + SERVER.getLocalPort() + "/bioportal/ontologies"))
        .header("Authorization", authHeader).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private static FakeBioPortal startBioPortal() {
    try {
      return new FakeBioPortal();
    } catch (IOException e) {
      throw new IllegalStateException("Could not start the fake BioPortal", e);
    }
  }
}
