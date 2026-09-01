package org.metadatacenter.terms.util;

import org.junit.jupiter.api.Test;

import javax.xml.ws.http.HTTPException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The status the caller receives is the status BioPortal gave, unchanged.
 *
 * <p>What this records is the evidence, not a new status: the terminology server relays BioPortal's
 * code as its own, and every correction to that is a client-visible change. So the one thing worth
 * pinning is that adding the log left the relayed status exactly where it was.
 */
public class BioPortalFailureTest {

  @Test
  public void relayCarriesTheUpstreamStatusUnchanged() {
    for (int status : new int[] {400, 401, 403, 404, 429, 500, 502, 503}) {
      HTTPException relayed = BioPortalFailure.relay(status, "https://data.bioontology.org/ontologies/DOID");
      assertEquals(status, relayed.getStatusCode(),
          "the relayed exception must carry BioPortal's own status");
    }
  }

  @Test
  public void aSuccessStatusIsRelayedToo() {
    // Two provisional-class calls throw unconditionally and reach here with the 204 that means the
    // work went through. The status has to survive that path as well.
    assertEquals(204, BioPortalFailure.relay(204, "https://data.bioontology.org/provisional_classes/x")
        .getStatusCode());
  }
}
