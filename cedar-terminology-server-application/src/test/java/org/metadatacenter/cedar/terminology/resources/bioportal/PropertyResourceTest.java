package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.junit.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyProperty;
import org.metadatacenter.terms.util.Util;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.BP_PROPERTIES;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class PropertyResourceTest extends AbstractTerminologyServerResourceTest {

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

  // TODO: test regular classes
  @Test
  public void findPropertyTest() {
    // First we do a search to retrieve some properties and pick the first one. Then we try to retrieve it by id
    String q = "title";
    PagedResults<OntologyProperty> properties = searchProperties(q);
    Assert.assertTrue("No properties found to perform the test", properties.getCollection().size() > 0);
    // Pick the first one
    OntologyProperty p = properties.getCollection().get(0);
    // Find it by id
    String encodedPropertyId = null;
    try {
      encodedPropertyId = URLEncoder.encode(p.getId(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String url = baseUrlBpOntologies + "/" + Util.getShortIdentifier(p.getSource()) + "/" + BP_PROPERTIES + "/" + encodedPropertyId;
    // Service invocation
    Response findResponse = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the element retrieved
    OntologyProperty found = findResponse.readEntity(OntologyProperty.class);
    // Check id
    Assert.assertTrue("Wrong property id: " + found.getId(), p.getId().equals(found.getId()));
  }

  @Test
  public void findAllPropertiesForOntologyTest() {
    String ontology = "BIBFRAME";
    int approxPropertiesCount = 480;
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_PROPERTIES;
    // Service invocation
    Response findResponse = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    List<OntologyProperty> properties = findResponse.readEntity(new GenericType<List<OntologyProperty>>() {});
    Assert.assertTrue("The number of properties found (" + properties.size() + ") is lower than expected (" +
        approxPropertiesCount + ")", properties.size() >= approxPropertiesCount);
    Assert.assertTrue("The number of properties found (" + properties.size() + ") is higher than expected (" +
        approxPropertiesCount + ")", properties.size() < approxPropertiesCount * 2);
  }

}
