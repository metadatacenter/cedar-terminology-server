package org.metadatacenter.cedar.terminology;

import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.util.test.RouteSurface;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Boots the real application in test mode through the Dropwizard test rule and exercises the
 * wiring no backend is needed for: the index endpoint and the authentication gate. Unlike the
 * BioPortal resource tests, this runs without network access to BioPortal, so it catches
 * configuration and startup rot even where the full suite can not run.
 */
public class TerminologyServerApplicationSmokeTest {

  static {
    // Must run before the test support boots the server, which reads the port env vars.
    // OS-assigned server ports, so the test instance never collides with a running dev server.
    java.util.Map<String, String> environment =
        new java.util.HashMap<>(org.metadatacenter.config.environment.CedarEnvironmentSource.getAll());
    environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_STOP_PORT", "0");
    org.metadatacenter.config.environment.CedarEnvironmentSource.setOverride(environment);
  }

  public static final DropwizardTestSupport<TerminologyServerConfiguration> SERVER =
      new DropwizardTestSupport<>(TerminologyServerApplicationTest.class,
          ResourceHelpers.resourceFilePath("test-config.yml"));

  @BeforeAll
  public static void startServer() throws Exception {
    SERVER.before();
  }

  @AfterAll
  public static void stopServer() {
    SERVER.after();
  }

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + SERVER.getLocalPort() + path))
        .GET()
        .build();
    return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, String body) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + SERVER.getLocalPort() + path))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  public void indexIsServed() throws Exception {
    HttpResponse<String> response = get("/");
    Assertions.assertEquals(200, response.statusCode());
  }

  @Test
  public void unauthenticatedBioPortalRequestIsRejected() throws Exception {
    // The logged-in assertion runs before any BioPortal call, so this needs no network access
    HttpResponse<String> response = get("/bioportal/ontologies");
    Assertions.assertEquals(401, response.statusCode());
  }

  /**
   * The relation and integrated-retrieve routes answer, and answer with the authentication gate.
   *
   * <p>Every other test of these two resources exercises live BioPortal and is excluded from the
   * default build, so neither received an HTTP request from any test that ordinarily runs: a
   * resource dropped from registration, or a path that stopped matching, would have shown up
   * nowhere. Both checks stop at the credential, so neither needs the network.
   */
  @Test
  public void theRelationRouteIsReachableAndGuarded() throws Exception {
    HttpResponse<String> response = get("/bioportal/relations/some-relation-id");
    Assertions.assertEquals(401, response.statusCode());
  }

  /**
   * Integrated retrieve is registered and matches its path.
   *
   * <p>This asserts reachability rather than a status. The route's authentication is commented out
   * behind a {@code //TODO} in {@code IntegratedRetrieveResource} — as it is in
   * {@code IntegratedSearchResource} — so an anonymous caller is answered rather than refused.
   * Asserting the 401 the OpenAPI on this method documents would fail today; asserting the 200 it
   * actually returns would write the missing gate into the suite as though it were intended. Both
   * are recorded on the backend roadmap instead.
   */
  @Test
  public void theIntegratedRetrieveRouteIsReachable() throws Exception {
    String body = "{\"valueConstraints\":{\"ontologies\":[],\"valueSets\":[],\"classes\":[],\"branches\":[]},"
        + "\"page\":1,\"pageSize\":10}";
    HttpResponse<String> response = post("/bioportal/integrated-retrieve", body);
    Assertions.assertNotEquals(404, response.statusCode(),
        "POST /bioportal/integrated-retrieve did not match a registered route");
    Assertions.assertNotEquals(405, response.statusCode(),
        "POST /bioportal/integrated-retrieve is registered under a different method");
  }

  @Test
  public void runtimeRegistrationInventoryIncludesEveryTerminologyEndpoint() {
    org.glassfish.jersey.server.ResourceConfig resourceConfig =
        SERVER.getEnvironment().jersey().getResourceConfig();
    List<Object> registeredComponents = new ArrayList<>();
    registeredComponents.addAll(resourceConfig.getInstances());
    registeredComponents.addAll(resourceConfig.getSingletons());
    registeredComponents.addAll(resourceConfig.getClasses());
    registeredComponents.addAll(resourceConfig.getResources());
    List<Class<?>> registeredResources = RouteSurface.registeredResourceClasses(
            registeredComponents, "org.metadatacenter.cedar.terminology.resources").stream()
        .filter(resourceClass -> !resourceClass.getSimpleName().equals("IndexResource"))
        .toList();

    Assertions.assertTrue(registeredResources.size() >= 11,
        "the runtime-derived inventory should include all registered terminology resources: "
            + registeredResources);
    List<RouteSurface.Endpoint> endpoints = RouteSurface.endpoints(registeredResources);
    Assertions.assertTrue(endpoints.size() >= 40,
        "the runtime-derived inventory should include the complete terminology route surface: "
            + endpoints);
    Assertions.assertEquals(endpoints.size(), endpoints.stream().map(RouteSurface.Endpoint::key).distinct().count(),
        "registered terminology routes must have unique verb/path identities");
  }

}
