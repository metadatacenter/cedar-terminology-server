package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.junit.*;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyClass;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class OntologyResourceTest extends AbstractTest {

  private static Ontology ontology1;

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUp() {
    // Wait while cache is being generated
    while (Cache.ontologiesCache.size() == 0) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
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
    Response response = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns the expected parent
    List<Ontology> ontologies = response.readEntity(new GenericType<List<Ontology>>() {});
    Assert.assertTrue("No ontologies returned", ontologies.size() > 0);
    Assert.assertTrue("Wrong number of ontologies returned", ontologies.size() > 525);
  }

  @Test
  public void findOntologyTest() {
    String url = baseUrlBpOntologies + "/" + ontology1.getId();
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).get();
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
    Response response = client.target(url).request().header("Authorization", authHeader).get();
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



}
