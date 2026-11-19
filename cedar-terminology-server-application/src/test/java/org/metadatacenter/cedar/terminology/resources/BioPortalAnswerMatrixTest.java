package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.terms.util.HttpUtil;
import org.metadatacenter.util.test.RouteSurface;
import org.metadatacenter.util.test.TestAuthUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BioPortal answered in every way it can answer, behind every route the terminology server answers from it.
 *
 * <p>The routes are the registered ones: {@link #theMatrixCoversEveryBioPortalRoute()} holds {@link #REQUESTS} to the
 * routes Jersey registered under {@code /bioportal}, so a route added to the application fails that test until it is
 * given a request here. A BioPortal that stops answering is an outage too: {@link #aStalledBioPortalIsAnOutage}
 * shortens the response timeout and expects a 503 well before the stall ends.
 *
 * <p>A status BioPortal gives already had a rule, which {@link RelayedFailureBodyTest} pins: 404 and
 * 400 are the caller's answer, and the rest is a 502. A call that got no status had none. Thirty-one
 * routes caught the IOException and answered 500, so a BioPortal that did not answer and one whose
 * answer could not be read both reported this server as having failed. The first is an outage of the
 * service behind this one, a 503, and the second is a gateway's 502. Seven more read BioPortal through
 * the cache, which wraps the same failures in an ExecutionException, and answered 500 for it too.
 */
@Tag("integration")
public class BioPortalAnswerMatrixTest {

  private static final HttpServer BIOPORTAL = startBioPortal();

  /** A BioPortal answer: a status and a body, or no answer at all. */
  private enum Answer {
    UNREADABLE(200, "<html><body>Maintenance</body></html>"), BAD_REQUEST(400, "{\"errors\":[\"bad\"]}"),
    UNAUTHORIZED(401, "{\"errors\":[\"key\"]}"), NOT_FOUND(404, "{\"errors\":[\"none\"]}"),
    TOO_MANY_REQUESTS(429, "{\"errors\":[\"slow\"]}"), SERVER_ERROR(500, "<html>Oops</html>"),
    UNAVAILABLE(503, "<html>Down</html>"), GATEWAY_TIMEOUT(504, "<html>Timed out</html>"), NO_ANSWER(0, null),
    STALLED(200, "{}");

    final int status;
    final String body;

    Answer(int status, String body) {
      this.status = status;
      this.body = body;
    }

    /** What the terminology server answers when BioPortal answers this. */
    int relayed() {
      return switch (this) {
        case BAD_REQUEST, NOT_FOUND -> status;
        case NO_ANSWER, STALLED -> 503;
        default -> 502;
      };
    }
  }

  private static final String CLASS = URLEncoder.encode("http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C12345",
      StandardCharsets.UTF_8);

  private static final String ONTOLOGY = "/bioportal/ontologies/NCIT";
  private static final String COLLECTION = "/bioportal/vs-collections/CEDARVS";

  /** A field's constraints naming one ontology, the input of the three POST routes. */
  private static final String CONSTRAINTS = "{\"ontologies\":[{\"acronym\":\"NCIT\",\"uri\":"
      + "\"https://data.bioontology.org/ontologies/NCIT\",\"name\":\"NCIT\"}],\"branches\":[],\"valueSets\":[],"
      + "\"classes\":[],\"multipleChoice\":false}";

  /**
   * A request that reaches BioPortal for each BioPortal route, keyed by the route as Jersey registers it. A GET is its
   * path and query; a POST is its path, a space and its body.
   */
  private static final Map<String, String> REQUESTS = Map.ofEntries(
      Map.entry("GET /bioportal/ontologies", "/bioportal/ontologies"),
      Map.entry("GET /bioportal/ontologies/{id}", ONTOLOGY),
      Map.entry("GET /bioportal/ontologies/{ontology}/classes/roots", ONTOLOGY + "/classes/roots"),
      Map.entry("GET /bioportal/ontologies/{ontology}/properties/roots", ONTOLOGY + "/properties/roots"),
      Map.entry("GET /bioportal/ontologies/{ontology}/classes", ONTOLOGY + "/classes"),
      Map.entry("GET /bioportal/ontologies/{ontology}/classes/{id}", ONTOLOGY + "/classes/" + CLASS),
      Map.entry("GET /bioportal/ontologies/{ontology}/classes/{id}/children", ONTOLOGY + "/classes/" + CLASS + "/children"),
      Map.entry("GET /bioportal/ontologies/{ontology}/classes/{id}/descendants",
          ONTOLOGY + "/classes/" + CLASS + "/descendants"),
      Map.entry("GET /bioportal/ontologies/{ontology}/classes/{id}/parents", ONTOLOGY + "/classes/" + CLASS + "/parents"),
      Map.entry("GET /bioportal/ontologies/{ontology}/classes/{id}/tree", ONTOLOGY + "/classes/" + CLASS + "/tree"),
      Map.entry("GET /bioportal/ontologies/{ontology}/classes/provisional", ONTOLOGY + "/classes/provisional"),
      Map.entry("GET /bioportal/classes/provisional", "/bioportal/classes/provisional"),
      Map.entry("GET /bioportal/ontologies/{ontology}/properties", ONTOLOGY + "/properties"),
      Map.entry("GET /bioportal/ontologies/{ontology}/properties/{id}", ONTOLOGY + "/properties/" + CLASS),
      Map.entry("GET /bioportal/ontologies/{ontology}/properties/{id}/children",
          ONTOLOGY + "/properties/" + CLASS + "/children"),
      Map.entry("GET /bioportal/ontologies/{ontology}/properties/{id}/descendants",
          ONTOLOGY + "/properties/" + CLASS + "/descendants"),
      Map.entry("GET /bioportal/ontologies/{ontology}/properties/{id}/parents",
          ONTOLOGY + "/properties/" + CLASS + "/parents"),
      Map.entry("GET /bioportal/ontologies/{ontology}/properties/{id}/tree", ONTOLOGY + "/properties/" + CLASS + "/tree"),
      Map.entry("GET /bioportal/relations/{id}", "/bioportal/relations/7c2d1e3f"),
      Map.entry("GET /bioportal/search", "/bioportal/search?q=cancer&scope=classes&sources=NCIT"),
      Map.entry("GET /bioportal/property_search", "/bioportal/property_search?q=cancer&sources=NCIT"),
      Map.entry("GET /bioportal/vs-collections", "/bioportal/vs-collections"),
      Map.entry("GET /bioportal/value-sets", "/bioportal/value-sets"),
      Map.entry("GET /bioportal/vs-collections/{vs_collection}/value-sets", COLLECTION + "/value-sets"),
      Map.entry("GET /bioportal/vs-collections/{vs_collection}/value-sets/{id}", COLLECTION + "/value-sets/" + CLASS),
      Map.entry("GET /bioportal/vs-collections/{vs_collection}/value-sets/{id}/tree",
          COLLECTION + "/value-sets/" + CLASS + "/tree"),
      Map.entry("GET /bioportal/vs-collections/{vs_collection}/value-sets/{vs}/values",
          COLLECTION + "/value-sets/" + CLASS + "/values"),
      Map.entry("GET /bioportal/vs-collections/{vs_collection}/values/{id}", COLLECTION + "/values/" + CLASS),
      Map.entry("GET /bioportal/vs-collections/{vs_collection}/values/{id}/value-set",
          COLLECTION + "/values/" + CLASS + "/value-set"),
      Map.entry("GET /bioportal/vs-collections/{vs_collection}/values/{id}/all-values",
          COLLECTION + "/values/" + CLASS + "/all-values"),
      Map.entry("GET /bioportal/vs-collections/{vs_collection}/values/{id}/tree", COLLECTION + "/values/" + CLASS + "/tree"),
      Map.entry("POST /bioportal/integrated-search", "/bioportal/integrated-search {\"parameterObject\":{\"inputText\":"
          + "\"cancer\",\"valueConstraints\":" + CONSTRAINTS + "},\"page\":1,\"pageSize\":50}"),
      Map.entry("POST /bioportal/integrated-retrieve",
          "/bioportal/integrated-retrieve {\"valueConstraints\":" + CONSTRAINTS + ",\"page\":1,\"pageSize\":50}"));

  /** The routes under {@code /bioportal} that read the local catalog alone and never ask BioPortal. */
  private static final Set<String> LOCAL_ROUTES = Set.of(
      "GET /bioportal/classes/version-current",
      "GET /bioportal/ontologies/{id}/versions",
      "GET /bioportal/ontologies/{id}/versions/current",
      "GET /bioportal/ontologies/{id}/versions/diff",
      "GET /bioportal/vs-collections/version-current");

  /** The term verification route, whose answer to a BioPortal failure is a verdict rather than a status. */
  private static final String VERIFICATION = "POST /bioportal/validate-term";
  /** How long a stalled BioPortal holds a request before it answers. */
  private static final int STALL_MILLIS = 6_000;
  /** The response timeout the stalled cases run under, in place of the configured 30 seconds. */
  private static final int STALLED_TIMEOUT_MILLIS = 300;
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final AtomicReference<Answer> ANSWER = new AtomicReference<>(Answer.SERVER_ERROR);
  private static final AtomicInteger ASKED = new AtomicInteger();

  static {
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "0");
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

  static Stream<Arguments> cases() {
    List<Arguments> cases = new ArrayList<>();
    for (String route : new TreeSet<>(REQUESTS.keySet())) {
      for (Answer answer : Answer.values()) {
        if (answer != Answer.STALLED) {
          cases.add(Arguments.of(route, answer));
        }
      }
    }
    return cases.stream();
  }

  @ParameterizedTest(name = "{0}, BioPortal answering {1}")
  @MethodSource("cases")
  public void theRouteAnswersAsTheGatewayItIs(String route, Answer answer) throws Exception {
    ANSWER.set(answer);
    ASKED.set(0);
    HttpResponse<String> response = send(REQUESTS.get(route));

    assertTrue(ASKED.get() > 0, "the route did not ask BioPortal, so it tests nothing: " + response.body());
    assertEquals(answer.relayed(), response.statusCode(), route + " answered " + response.body());
    assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
        "the answer is the JSON error envelope: " + response.headers().map());
  }

  /**
   * Every route Jersey registered under {@code /bioportal} has a request in the matrix, or reads only the local catalog.
   * Term verification answers a BioPortal failure as a verdict rather than a status, so it has a test of its own.
   */
  @Test
  public void theMatrixCoversEveryBioPortalRoute() {
    org.glassfish.jersey.server.ResourceConfig config = SERVER.getEnvironment().jersey().getResourceConfig();
    List<Object> components = new ArrayList<>();
    components.addAll(config.getInstances());
    components.addAll(config.getSingletons());
    components.addAll(config.getClasses());
    components.addAll(config.getResources());
    Set<String> registered = new TreeSet<>();
    RouteSurface.endpoints(RouteSurface.registeredResourceClasses(components, "org.metadatacenter.cedar.terminology.resources"))
        .stream().map(RouteSurface.Endpoint::key).filter(key -> key.contains(" /bioportal")).forEach(registered::add);
    Set<String> covered = new TreeSet<>(REQUESTS.keySet());
    covered.addAll(LOCAL_ROUTES);
    covered.add(VERIFICATION);
    assertEquals(registered, covered, "a BioPortal route has no request in the matrix, or the matrix names a route "
        + "that is no longer registered");
  }

  /**
   * A BioPortal that accepts the connection and never answers is an outage. With the response timeout shortened, a
   * route answers 503 well before the stall would have ended, for a call made directly and for one made through the
   * cache.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("stalledRoutes")
  public void aStalledBioPortalIsAnOutage(String route) throws Exception {
    CedarConfig cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_TERMINOLOGY));
    HttpUtil.install(cedarConfig.getTerminologyConfig().getBioPortal().getConnectTimeout(), STALLED_TIMEOUT_MILLIS);
    try {
      ANSWER.set(Answer.STALLED);
      ASKED.set(0);
      long started = System.nanoTime();
      HttpResponse<String> response = send(REQUESTS.get(route));
      long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

      assertTrue(ASKED.get() > 0, "the route did not ask BioPortal: " + response.body());
      assertEquals(Answer.STALLED.relayed(), response.statusCode(), route + " answered " + response.body());
      assertTrue(elapsedMillis < STALL_MILLIS / 2, route + " took " + elapsedMillis + " ms, as if it waited out the stall");
    } finally {
      HttpUtil.install(cedarConfig.getTerminologyConfig().getBioPortal().getConnectTimeout(),
          cedarConfig.getTerminologyConfig().getBioPortal().getSocketTimeout());
    }
  }

  static Stream<String> stalledRoutes() {
    return Stream.of("GET /bioportal/ontologies/{ontology}/classes/{id}", "GET /bioportal/ontologies");
  }

  /**
   * Term verification reports a BioPortal it could not read as unavailable, never as a failed membership check, and
   * a class BioPortal does not hold as failed.
   */
  @ParameterizedTest(name = "BioPortal answering {0}")
  @MethodSource("verificationCases")
  public void termVerificationReportsWhatBioPortalCouldNotAnswer(Answer answer) throws Exception {
    ANSWER.set(answer);
    ASKED.set(0);
    HttpResponse<String> response = send("/bioportal/validate-term {\"iri\":\""
        + URLDecoder.decode(CLASS, StandardCharsets.UTF_8) + "\",\"valueConstraints\":" + CONSTRAINTS + "}");

    assertTrue(ASKED.get() > 0, "verification did not ask BioPortal: " + response.body());
    assertEquals(200, response.statusCode(), response.body());
    String expected = answer == Answer.NOT_FOUND ? "failed" : "unavailable";
    assertEquals(expected, JSON.readTree(response.body()).path("status").asText(), response.body());
  }

  static Stream<Answer> verificationCases() {
    return Stream.of(Answer.values()).filter(answer -> answer != Answer.STALLED);
  }

  /** Sends a matrix request: a GET path, or a POST path, a space and its body. */
  private static HttpResponse<String> send(String request) throws Exception {
    int space = request.indexOf(' ');
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + SERVER.getLocalPort() + (space < 0 ? request : request.substring(0, space))))
        .header("Authorization", authHeader);
    if (space < 0) {
      builder.GET();
    } else {
      builder.header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(request.substring(space + 1)));
    }
    return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  public void noBioPortalRouteAnswersAnIOExceptionAsItsOwnFailure() throws Exception {
    List<String> offenders = new ArrayList<>();
    for (Path file : BioPortalResourceSources.files()) {
      String source = Files.readString(file);
      if (source.matches("(?s).*catch \\((IOException|ExecutionException)[^)]*\\) \\{\\s*throw new CedarProcessingException\\(e\\);.*")) {
        offenders.add(file.getFileName().toString());
      }
    }
    assertEquals(List.of(), offenders, "these still answer a BioPortal that did not answer as a 500");
  }

  private static HttpServer startBioPortal() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", BioPortalAnswerMatrixTest::answer);
      // A stalled answer holds its thread, so the others need their own.
      server.setExecutor(Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "stub-bioportal");
        thread.setDaemon(true);
        return thread;
      }));
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("Could not bind the stub BioPortal", e);
    }
  }

  private static void answer(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    ASKED.incrementAndGet();
    Answer answer = ANSWER.get();
    if (answer == Answer.NO_ANSWER) {
      // The connection closes before a status line, as a BioPortal that went down would.
      exchange.close();
      return;
    }
    if (answer == Answer.STALLED) {
      try {
        Thread.sleep(STALL_MILLIS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    byte[] payload = answer.body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", answer.body.startsWith("<") ? "text/html" : "application/json");
    exchange.sendResponseHeaders(answer.status, payload.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(payload);
    }
  }
}
