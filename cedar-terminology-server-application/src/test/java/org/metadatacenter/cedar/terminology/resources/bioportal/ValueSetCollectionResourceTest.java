package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.junit.*;
import org.metadatacenter.terms.domainObjects.ValueSetCollection;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class ValueSetCollectionResourceTest extends AbstractTerminologyServerResourceTest {

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUp() {
  }

  /**
   * Sets up the test fixture.
   * (Called before every test case method.)
   */
  @Before
  public void setUp() {
  }

  /**
   * Tears down the test fixture.
   * (Called after every test case method.)
   */
  @After
  public void tearDown() {
  }

  @Test
  public void findAllVSCollectionsTest() {
    String url = baseUrlBpVSCollections;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the results returned
    List<ValueSetCollection> vsc = response.readEntity(new GenericType<List<ValueSetCollection>>() {
    });
    Assert.assertTrue("No ontologies returned", vsc.size() > 0);
    // Check that the CEDARVS collection is included into the results
    String sampleVsc = "CEDARVS";
    boolean found = false;
    for (ValueSetCollection c : vsc) {
      if (c.getId().equals(sampleVsc)) {
        found = true;
        break;
      }
    }
    Assert.assertTrue("Expected value set collection not found in the results (" + sampleVsc + ")", found);
  }

}
