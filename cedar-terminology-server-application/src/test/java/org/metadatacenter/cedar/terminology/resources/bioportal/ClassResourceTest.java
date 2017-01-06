package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.junit.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.util.Util;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class ClassResourceTest extends AbstractTest {

  private static OntologyClass class1;
  private static List<OntologyClass> createdClasses;

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUp() {
    // Initialize test class
    String classLabel = "class1_test";
    String classCreator = "http://data.bioontology.org/users/cedar-test";
    String classOntology = "http://data.bioontology.org/ontologies/CEDARPC";
    List classDefinitions = new ArrayList<>();
    classDefinitions.add("classDefinition1");
    classDefinitions.add("classDefinition2");
    List classSynonyms = new ArrayList<>();
    classSynonyms.add("classSynonym1");
    classSynonyms.add("classSynonym2");
    List classRelations = new ArrayList<>();
    class1 = new OntologyClass(null, null, classLabel, classCreator, classOntology, classDefinitions,
        classSynonyms, null, classRelations, true, null, false);
  }

  /**
   * Sets up the test fixture.
   * (Called before every test case method.)
   */
  @Before
  public void setUp() {
    // Ids of test objects
    createdClasses = new ArrayList<>();
  }

  /**
   * Tears down the test fixture.
   * (Called after every test case method.)
   */
  @After
  public void tearDown() {
    try {
      deleteCreatedClasses();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void createClassTest() {
    String url = baseUrlBpOntologies + "/" + Util.getShortIdentifier(class1.getOntology()) + "/" + BP_CLASSES;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).post(Entity.json(class1));
    // Check HTTP response
    Assert.assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check fields
    OntologyClass created = response.readEntity(OntologyClass.class);
    // Store class to delete the class after the test
    createdClasses.add(created);
    OntologyClass expected = class1;
    Assert.assertNotNull(created.getId());
    Assert.assertNotNull(created.getLdId());
    Assert.assertNotNull(created.getCreated());
    Assert.assertEquals(expected.getPrefLabel(), created.getPrefLabel());
    Assert.assertEquals(expected.getCreator(), created.getCreator());
    Assert.assertEquals(expected.getOntology(), created.getOntology());
    Assert.assertEquals(expected.getDefinitions(), created.getDefinitions());
    Assert.assertEquals(expected.getSynonyms(), created.getSynonyms());
    Assert.assertEquals(expected.getSubclassOf(), created.getSubclassOf());
    Assert.assertEquals(expected.getRelations(), created.getRelations());
    Assert.assertEquals(expected.isProvisional(), created.isProvisional());
  }

  // TODO: test regular classes
  @Test
  public void findClassTest() {
    // Create a provisional class
    OntologyClass created = createClass();
    // Find the provisional class by id
    String classUrl = baseUrlBpOntologies + "/" + Util.getShortIdentifier(class1.getOntology()) + "/" + BP_CLASSES + "/" + created.getId();
    // Service invocation
    Response findResponse = client.target(classUrl).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the element retrieved
    OntologyClass found = findResponse.readEntity(OntologyClass.class);
    // Check fields
    Assert.assertEquals(created.getId(), found.getId());
    Assert.assertEquals(created.getLdId(), found.getLdId());
    Assert.assertEquals(created.getPrefLabel(), found.getPrefLabel());
    Assert.assertEquals(created.getCreator(), found.getCreator());
    Assert.assertEquals(created.getOntology(), found.getOntology());
    // Convert list to set because order is irrelevant
    Assert.assertEquals(new HashSet<>(created.getDefinitions()), new HashSet<>(found.getDefinitions()));
    Assert.assertEquals(new HashSet<>(created.getSynonyms()), new HashSet<>(found.getSynonyms()));
    Assert.assertEquals(created.getSubclassOf(), found.getSubclassOf());
    Assert.assertEquals(new HashSet<>(created.getRelations()), new HashSet<>(found.getRelations()));
    Assert.assertEquals(created.isProvisional(), found.isProvisional());
    Assert.assertEquals(created.getCreated(), found.getCreated());
  }

  // TODO: check that provisional classes are returned too
  @Test
  public void findAllClassesForOntologyTest() {
    // Create a provisional class
    String ontology = "NCIT";
    int ontologySize = 1080000;
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES;
    // Service invocation
    Response findResponse = client.target(url).request().header("Authorization", authHeader).get();
    // Check the number of results retrieved
    PagedResults<OntologyClass> classes = findResponse.readEntity(new GenericType<PagedResults<OntologyClass>>() {});
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results
    int numClassesFound = classes.getPageSize() * classes.getPageCount();
    Assert.assertTrue("The number of classes found (" +  numClassesFound +") is lower than expected (" + ontologySize + ")" , numClassesFound >= ontologySize);
  }

  @Test
  public void deleteClassTest() {
    // Create a provisional class
    OntologyClass createdClass = createClass();
    // Delete the class that has been created
    String classUrl = baseUrlBp + "/" + BP_CLASSES + "/" + createdClass.getId();
    Response deleteResponse = client.target(classUrl).request().header("Authorization", authHeader).delete();
    // Check HTTP response
    Assert.assertEquals(Status.NO_CONTENT.getStatusCode(), deleteResponse.getStatus());
    // Remove class from the list of created classes
    createdClasses.remove(createdClass);
    // Try to retrieve the class to check that it has been deleted correctly
    String findUrl = baseUrlBpOntologies + "/" + Util.getShortIdentifier(createdClass.getOntology()) + "/" + BP_CLASSES + "/" + createdClass.getId();
    Response findResponse = client.target(findUrl).request().header("Authorization", authHeader).get();
    // Check not found
    Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), findResponse.getStatus());
  }

  /**
   * Utils
   */

  private static OntologyClass createClass() {
    String url = baseUrlBpOntologies + "/" + Util.getShortIdentifier(class1.getOntology()) + "/" + BP_CLASSES;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).post(Entity.json(class1));
    // Check HTTP response
    Assert.assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    OntologyClass created = response.readEntity(OntologyClass.class);
    createdClasses.add(created);
    return created;
  }

  private static void deleteCreatedClasses() throws Exception {
    for (OntologyClass c : createdClasses) {
      // Check if the class still exists
      String findUrl = baseUrlBpOntologies + "/" + Util.getShortIdentifier(c.getOntology()) + "/" +
          BP_CLASSES + "/" + c.getId();
      String deleteUrl = baseUrlBp + "/" + BP_CLASSES + "/" + c.getId();

      Response findResponse = client.target(findUrl).request().header("Authorization", authHeader).get();
      if (findResponse.getStatus() == Status.OK.getStatusCode()) {
        Response deleteResponse = client.target(deleteUrl).request().header("Authorization", authHeader).delete();
        if (deleteResponse.getStatus() != Status.NO_CONTENT.getStatusCode()) {
          throw new Exception("Couldn't delete class: Id = " + c.getLdId());
        }
      } else {
        throw new Exception("Couldn't find class: Id = " + c.getLdId());
      }
    }
  }

}
