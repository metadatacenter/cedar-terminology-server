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
  @DisplayName("The status is relayed unchanged, so this adds a body and breaks nobody")
  void statusIsUnchanged() throws Exception {
    assertEquals(401, relay(401).getStatus());
    assertEquals(404, relay(404).getStatus());
    assertEquals(500, relay(500).getStatus());
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
  @DisplayName("A status CEDAR does not model still produces a response rather than a null")
  void unmodelledStatusFallsBack() throws Exception {
    Response response = relay(418);
    assertNotNull(response);
    assertTrue(response.getStatus() >= 400);
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
