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
