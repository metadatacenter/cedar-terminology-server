package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.junit.*;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.OntologyProperty;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
@Ignore
public class OntologyResourceTest extends AbstractTerminologyServerResourceTest {

  private static Ontology ontology1;

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUp() {
    // Initialize ontology information
    ontology1 = new Ontology("NCIT","http://data.bioontology.org/ontologies/",
        "National Cancer Institute Thesaurus", false, null);
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
  public void findAllOntologiesTest() {
    String url = baseUrlBpOntologies;
    // Service invocation
    Response response = client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the results returned
    List<Ontology> ontologies = response.readEntity(new GenericType<List<Ontology>>() {});
    Assert.assertTrue("No ontologies returned", ontologies.size() > 0);
    Assert.assertTrue("Wrong number of ontologies returned", ontologies.size() > 525);
  }

  @Test
  public void findOntologyTest() {
    String url = baseUrlBpOntologies + "/" + ontology1.getId();
    // Service invocation
    Response response = client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns the expected ontology
    Ontology ontology = response.readEntity(Ontology.class);
    Assert.assertEquals("Wrong ontology id", "NCIT", ontology.getId());
    Assert.assertEquals("Wrong ontology name", "National Cancer Institute Thesaurus", ontology.getName());
  }

  @Test
  public void findRootClassesTest() {
    String url = baseUrlBpOntologies + "/" + ontology1.getId() + "/" + BP_CLASSES + "/" + BP_ROOTS;
    // Service invocation
    Response response = client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check results
    List<OntologyClass> roots = response.readEntity(new GenericType<List<OntologyClass>>() {});
    Assert.assertTrue("No roots returned", roots.size() > 0);
    // Basic check to see whether "Biological Process" is found
    String rootId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C17828";
    boolean found = false;
    for (OntologyClass c : roots) {
      if (c.getLdId().equals(rootId)) {
        found = true;
        break;
      }
    }
    Assert.assertTrue("Expected root class not found", found);
  }

  @Test
  public void findRootPropertiesTest() {
    String ontology = "BIBFRAME";
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_PROPERTIES + "/" + BP_ROOTS;
    // Service invocation
    Response response = client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check results
    List<OntologyProperty> roots = response.readEntity(new GenericType<List<OntologyProperty>>() {});
    Assert.assertTrue("No roots returned", roots.size() > 0);
    // Basic check to see if the "Administrative metadata" root property is found
    String rootId = "http://id.loc.gov/ontologies/bibframe/adminMetadata";
    boolean found = false;
    for (OntologyProperty property : roots) {
      if (property.getLdId().equals(rootId)) {
        found = true;
        break;
      }
    }
    Assert.assertTrue("Expected root property not found", found);
  }

}
