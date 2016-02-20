import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.*;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;
import play.Configuration;
import play.Play;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;

import javax.validation.constraints.AssertTrue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static play.mvc.Http.Status.*;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;
import static utils.Constants.*;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class TerminologyServerHttpTest {

  private static final int TEST_SERVER_PORT = 3333;
  private static final String SERVER_URL = "http://localhost:" + TEST_SERVER_PORT + "/";
  private static final String SERVER_URL_BIOPORTAL = SERVER_URL + BP_ENDPOINT;
  private static final int TIMEOUT_MS = 30000;

  private static String authHeader;

  private static List<String> createdClasses;
  private static List<String> createdRelations;

  private static JsonNode class1;
  private static JsonNode relation1;

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUp() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        Configuration config = Play.application().configuration();
        // Authorization header
        authHeader = "apikey token=" + config.getString("bioportal.apikey.test");
      }
    });

    /** Objects initialization **/
    ObjectMapper mapper = new ObjectMapper();
    try {
      // Initialize test class
      List definitions = new ArrayList<>();
      definitions.add("definition1");
      definitions.add("definition2");
      OntologyClass c1 = new OntologyClass(null, null, "class1_test", "cedar", "CEDARVS", definitions,
          new ArrayList<String>(), null, null, true, null);
      class1 = mapper.readTree(mapper.writeValueAsString(c1));

      // Initialize test relation
      String relationType = "http://www.w3.org/2004/02/skos/core#closeMatch";
      String targetClassId = "http://www.owl-ontologies.com/Ontology1447432460.owl#RID1559";
      String targetClassOntology = "http://data.bioontology.org/ontologies/RADLEX";
      Relation r1 = new Relation(null, null, null, relationType, targetClassId, targetClassOntology, null);
      relation1 = mapper.readTree(mapper.writeValueAsString(r1));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * (Called once after all the test methods in the class).
   */
  @AfterClass
  public static void oneTimeTearDown() {

  }

  /**
   * Sets up the test fixture.
   * (Called before every test case method.)
   */
  @Before
  public void setUp() {
    // Ids of test objects
    createdClasses = new ArrayList<>();
    createdRelations = new ArrayList<>();
  }

  /**
   * Tears down the test fixture.
   * (Called after every test case method.)
   */
  @After
  public void tearDown() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        deleteCreatedClasses();
        deleteCreatedRelations();
      }
    });
  }

  /**
   * Search tests
   */

  @Test
  public void searchAllTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_SEARCH;
        // Query parameters
        String q = "white blood cell";
        // Service invocation - Search all
        WSResponse wsResponse = WS.url(url).setHeader("Authorization", authHeader).setQueryParameter("q", q).get()
            .get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(OK, wsResponse.getStatus());
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponse.getHeader("Content-Type"));
        // Check the number of results
        JsonNode jsonResponse = wsResponse.asJson();
        int pageCount = jsonResponse.get("pageCount").asInt();
        int lowLimitPageCount = 2000;
        Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", pageCount >
            lowLimitPageCount);
      }
    });
  }

  //@Test
  public void searchClassesTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_SEARCH;
        // Query parameters
        String q = "white blood cell";
        String scope = "classes";
        // Service invocation - Search all
        WSResponse wsResponse = WS.url(url).setHeader("Authorization", authHeader).setQueryParameter("q", q)
            .setQueryParameter("scope", scope).get().get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(OK, wsResponse.getStatus());
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponse.getHeader("Content-Type"));
        // Check the number of results
        JsonNode jsonResponse = wsResponse.asJson();
        int pageCount = jsonResponse.get("pageCount").asInt();
        int lowLimitPageCount = 2000;
        Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", pageCount >
            lowLimitPageCount);
      }
    });
  }

  //  @Test
  public void searchValueSetsTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_SEARCH;
        // Query parameters
        String q = "Amblyopia";
        String scope = "value_sets";
        // Service invocation - Search all
        WSResponse wsResponse = WS.url(url).setHeader("Authorization", authHeader).setQueryParameter("q", q)
            .setQueryParameter("scope", scope).get().get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(OK, wsResponse.getStatus());
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponse.getHeader("Content-Type"));
        // Check the number of results
        JsonNode jsonResponse = wsResponse.asJson();
        int resultsCount = jsonResponse.get("collection").size();
        int lowLimitCount = 1;
        Assert.assertTrue("The number of value sets found for '" + q + "' is lower than expected", resultsCount >
            lowLimitCount);
      }
    });
  }

  //  @Test
  public void searchValuesTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_SEARCH;
        // Query parameters
        String q = "inclusion";
        String scope = "values";
        // Service invocation - Search all
        WSResponse wsResponse = WS.url(url).setHeader("Authorization", authHeader).setQueryParameter("q", q)
            .setQueryParameter("scope", scope).get().get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(OK, wsResponse.getStatus());
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponse.getHeader("Content-Type"));
        // Check the number of results
        JsonNode jsonResponse = wsResponse.asJson();
        int resultsCount = jsonResponse.get("collection").size();
        int lowLimitCount = 1;
        Assert.assertTrue("The number of values found for '" + q + "' is lower than expected", resultsCount >
            lowLimitCount);
      }
    });
  }

  /**
   * Classes
   **/

  @Test
  public void createProvisionalClassTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_PROVISIONAL_CLASSES;
        // Service invocation - Create
        WSResponse wsResponseCreate = WS.url(url).setHeader("Authorization", authHeader).setContentType
            ("application/json").post(class1).get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
        JsonNode created = wsResponseCreate.asJson();
        // Store the id to delete the class after the test
        createdClasses.add(created.get("id").asText());
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponseCreate.getHeader("Content-Type"));
        // Check fields
        JsonNode expected = class1;
        Assert.assertNotNull(created.get("@id"));
        Assert.assertNotNull(created.get("id"));
        Assert.assertNotNull(created.get("label"));
        Assert.assertEquals(expected.get("label"), created.get("label"));
      }
    });
  }

  @Test
  public void findProvisionalClassTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_PROVISIONAL_CLASSES;
        // Create a provisional class
        JsonNode created = createProvisionalClass();
        // Find the provisional class by id
        String classUrl = url + "/" + created.get("id").asText();
        WSResponse wsResponseFind = WS.url(classUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        // Check response is OK
        Assert.assertEquals(wsResponseFind.getStatus(), OK);
        // Check Content-Type
        Assert.assertEquals(wsResponseFind.getHeader("Content-Type"), "application/json; charset=utf-8");
        // Check the element retrieved
        JsonNode found = wsResponseFind.asJson();
        Assert.assertEquals(created, found);
      }
    });
  }

  @Test
  public void findAllProvisionalClassesTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_PROVISIONAL_CLASSES;
        // Find all
        WSResponse wsResponse = WS.url(url).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        // Check response is OK
        Assert.assertEquals(wsResponse.getStatus(), OK);
        // Check Content-Type
        Assert.assertEquals(wsResponse.getHeader("Content-Type"), "application/json; charset=utf-8");
        // Check that the array returned is not empty
        int classesCount = wsResponse.asJson().size();
        Assert.assertFalse("Empty array returned", classesCount == 0);
      }
    });
  }

  @Test
  public void updateProvisionalClassTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_PROVISIONAL_CLASSES;
        // Create a provisional class
        JsonNode createdClass = createProvisionalClass();
        // Update the created class
        String updatedLabel = "new label";
        JsonNode changes = Json.newObject().put("label", updatedLabel);
        // Update
        String classUrl = url + "/" + createdClass.get("id").asText();
        WSResponse wsResponseUpdate = WS.url(classUrl).setHeader("Authorization", authHeader).patch(changes).get
            (TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(NO_CONTENT, wsResponseUpdate.getStatus());
        // Retrieve the class
        WSResponse wsResponseFind = WS.url(classUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        JsonNode retrievedClass = wsResponseFind.asJson();
        // Check that the modifications have been done correctly
        Assert.assertNotNull(retrievedClass.get("label"));
        Assert.assertTrue("The class has not been updated correctly", updatedLabel.compareTo
            (retrievedClass.get("label").asText()) == 0);
      }
    });
  }

  @Test
  public void deleteProvisionalClassTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_PROVISIONAL_CLASSES;
        // Create a provisional class
        JsonNode createdClass = createProvisionalClass();
        // Delete the class that has been created
        String classUrl = url + "/" + createdClass.get("id").asText();
        WSResponse wsResponseDelete = WS.url(classUrl).setHeader("Authorization", authHeader).delete().get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(NO_CONTENT, wsResponseDelete.getStatus());
      }
    });
  }

  /**
   * Relations
   **/

  @Test
  public void createProvisionalRelationTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        JsonNode createdClass = createProvisionalClass();
        // Create provisional relation
        ((ObjectNode) relation1).put("sourceClassId", createdClass.get("@id").asText());
        //((ObjectNode)relation1).put("sourceClassId", createdClass.get("@id").asText());
        WSResponse wsResponseCreate = WS.url(SERVER_URL_BIOPORTAL + BP_PROVISIONAL_RELATIONS).setHeader
            ("Authorization", authHeader).setContentType
            ("application/json").post(relation1).get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
        JsonNode created = wsResponseCreate.asJson();
        // Store the id to delete the relation after the test
        createdRelations.add(created.get("id").asText());
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponseCreate.getHeader("Content-Type"));
        // Check fields
        JsonNode expected = relation1;
        ((ObjectNode) expected).put("id", created.get("id").asText());
        ((ObjectNode) expected).put("@id", created.get("@id").asText());
        ((ObjectNode) expected).put("created", created.get("created").asText());
        Assert.assertEquals(expected, created);
      }
    });
  }

