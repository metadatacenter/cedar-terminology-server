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
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.util.dw.AnonymousAccess;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exact set of routes that answer a caller holding no credential.
 *
 * <p>Every operation the spec lists is sent once with no credential, its path and required query
 * parameters filled with placeholders. A route that refuses with 401 is gated; any other answer means the
 * request got past whatever gate the route has. The set is asserted whole, so a route that opens or closes
 * shows up as a diff of this list.
 *
 * <p>No terminology POST route is gated, so a POST body that fails validation cannot hide a gate. A gated
 * GET checks the credential before it reads any parameter its placeholders could fail.
 */
@Tag("integration")
public class AnonymousRouteSetTest {

  private static final HttpServer BIOPORTAL = startBioPortal();

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

  /**
   * Every route an anonymous caller is answered by. A change here is a change to who can reach the server.
   * Each is served by a method annotated {@code @AnonymousAccess}, which the second test checks.
   */
  private static final TreeSet<String> ANONYMOUS_ROUTES = new TreeSet<>(List.of(
      "GET /",
      "GET /bioportal/ontologies/{acronym}/classes/{id}/descendants",
      "GET /bioportal/search",
      "GET /properties",
      "GET /properties/hierarchy",
      "GET /properties/roots",
      "GET /properties/versions",
      "GET /resources/icons/bioportal.png",
      "GET /search/hierarchy",
      "POST /bioportal/integrated-retrieve",
      "POST /bioportal/integrated-search",
      "POST /bioportal/validate-term",
      "POST /properties/search",
      "POST /resources/description",
      "POST /search"));

  @BeforeAll
  public static void setUp() throws Exception {
    SERVER.before();
  }

  @AfterAll
  public static void tearDown() {
    SERVER.after();
    BIOPORTAL.stop(0);
  }

  @Test
  public void exactlyTheseRoutesAnswerAnAnonymousCaller() throws Exception {
    TreeSet<String> anonymous = new TreeSet<>();
    TreeMap<String, Integer> statuses = new TreeMap<>();
    JsonNode paths = readSpec().path("paths");
    for (Iterator<Map.Entry<String, JsonNode>> p = paths.fields(); p.hasNext(); ) {
      Map.Entry<String, JsonNode> path = p.next();
      for (Iterator<Map.Entry<String, JsonNode>> o = path.getValue().fields(); o.hasNext(); ) {
        Map.Entry<String, JsonNode> operation = o.next();
        String method = operation.getKey().toUpperCase();
        String route = method + " " + path.getKey();
        int status = send(method, path.getKey(), operation.getValue()).statusCode();
        statuses.put(route, status);
        if (status != 401) {
          anonymous.add(route);
        }
      }
    }
    assertEquals(ANONYMOUS_ROUTES, anonymous, "status of every route sent anonymously: " + statuses);
  }

  @Test
  public void everyAnonymousRouteDeclaresIt() {
    Map<String, Method> handlers = new TreeMap<>();
    List<Class<?>> resources = new java.util.ArrayList<>(SERVER.getEnvironment().jersey().getResourceConfig().getClasses());
    for (Object instance : SERVER.getEnvironment().jersey().getResourceConfig().getInstances()) {
      resources.add(instance.getClass());
    }
    for (Class<?> resource : resources) {
      Path base = resource.getAnnotation(Path.class);
      if (base == null) {
        continue;
      }
      for (Method method : resource.getMethods()) {
        String verb = httpMethod(method);
        if (verb != null) {
          Path sub = method.getAnnotation(Path.class);
          handlers.put(verb + " " + route(base.value(), sub == null ? "" : sub.value()), method);
        }
      }
    }
    for (String route : ANONYMOUS_ROUTES) {
      Method handler = handlers.get(route);
      assertNotNull(handler, "no handler found for " + route + " among " + handlers.keySet());
      assertTrue(handler.isAnnotationPresent(AnonymousAccess.class),
          route + " answers an anonymous caller, so " + handler + " must declare @AnonymousAccess");
    }
  }

  private static String httpMethod(Method method) {
    for (Annotation annotation : method.getAnnotations()) {
      HttpMethod verb = annotation.annotationType().getAnnotation(HttpMethod.class);
      if (verb != null) {
        return verb.value();
      }
    }
    return null;
  }

  private static String route(String base, String sub) {
    String joined = ("/" + base + "/" + sub).replaceAll("/+", "/");
    return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
  }

  private static HttpResponse<String> send(String method, String template, JsonNode operation) throws Exception {
    String path = template.replaceAll("\\{[^}]+}", "x");
    StringJoiner query = new StringJoiner("&", "?", "").setEmptyValue("");
    for (JsonNode parameter : operation.path("parameters")) {
      if ("query".equals(parameter.path("in").asText()) && parameter.path("required").asBoolean()) {
        query.add(parameter.path("name").asText() + "=" + URLEncoder.encode(placeholder(parameter), StandardCharsets.UTF_8));
      }
    }
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + SERVER.getLocalPort() + path + query));
    if (method.equals("GET")) {
      request.GET();
    } else {
      request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString("{}"));
    }
    return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static String placeholder(JsonNode parameter) {
    return switch (parameter.path("schema").path("type").asText()) {
      case "integer", "number" -> "1";
      case "boolean" -> "false";
      default -> "cancer";
    };
  }

  private static JsonNode readSpec() throws IOException {
    try (InputStream input = AnonymousRouteSetTest.class.getResourceAsStream("/assets/swagger-api/swagger.json")) {
      assertNotNull(input, "generated OpenAPI document");
      return JsonMapper.STRICT_MAPPER.readTree(input);
    }
  }

  private static HttpServer startBioPortal() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", AnonymousRouteSetTest::answer);
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException("Could not bind the stub BioPortal", e);
    }
  }

  private static void answer(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    byte[] payload = "{\"errors\":[\"none\"]}".getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(404, payload.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(payload);
    }
  }
}
