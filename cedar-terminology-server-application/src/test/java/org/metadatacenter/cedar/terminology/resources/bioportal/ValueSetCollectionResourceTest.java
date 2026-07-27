package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.domainObjects.ValueSetCollection;

import java.util.List;

import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
@Disabled
public class ValueSetCollectionResourceTest extends AbstractTerminologyServerResourceTest {

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeAll
  public static void oneTimeSetUp() {
  }

  /**
   * Sets up the test fixture.
   * (Called before every test case method.)
   */
  @BeforeEach
  public void setUp() {
  }

  /**
   * Tears down the test fixture.
   * (Called after every test case method.)
   */
  @AfterEach
  public void tearDown() {
  }

  @Disabled
  @Test
  public void findAllVSCollectionsTest() {
    String url = baseUrlBpVSCollections;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    response.close();
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the results returned
    List<ValueSetCollection> vsc = response.readEntity(new GenericType<List<ValueSetCollection>>() {
    });
    response.close();
    Assertions.assertTrue( vsc.size() > 0,"No ontologies returned");
    // Check that the CEDARVS collection is included into the results
    String sampleVsc = "CEDARVS";
    boolean found = false;
    for (ValueSetCollection c : vsc) {
      if (c.getId().equals(sampleVsc)) {
        found = true;
        break;
      }
    }
    Assertions.assertTrue( found,"Expected value set collection not found in the results (" + sampleVsc + ")");
  }

}
