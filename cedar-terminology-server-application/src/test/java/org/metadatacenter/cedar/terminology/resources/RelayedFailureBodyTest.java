package org.metadatacenter.cedar.terminology.resources;

import jakarta.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A relayed BioPortal failure carries a body.
 *
 * <p>Thirty-three catch blocks answered a bare status and nothing else, so a caller could not tell that
 * the condition came from BioPortal rather than from CEDAR. The most confusing case is this server's own
 * API key expiring, which reached the client as a bodiless 401 about a credential it does not hold.
 */
class RelayedFailureBodyTest {

  private static Response relay(int upstreamStatus) throws Exception {
    Method m = AbstractTerminologyServerResource.class
        .getDeclaredMethod("relayedBioPortalFailure", HTTPException.class);
    m.setAccessible(true);
    return (Response) m.invoke(null, new HTTPException(upstreamStatus));
  }

  @Test
  @DisplayName("A refused key or an upstream outage becomes 502, not the caller's problem")
  void upstreamFaultsBecomeBadGateway() throws Exception {
    assertEquals(502, relay(401).getStatus(),
        "a 401 here means BioPortal refused CEDAR's key; telling the caller to authenticate is wrong");
    assertEquals(502, relay(403).getStatus());
    assertEquals(502, relay(500).getStatus());
    assertEquals(502, relay(503).getStatus());
  }

  @Test
  @DisplayName("A status that is genuinely the caller's answer is relayed")
  void callerAnswersAreRelayed() throws Exception {
    assertEquals(404, relay(404).getStatus(), "the term really was not found");
    assertEquals(400, relay(400).getStatus(), "usually reflects a parameter the caller supplied");
  }

  @Test
  @DisplayName("The body names BioPortal and the status it gave")
  void bodyNamesTheUpstream() throws Exception {
    Response response = relay(401);
    assertNotNull(response.getEntity(), "the point of the change is that there is a body");
    String rendered = response.getEntity().toString();
    assertTrue(rendered.contains("BioPortal"), "the body should name the upstream: " + rendered);
    assertTrue(rendered.contains("401"), "the body should carry the upstream status: " + rendered);
  }

  @Test
  @DisplayName("A status CEDAR does not model becomes a gateway failure rather than a null")
  void unmodelledStatusFallsBack() throws Exception {
    Response response = relay(418);
    assertNotNull(response);
    assertEquals(502, response.getStatus());
  }

  @Test
  @DisplayName("No resource class relays a bare status any more")
  void noBareRelaysRemain() throws Exception {
    Path resources = Path.of("src/main/java/org/metadatacenter/cedar/terminology/resources/bioportal");
    if (!Files.isDirectory(resources)) {
      return; // running from a different working directory; the other tests still hold
    }
    try (Stream<Path> files = Files.walk(resources)) {
      List<String> offenders = files
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> {
            try {
              return Files.readString(p).contains("Response.status(e.getStatusCode()).build()");
            } catch (Exception e) {
              return false;
            }
          })
          .map(p -> p.getFileName().toString())
          .toList();
      assertFalse(offenders.size() > 0,
          "these still answer a bare relayed status with no body: " + offenders);
    }
  }
}
