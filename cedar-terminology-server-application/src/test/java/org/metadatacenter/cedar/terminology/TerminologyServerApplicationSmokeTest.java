package org.metadatacenter.cedar.terminology;

import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.resources.bioportal.ValueResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.ValueSetResource;
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
      new DropwizardTestSupport<>(TerminologyServerApplication.class,
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

  /**
   * Terminology ships an API spec, so it advertises the documentation links and serves the document.
   *
   * <p>The counterpart to {@code RepoServerApplicationSmokeTest.noApiDocumentationIsAdvertisedOrServed}:
   * the same shared library code gates both, and gating it wrongly would silence a service that does
   * have documentation just as easily as it would quieten one that does not.
   */
  @Test
  public void theApiSpecIsAdvertisedAndServed() throws Exception {
    String index = get("/").body();
    Assertions.assertTrue(index.contains("apiDocs"), "A service with a spec should advertise it: " + index);
    Assertions.assertTrue(index.contains("/swagger-api/swagger.json"),
        "The advertised links should name the spec: " + index);

    HttpResponse<String> spec = get("/swagger-api/swagger.json");
    Assertions.assertEquals(200, spec.statusCode(), "The advertised spec path should serve the document");
    Assertions.assertTrue(spec.body().contains("openapi"),
        "The document served should be an OpenAPI spec");
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

  /** The terminology resource classes Jersey actually registered, less the index resource. */
  private static List<Class<?>> registeredResources() {
    org.glassfish.jersey.server.ResourceConfig resourceConfig =
        SERVER.getEnvironment().jersey().getResourceConfig();
    List<Object> registeredComponents = new ArrayList<>();
    registeredComponents.addAll(resourceConfig.getInstances());
    registeredComponents.addAll(resourceConfig.getSingletons());
    registeredComponents.addAll(resourceConfig.getClasses());
    registeredComponents.addAll(resourceConfig.getResources());
    return RouteSurface.registeredResourceClasses(
            registeredComponents, "org.metadatacenter.cedar.terminology.resources").stream()
        .filter(resourceClass -> !resourceClass.getSimpleName().equals("IndexResource"))
        .toList();
  }

  @Test
  public void runtimeRegistrationInventoryIncludesEveryTerminologyEndpoint() {
    List<Class<?>> registeredResources = registeredResources();

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

  /**
   * The routes that answer an anonymous caller rather than refusing one.
   *
   * <p>{@code POST /search} and {@code GET /search/hierarchy} are unauthenticated by design. The two
   * integrated routes lost their assertion to a {@code //TODO} in the resource. The class
   * descendants route and the BioPortal search build an anonymous request context explicitly, so
   * they read as decisions rather than omissions, but they are the only two of their siblings that
   * do — the other five class routes and {@code property_search} all assert {@code LoggedIn}. All
   * six are recorded on the backend roadmap.
   *
   * <p>Four of them are exercised elsewhere in the default build: the three search routes by
   * {@code LocalStoreResourceTest} and integrated-retrieve by
   * {@link #theIntegratedRetrieveRouteIsReachable()}. The remaining two would have to reach
   * BioPortal to answer at all, so here they are only asserted registered; their behaviour is
   * covered by {@code ClassResourceTest} and {@code SearchResourceTest} under the bioportal tag.
   */
  private static final java.util.Set<String> UNAUTHENTICATED_ROUTES = java.util.Set.of(
      "POST /search",
      "GET /search/hierarchy",
      "POST /bioportal/integrated-search",
      "POST /bioportal/integrated-retrieve",
      "GET /bioportal/search",
      "GET /bioportal/ontologies/{ontology}/classes/{id}/descendants");

  /**
   * Every terminology route that declares an authentication gate stops an anonymous caller at it.
   *
   * <p>Most behavioural tests of these resources exercise live BioPortal and are excluded from the
   * default build, which left the greater part of the route surface receiving no HTTP request from
   * any test that ordinarily runs. The probe reaches only as far as the logged-in assertion, so it
   * needs no network access, and it catches what a refactor breaks: a resource dropped from
   * registration, a path that stopped matching, a verb that changed, or a gate that was lost.
   * {@code findValueSetByValue} went without its gate long enough to be found by an audit rather
   * than by a test, which is the case this probe exists to make impossible.
   *
   * <p>The route list comes from Jersey's own registration, so a resource added to the application
   * is probed without anyone remembering to add it here.
   */
  @Test
  public void everyTerminologyRouteRequiresAuthentication() {
    List<RouteSurface.Endpoint> registered = RouteSurface.endpoints(registeredResources());
    List<String> keys = registered.stream().map(RouteSurface.Endpoint::key).toList();
    Assertions.assertTrue(keys.containsAll(UNAUTHENTICATED_ROUTES),
        "The exclusion list names a route that is no longer registered: " + UNAUTHENTICATED_ROUTES);

    List<RouteSurface.Endpoint> gated = registered.stream()
        .filter(endpoint -> !UNAUTHENTICATED_ROUTES.contains(endpoint.key()))
        // property_search declares @NotEmpty on its query, and bean validation answers 400 before
        // the method body reaches the gate. It is probed with a query below instead.
        .filter(endpoint -> !endpoint.key().equals("GET /bioportal/property_search"))
        .toList();
    Assertions.assertTrue(gated.size() >= 33,
        "the gated terminology surface should be nearly the whole route list: " + gated);
    RouteSurface.assertEveryRouteAnswers("http://localhost:" + SERVER.getLocalPort(), gated, 401);
  }

  /**
   * The property search refuses an anonymous caller once its query passes bean validation.
   *
   * <p>Without {@code q} it answers 400 before the gate is reached, which says nothing about
   * authentication, so the probe above cannot cover it.
   */
  @Test
  public void thePropertySearchRouteIsGuarded() throws Exception {
    HttpResponse<String> response = get("/bioportal/property_search?q=title");
    Assertions.assertEquals(401, response.statusCode());
  }

}
