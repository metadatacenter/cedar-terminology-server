package org.metadatacenter.terms;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.metadatacenter.cedar.terminology.util.Constants.BP_API_BASE;
import static org.metadatacenter.util.json.JsonMapper.STRICT_MAPPER;

/** Exercises the actual BioPortal HTTP/parser/fallback path used by semantic verification. */
@Tag("integration")
class RemoteTermMembershipTest {
  private static final String BASE = "https://example.org/";
  private static final String ONTOLOGY = "{\"ontologies\":[{\"acronym\":\"EX\"}]}";
  private static final String BRANCH = "{\"branches\":[{\"acronym\":\"EX\",\"uri\":\"" + BASE + "root\",\"maxDepth\":1}]}";
  private HttpServer server;
  private String previousBase;
  private TerminologyService remote;
  private volatile int regularStatus = 200;
  private volatile int provisionalStatus = 404;
  private volatile String regularBody;
  private volatile String provisionalBody = "{}";
  private volatile String parentsBody = "[]";

  @BeforeEach void start() throws IOException {
    previousBase = BP_API_BASE;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::answer);
    server.start();
    remote = new TerminologyService("http://127.0.0.1:" + server.getAddress().getPort() + "/", 1000, 1000);
  }

  @AfterEach void stop() {
    if (server != null) server.stop(0);
    BP_API_BASE = previousBase;
  }

  @ParameterizedTest @ValueSource(ints = {401, 403, 429, 500, 502, 503, 504})
  void provisionalNotFoundCannotHideARegularLookupFailure(int status) throws Exception {
    regularStatus = status;
    HTTPException legacy = assertThrows(HTTPException.class, () -> remote.findClass(BASE + "child", "EX", "key"));
    assertEquals(404, legacy.getStatusCode(), "the existing details API retains its fallback status");
    assertEquals(status, ((HTTPException) legacy.getSuppressed()[0]).getStatusCode());
    assertEquals("unavailable", check(ONTOLOGY).status());
  }

  @Test void twoAuthoritativeNotFoundAnswersRemainFailed() throws Exception {
    regularStatus = 404;
    assertEquals("failed", check(ONTOLOGY).status());
  }

  @ParameterizedTest @ValueSource(strings = {"EX", "http://data.bioontology.org/ontologies/EX", "https://data.bioontology.org/ontologies/EX"})
  void provisionalSuccessRequiresTheRequestedSource(String source) throws Exception {
    regularStatus = 404;
    provisionalStatus = 200;
    provisionalBody = STRICT_MAPPER.createObjectNode().put("@id", BASE + "child")
        .put("ontology", source).put("label", "An irrelevant label").toString();
    assertEquals("passed", check(ONTOLOGY).status());
  }

  @ParameterizedTest @ValueSource(strings = {"OTHER", "", "http://data.bioontology.org/ontologies/OTHER", "https://unrelated.example/ontologies/EX"})
  void provisionalSourceMismatchIsUnverified(String source) throws Exception {
    regularStatus = 404;
    provisionalStatus = 200;
    provisionalBody = STRICT_MAPPER.createObjectNode().put("@id", BASE + "child")
        .put("ontology", source).put("label", "An irrelevant label").toString();
    assertEquals("unavailable", check(ONTOLOGY).status());
  }

  @Test void aProvisionalAnswerWithoutSourceIdentityIsUnverified() throws Exception {
    regularStatus = 404;
    provisionalStatus = 200;
    provisionalBody = "{\"@id\":\"" + BASE + "child\",\"label\":\"An irrelevant label\"}";
    assertEquals("unavailable", check(ONTOLOGY).status());
  }

  @ParameterizedTest @ValueSource(strings = {"{}", "null", "[]", "{\"links\":{}}"})
  void unreadableSuccessfulClassAnswersAreUnavailable(String body) throws Exception {
    regularBody = body;
    assertEquals("unavailable", check(ONTOLOGY).status());
  }

  @Test void aRegularClassFromAnotherSourceIsNotProof() throws Exception {
    regularBody = concept(BASE + "child", "OTHER");
    assertEquals("unavailable", check(ONTOLOGY).status());
  }

  @ParameterizedTest @ValueSource(strings = {"{}", "null", "42", "\"[]\"", "[null]", "[{}]", "{\"collection\":[]}"})
  void malformedHierarchyIsUnavailableRatherThanAnEmptyParentList(String body) throws Exception {
    parentsBody = body;
    assertEquals("unavailable", check(BRANCH).status());
  }

  @Test void aParentWithoutIdentityOrFromAnotherSourceDoesNotProveMembership() throws Exception {
    parentsBody = "[{\"@id\":\"" + BASE + "root\"}]";
    assertEquals("unavailable", check(BRANCH).status());
    parentsBody = "[" + concept(BASE + "root", "OTHER") + "]";
    assertEquals("unavailable", check(BRANCH).status());
  }

  @Test void realEmptyHierarchyFailsAndAnExactParentPasses() throws Exception {
    assertEquals("failed", check(BRANCH).status());
    parentsBody = "[" + concept(BASE + "root", "EX") + "]";
    assertEquals("passed", check(BRANCH).status());
  }

  private TermVerification check(String constraints) throws Exception {
    return new TermMembershipVerifier(null, remote).verify(BASE + "child",
        STRICT_MAPPER.readValue(constraints, ValueConstraints.class), "key");
  }

  private static String concept(String iri, String source) {
    var result = STRICT_MAPPER.createObjectNode().put("@id", iri).put("prefLabel", "Label ignored");
    result.putObject("links").put("ontology", source);
    return result.toString();
  }

  private void answer(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    boolean provisional = path.startsWith("/provisional_classes/");
    int status = provisional ? provisionalStatus : regularStatus;
    String body = provisional ? provisionalBody : path.endsWith("/parents") ? parentsBody
        : regularBody != null ? regularBody : concept(BASE + (path.contains("root") ? "root" : "child"), "EX");
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) { output.write(bytes); }
    exchange.close();
  }
}
