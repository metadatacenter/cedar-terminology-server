package org.metadatacenter.cedar.terminology;

import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Boots the real application in test mode through the Dropwizard test rule and exercises the
 * wiring no backend is needed for: the index endpoint and the authentication gate. Unlike the
 * BioPortal resource tests, this runs without network access to BioPortal, so it catches
 * configuration and startup rot even where the full suite can not run.
 */
public class TerminologyServerApplicationSmokeTest {

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

}