//  @Test
//  public void updateProvisionalRelationTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        String url = SERVER_URL_BIOPORTAL + BP_PROVISIONAL_CLASSES;
//        // Create a provisional relation
//        JsonNode createdRelation = createProvisionalRelation();
//        // Update the created relation
//        String updatedRelationType = "http://www.w3.org/2004/02/skos/core#exactMatch";
//        JsonNode changes = Json.newObject().put("relationType", updatedRelationType);
//        // Update
//        String relationUrl = url + "/" + createdRelation.get("id").asText();
//        WSResponse wsResponseUpdate = WS.url(relationUrl).setHeader("Authorization", authHeader).patch(changes).get
//            (TIMEOUT_MS);
//        // Check HTTP response
//        Assert.assertEquals(NO_CONTENT, wsResponseUpdate.getStatus());
//        // Retrieve the relation
//        WSResponse wsResponseFind = WS.url(relationUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
//        JsonNode retrievedRelation = wsResponseFind.asJson();
//        // Check that the modifications have been done correctly
//        Assert.assertTrue("The relation has not been updated correctly", updatedRelationType.compareTo
//            (retrievedRelation.get("relationType").asText()) == 0);
//      }
//    });
//  }

  /** Value Sets **/

  /** Values **/


  /**
   * Utils
   **/
  private static JsonNode createProvisionalClass() {
    String url = SERVER_URL_BIOPORTAL + BP_PROVISIONAL_CLASSES;
    // Create a provisional class
    WSResponse wsResponseCreate = WS.url(url).setHeader("Authorization", authHeader).setContentType
        ("application/json").post(class1).get(TIMEOUT_MS);
    JsonNode created = wsResponseCreate.asJson();
    // Check HTTP response
    Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
    String classId = created.get("id").asText();
    // Store the id to delete the class after the test
    createdClasses.add(classId);
    return created;
  }

  private static JsonNode createProvisionalRelation() {
    String url = SERVER_URL_BIOPORTAL + BP_PROVISIONAL_RELATIONS;
    JsonNode createdClass = createProvisionalClass();
    // Create provisional relation
    ((ObjectNode) relation1).put("sourceClassId", createdClass.get("@id").asText());
    WSResponse wsResponseCreate = WS.url(SERVER_URL_BIOPORTAL + BP_PROVISIONAL_RELATIONS).setHeader
        ("Authorization", authHeader).setContentType
        ("application/json").post(relation1).get(TIMEOUT_MS);
    // Check HTTP response
    Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
    JsonNode created = wsResponseCreate.asJson();
    // Store the id to delete the relation after the test
    createdRelations.add(created.get("id").asText());
    return created;
  }


  private static void deleteCreatedClasses() {
    for (String id : createdClasses) {
      // Check if the class still exists
      String classUrl = SERVER_URL_BIOPORTAL + BP_PROVISIONAL_CLASSES + "/" + id;
      if (WS.url(classUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS).getStatus() == OK) {
        WS.url(classUrl).setHeader("Authorization", authHeader).delete().get(TIMEOUT_MS);
        System.out.println("Deleted class: " + id);
      }
    }
  }

  private static void deleteCreatedRelations() {
    for (String id : createdRelations) {
      // Check if the relation still exists
      String relationUrl = SERVER_URL_BIOPORTAL + BP_PROVISIONAL_RELATIONS + "/" + id;
      if (WS.url(relationUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS).getStatus() == OK) {
        WS.url(relationUrl).setHeader("Authorization", authHeader).delete().get(TIMEOUT_MS);
        System.out.println("Deleted relation: " + id);
      }
    }
  }

}




























