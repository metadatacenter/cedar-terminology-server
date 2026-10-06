package org.metadatacenter.cedar.terminology.resources;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.metadatacenter.util.test.TestAuthUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BioPortal answered in every way it can answer, behind one route for each kind of call the
 * terminology server makes to it.
 *
 * <p>A status BioPortal gives already had a rule, which {@link RelayedFailureBodyTest} pins: 404 and
 * 400 are the caller's answer, and the rest is a 502. A call that got no status had none. Thirty-one
 * routes caught the IOException and answered 500, so a BioPortal that did not answer and one whose
 * answer could not be read both reported this server as having failed. The first is an outage of the
 * service behind this one, a 503, and the second is a gateway's 502. Seven more read BioPortal through
 * the cache, which wraps the same failures in an ExecutionException, and answered 500 for it too.
 */
public class BioPortalAnswerMatrixTest {

  private static final HttpServer BIOPORTAL = startBioPortal();

  /** A BioPortal answer: a status and a body, or no answer at all. */
  private enum Answer {
    UNREADABLE(200, "<html><body>Maintenance</body></html>"), BAD_REQUEST(400, "{\"errors\":[\"bad\"]}"),
    UNAUTHORIZED(401, "{\"errors\":[\"key\"]}"), NOT_FOUND(404, "{\"errors\":[\"none\"]}"),
    TOO_MANY_REQUESTS(429, "{\"errors\":[\"slow\"]}"), SERVER_ERROR(500, "<html>Oops</html>"),
    UNAVAILABLE(503, "<html>Down</html>"), GATEWAY_TIMEOUT(504, "<html>Timed out</html>"), NO_ANSWER(0, null);

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
        case NO_ANSWER -> 503;
        default -> 502;
      };
    }
  }

  private static final String CLASS = URLEncoder.encode("http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C12345",
      StandardCharsets.UTF_8);

  /**
   * One route for each BioPortal call: a class, its children, tree and parents, a property, a search,
   * and an ontology and its roots, which go through the cache.
   */
  private static final List<String> ROUTES = List.of(
      "/bioportal/ontologies/NCIT",
      "/bioportal/ontologies/NCIT/classes/roots",
      "/bioportal/ontologies/NCIT/classes/" + CLASS,
      "/bioportal/ontologies/NCIT/classes/" + CLASS + "/children",
      "/bioportal/ontologies/NCIT/classes/" + CLASS + "/tree",
      "/bioportal/ontologies/NCIT/classes/" + CLASS + "/parents",
      "/bioportal/ontologies/NCIT/properties/" + CLASS,
      "/bioportal/search?q=cancer&scope=classes&sources=NCIT");

  private static final AtomicReference<Answer> ANSWER = new AtomicReference<>(Answer.SERVER_ERROR);
  private static final AtomicInteger ASKED = new AtomicInteger();

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

  static Stream<Arguments> cases() {
    List<Arguments> cases = new ArrayList<>();
    for (String route : ROUTES) {
      for (Answer answer : Answer.values()) {
        cases.add(Arguments.of(route, answer));
      }
    }
    return cases.stream();
  }

  @ParameterizedTest(name = "{0}, BioPortal answering {1}")
  @MethodSource("cases")
  public void theRouteAnswersAsTheGatewayItIs(String route, Answer answer) throws Exception {
    ANSWER.set(answer);
    ASKED.set(0);
    HttpResponse<String> response = CLIENT.send(HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + SERVER.getLocalPort() + route))
        .header("Authorization", authHeader).GET().build(), HttpResponse.BodyHandlers.ofString());

    assertTrue(ASKED.get() > 0, "the route did not ask BioPortal, so it tests nothing: " + response.body());
    assertEquals(answer.relayed(), response.statusCode(), route + " answered " + response.body());
    assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
        "the answer is the JSON error envelope: " + response.headers().map());
  }

  @Test
  public void noBioPortalRouteAnswersAnIOExceptionAsItsOwnFailure() throws Exception {
    Path resources = Path.of("src/main/java/org/metadatacenter/cedar/terminology/resources/bioportal");
    if (!Files.isDirectory(resources)) {
      return; // running from a different working directory; the matrix still holds
    }
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> files = Files.walk(resources)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file);
        if (source.matches("(?s).*catch \\((IOException|ExecutionException)[^)]*\\) \\{\\s*throw new CedarProcessingException\\(e\\);.*")) {
          offenders.add(file.getFileName().toString());
        }
      }
    }
    assertEquals(List.of(), offenders, "these still answer a BioPortal that did not answer as a 500");
  }

  private static HttpServer startBioPortal() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", BioPortalAnswerMatrixTest::answer);
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
    byte[] payload = answer.body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", answer.body.startsWith("<") ? "text/html" : "application/json");
    exchange.sendResponseHeaders(answer.status, payload.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(payload);
    }
  }
}
