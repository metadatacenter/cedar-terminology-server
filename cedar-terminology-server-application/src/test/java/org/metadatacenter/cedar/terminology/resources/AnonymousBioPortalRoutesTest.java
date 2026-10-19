package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.TestAuthUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four BioPortal routes that serve anonymous callers answer one, and the spec says that a credential
 * is optional on them.
 *
 * <p>The spec declared {@code api_key} on all four, as on every route under {@code /bioportal}, while each
 * answered a request that carried no credential. A client built from the spec therefore insisted on a
 * key the route never read, and the search and descendants routes documented a 401 and a 403 that
 * neither sends. Each route has answered anonymous callers in every release for years, and callers in
 * CEDAR rely on two of them, so the spec now describes the routes rather than a gate they do not have.
 *
 * <p>BioPortal is a stub that answers every call with a 404, which the routes relay. A route that asked
 * BioPortal had already passed any gate it has, so the stub's count shows that the anonymous request was
 * served rather than refused. A gated sibling receives the same anonymous request as a control.
 */
@Tag("integration")
public class AnonymousBioPortalRoutesTest {

  private static final AtomicInteger ASKED = new AtomicInteger();
  private static final HttpServer BIOPORTAL = startBioPortal();

  static {
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_STOP_PORT", "0");
    environment.put("CEDAR_BIOPORTAL_REST_BASE", "http://127.0.0.1:" + BIOPORTAL.getAddress().getPort() + "/");
    CedarEnvironmentSource.setOverride(environment);
  }

  private static final DropwizardTestSupport<TerminologyServerConfiguration> SERVER =
      new DropwizardTestSupport<>(TerminologyServerApplication.class, ResourceHelpers.resourceFilePath("test-config.yml"));

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  /** An NCIT class identifier, encoded for a path. */
  private static final String CLASS = "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C12345";
  private static final String CONSTRAINTS = "{\"ontologies\":[{\"acronym\":\"NCIT\",\"name\":\"NCIT\","
      + "\"uri\":\"https://data.bioontology.org/ontologies/NCIT\"}],\"valueSets\":[],\"classes\":[],\"branches\":[]}";
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
    BIOPORTAL.stop(0);
  }

  @ParameterizedTest(name = "{0} {1}")
  @CsvSource(delimiter = '|', value = {
      "GET  | /bioportal/search?q=cancer",
      "GET  | /bioportal/ontologies/NCIT/classes/" + CLASS + "/descendants",
      "POST | /bioportal/integrated-search",
      "POST | /bioportal/integrated-retrieve"})
  public void anAnonymousRequestIsServedAsAnAuthenticatedOneIs(String method, String request) throws Exception {
    ASKED.set(0);
    HttpResponse<String> anonymous = send(method, request, null);
    assertTrue(ASKED.get() > 0, "the route refused before asking BioPortal: " + anonymous.body());
    assertEquals(404, anonymous.statusCode(), "the route should relay BioPortal's 404: " + anonymous.body());

    HttpResponse<String> authenticated = send(method, request, authHeader);
    assertEquals(anonymous.statusCode(), authenticated.statusCode(), authenticated.body());
  }

  @Test
  public void aGatedSiblingRefusesTheSameAnonymousRequest() throws Exception {
    ASKED.set(0);
    HttpResponse<String> response = send("GET", "/bioportal/ontologies/NCIT/classes/" + CLASS + "/children", null);
    assertEquals(401, response.statusCode(), response.body());
    assertEquals(0, ASKED.get(), "a refused request should not reach BioPortal");
  }

  @ParameterizedTest(name = "{0} {1}")
  @CsvSource(delimiter = '|', value = {
      "get  | /bioportal/search",
      "get  | /bioportal/ontologies/{ontology}/classes/{id}/descendants",
      "post | /bioportal/integrated-search",
      "post | /bioportal/integrated-retrieve"})
  public void theSpecSaysThatACredentialIsOptional(String method, String path) throws IOException {
    JsonNode operation = readSpec().path("paths").path(path).path(method);
    assertFalse(operation.isMissingNode(), method + " " + path + " is not in the spec");

    assertEquals(JsonMapper.STRICT_MAPPER.readTree("[{\"api_key\":[]},{}]"), operation.path("security"),
        "an empty requirement beside api_key is how OpenAPI marks a credential optional");
    assertTrue(operation.path("description").asText().contains("A credential is optional."),
        operation.path("description").asText());
    JsonNode responses = operation.path("responses");
    assertFalse(responses.has("401") || responses.has("403"),
        "an anonymous route sends neither 401 nor 403: " + responses);
  }

  private static HttpResponse<String> send(String method, String path, String auth) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + SERVER.getLocalPort() + path));
    if (auth != null) {
      request.header("Authorization", auth);
    }
    if (method.equals("POST")) {
      String body = path.endsWith("integrated-search")
          ? "{\"parameterObject\":{\"valueConstraints\":" + CONSTRAINTS + ",\"inputText\":\"cancer\"},\"limit\":10}"
          : "{\"valueConstraints\":" + CONSTRAINTS + ",\"limit\":10}";
      request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
    } else {
      request.GET();
    }
    return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static JsonNode readSpec() throws IOException {
    try (InputStream input = AnonymousBioPortalRoutesTest.class.getResourceAsStream("/assets/swagger-api/swagger.json")) {
      assertNotNull(input, "generated OpenAPI document");
      return JsonMapper.STRICT_MAPPER.readTree(input);
    }
  }

  private static HttpServer startBioPortal() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", AnonymousBioPortalRoutesTest::answer);
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("Could not bind the stub BioPortal", e);
    }
  }

  private static void answer(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    ASKED.incrementAndGet();
    byte[] payload = "{\"errors\":[\"none\"]}".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(404, payload.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(payload);
    }
  }
}
