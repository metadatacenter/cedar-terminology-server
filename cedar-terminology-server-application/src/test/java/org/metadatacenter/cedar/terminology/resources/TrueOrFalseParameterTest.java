package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.TestAuthUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The terminology server's true-or-false parameters accept true and false in any case and refuse every
 * other value, which Jersey used to read as false.
 *
 * <p>These parameters are primitive booleans. Three of them declare no default, so an absent one must
 * still arrive as false, and {@code include_details} declares false. BioPortal is a stub that records
 * what it is asked and answers 404, which the routes relay. A flag that is true adds a term to the
 * BioPortal request, so the request shows the value the route was given. A refused value never
 * reaches BioPortal.
 */
public class TrueOrFalseParameterTest {

  private static final List<String> ASKED = new CopyOnWriteArrayList<>();
  private static final HttpServer BIOPORTAL = startBioPortal();
  /** Each search asks for a different term, so no answer can come from a cache. */
  private static final AtomicInteger QUERY = new AtomicInteger();

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

  @ParameterizedTest
  @ValueSource(strings = {"&suggest=true", "&suggest=TRUE", "&suggest=True"})
  public void suggestTrueInAnyCaseAsksBioPortalForSuggestions(String flag) throws Exception {
    assertTrue(search(flag).contains("&suggest=true"), ASKED.toString());
  }

  /**
   * False in any case and an absent flag both ask for a plain search. The flag declares no default, so
   * an absent one must still be false.
   */
  @ParameterizedTest
  @ValueSource(strings = {"&suggest=false", "&suggest=FALSE", "&suggest=False", ""})
  public void suggestFalseOrAbsentAsksBioPortalForAPlainSearch(String flag) throws Exception {
    assertFalse(search(flag).contains("suggest"), ASKED.toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "1", "yes", ""})
  public void aSuggestValueThatIsNeitherTrueNorFalseIsRefused(String value) throws Exception {
    ASKED.clear();
    assertRefused(get("/bioportal/search?q=cancer&scope=classes&sources=NCIT&suggest=" + value, false), "suggest");
    assertEquals(List.of(), ASKED, "a refused value reached BioPortal");
  }

  @Test
  public void propertySearchFlagsAreConvertedInAnyCase() throws Exception {
    String both = propertySearch("&exact_match=TRUE&require_definitions=True");
    assertTrue(both.contains("&require_exact_match=true"), both);
    assertTrue(both.contains("&require_definitions=true"), both);
    String neither = propertySearch("&exact_match=False&require_definitions=FALSE");
    assertFalse(neither.contains("require_exact_match"), neither);
    assertFalse(neither.contains("require_definitions"), neither);
    String absent = propertySearch("");
    assertFalse(absent.contains("require_exact_match"), absent);
    assertFalse(absent.contains("require_definitions"), absent);
  }

  @ParameterizedTest
  @ValueSource(strings = {"exact_match", "require_definitions"})
  public void aPropertySearchFlagThatIsNeitherTrueNorFalseIsRefused(String parameter) throws Exception {
    for (String value : List.of("abc", "1", "yes", "")) {
      assertRefused(get("/bioportal/property_search?q=title&" + parameter + "=" + value, true), parameter);
    }
  }

  /**
   * The flag declares a default of false. An absent flag keeps it, and a value other than true or false
   * is refused like any other.
   */
  @Test
  public void includeDetailsIsConvertedOrRefused() throws Exception {
    for (String query : List.of("", "?include_details=true", "?include_details=FALSE")) {
      HttpResponse<String> response = get("/bioportal/vs-collections" + query, true);
      assertNotEquals(400, response.statusCode(), query + " answered " + response.body());
    }
    for (String value : List.of("abc", "1", "yes", "")) {
      assertRefused(get("/bioportal/vs-collections?include_details=" + value, true), "include_details");
    }
  }

  /** The BioPortal request a class search made, given the flags the CEDAR request carried. */
  private static String search(String flags) throws Exception {
    ASKED.clear();
    String term = "cancer" + QUERY.incrementAndGet();
    HttpResponse<String> response = get("/bioportal/search?q=" + term + "&scope=classes&sources=NCIT" + flags, false);
    assertEquals(404, response.statusCode(), "BioPortal's 404 is relayed: " + response.body());
    return ASKED.stream().filter(asked -> asked.contains(term)).findFirst()
        .orElseThrow(() -> new AssertionError("the search did not ask BioPortal: " + ASKED));
  }

  /** The BioPortal request a property search made, given the flags the CEDAR request carried. */
  private static String propertySearch(String flags) throws Exception {
    ASKED.clear();
    String term = "title" + QUERY.incrementAndGet();
    HttpResponse<String> response = get("/bioportal/property_search?q=" + term + flags, true);
    assertEquals(404, response.statusCode(), "BioPortal's 404 is relayed: " + response.body());
    return ASKED.stream().filter(asked -> asked.contains(term)).findFirst()
        .orElseThrow(() -> new AssertionError("the property search did not ask BioPortal: " + ASKED));
  }

  private static void assertRefused(HttpResponse<String> response, String parameter) throws Exception {
    assertEquals(400, response.statusCode(), response.body());
    JsonNode error = JsonMapper.STRICT_MAPPER.readTree(response.body());
    assertEquals("BAD_REQUEST", error.path("status").asText(), response.body());
    assertEquals("The query parameter " + parameter + " must be true or false", error.path("message").asText(),
        response.body());
  }

  private static HttpResponse<String> get(String path, boolean authenticated) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + SERVER.getLocalPort() + path));
    if (authenticated) request.header("Authorization", authHeader);
    return CLIENT.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private static HttpServer startBioPortal() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", TrueOrFalseParameterTest::answer);
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("Could not bind the stub BioPortal", e);
    }
  }

  private static void answer(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    ASKED.add(exchange.getRequestURI().toString());
    byte[] payload = "{\"errors\":[\"none\"]}".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(404, payload.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(payload);
    }
  }

}
