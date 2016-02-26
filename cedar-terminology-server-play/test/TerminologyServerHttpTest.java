import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.*;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.terms.domainObjects.ValueSet;
import org.metadatacenter.terms.util.Util;
import play.Configuration;
import play.Play;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
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

  private static List<OntologyClass> createdClasses;
  private static List<Relation> createdRelations;
  private static List<ValueSet> createdValueSets;
  private static List<Value> createdValues;

  private static OntologyClass class1;
  private static Relation relation1;
  private static ValueSet vs1;
  private static Value value1;

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
        authHeader = "apikey token=" + config.getString("bioportal.apiKeys.test");
      }
    });

    /** Objects initialization **/

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
        classSynonyms, null, classRelations, true, null);

    // Initialize test relation - the source class id will be set later
    String relationType = "http://www.w3.org/2004/02/skos/core#closeMatch";
    String targetClassId = "http://www.owl-ontologies.com/Ontology1447432460.owl#RID1559";
    String targetClassOntology = "http://data.bioontology.org/ontologies/RADLEX";
    relation1 = new Relation(null, null, null, relationType, targetClassId, targetClassOntology, null);

    // Initialize test value set
    String vsLabel = "vs1_test";
    String vsCreator = "http://data.bioontology.org/users/cedar-test";
    String vsCollection = "http://data.bioontology.org/ontologies/CEDARVS";
    List vsDefinitions = new ArrayList<>();
    vsDefinitions.add("vsDefinition1");
    vsDefinitions.add("vsDefinition2");
    List vsSynonyms = new ArrayList<>();
    vsSynonyms.add("vsSynonym1");
    vsSynonyms.add("vsSynonym2");
    List vsRelations = new ArrayList<>();
    vs1 = new ValueSet(null, null, vsLabel, vsCreator, vsCollection, vsDefinitions, vsSynonyms, vsRelations,
        true, null);

    // Initialize test value - the vsId will be set later
    String valueLabel = "value1_test";
    String valueCreator = "http://data.bioontology.org/users/cedar-test";
    String valueVsCollection = "http://data.bioontology.org/ontologies/CEDARVS";
    List valueDefinitions = new ArrayList<>();
    valueDefinitions.add("valueDefinition1");
    valueDefinitions.add("valueDefinition2");
    List valueSynonyms = new ArrayList<>();
    valueSynonyms.add("valueSynonym1");
    valueSynonyms.add("valueSynonym2");
    List valueRelations = new ArrayList<>();
    value1 = new Value(null, null, valueLabel, valueCreator, null, valueVsCollection, valueDefinitions,
        valueSynonyms, valueRelations, true, null);
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
    createdValueSets = new ArrayList<>();
    createdValues = new ArrayList<>();
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
        deleteCreatedValueSets();
        deleteCreatedValues();
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

  @Test
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

  @Test
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
        Assert.assertTrue("The number of value sets found for '" + q + "' is lower than expected", resultsCount >=
            lowLimitCount);
      }
    });
  }

  @Test
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
  public void createClassTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        try {
          String url = SERVER_URL_BIOPORTAL + BP_ONTOLOGIES + "/" + Util.getShortIdentifier(class1.getOntology()) +
              "/" + BP_CLASSES;
          // Service invocation - Create
          ObjectMapper mapper = new ObjectMapper();
          JsonNode class1Json = mapper.readTree(mapper.writeValueAsString(class1));
          WSResponse wsResponseCreate = WS.url(url).setHeader("Authorization", authHeader).setContentType
              ("application/json").post(class1Json).get(TIMEOUT_MS);
          // Check HTTP response
          Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
          OntologyClass created = mapper.treeToValue(wsResponseCreate.asJson(), OntologyClass.class);
          // Store the id to delete the class after the test
          createdClasses.add(created);
          // Check Content-Type
          Assert.assertEquals("application/json; charset=utf-8", wsResponseCreate.getHeader("Content-Type"));
          // Check fields
          OntologyClass expected = class1;
          Assert.assertNotNull(created.getId());
          Assert.assertNotNull(created.getLdId());
          Assert.assertNotNull(created.getCreated());
          Assert.assertEquals(expected.getLabel(), created.getLabel());
          Assert.assertEquals(expected.getCreator(), created.getCreator());
          Assert.assertEquals(expected.getOntology(), created.getOntology());
          Assert.assertEquals(expected.getDefinitions(), created.getDefinitions());
          Assert.assertEquals(expected.getSynonyms(), created.getSynonyms());
          Assert.assertEquals(expected.getSubclassOf(), created.getSubclassOf());
          Assert.assertEquals(expected.getRelations(), created.getRelations());
          Assert.assertEquals(expected.isProvisional(), created.isProvisional());
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    });
  }

  @Test
  // TODO: test regular classes
  public void findClassTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_PROVISIONAL_CLASSES;
        // Create a provisional class
        OntologyClass created = createClass();
        // Find the provisional class by id
        String classUrl = SERVER_URL_BIOPORTAL + BP_ONTOLOGIES + "/" + Util.getShortIdentifier(class1.getOntology()) +
            "/" + BP_CLASSES + "/" + created.getId();
        WSResponse wsResponseFind = WS.url(classUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        // Check response is OK
        Assert.assertEquals(wsResponseFind.getStatus(), OK);
        // Check Content-Type
        Assert.assertEquals(wsResponseFind.getHeader("Content-Type"), "application/json; charset=utf-8");
        // Check the element retrieved
        ObjectMapper mapper = new ObjectMapper();
        OntologyClass found = null;
        try {
          found = mapper.treeToValue(wsResponseFind.asJson(), OntologyClass.class);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        // Check fields
        Assert.assertEquals(created.getId(), found.getId());
        Assert.assertEquals(created.getLdId(), found.getLdId());
        Assert.assertEquals(created.getLabel(), found.getLabel());
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
        Assert.assertEquals(OK, wsResponse.getStatus());
        // Check Content-Type
        Assert.assertEquals(wsResponse.getHeader("Content-Type"), "application/json; charset=utf-8");
        // Check that the array returned is not empty
        int classesCount = wsResponse.asJson().size();
        Assert.assertFalse("Empty array returned", classesCount == 0);
      }
    });
  }

  @Test
  public void findAllProvisionalClassesForOntologyTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        // Create a provisional class
        OntologyClass createdClass = createClass();
        String url = SERVER_URL_BIOPORTAL + BP_ONTOLOGIES + "/" +
            Util.getShortIdentifier(createdClass.getOntology()) + "/" + BP_PROVISIONAL_CLASSES;
        // Find all
        WSResponse wsResponse = WS.url(url).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        // Check response is OK
        Assert.assertEquals(OK, wsResponse.getStatus());
        // Check Content-Type
        Assert.assertEquals(wsResponse.getHeader("Content-Type"), "application/json; charset=utf-8");
        // Check that the array returned is not empty
        int classesCount = wsResponse.asJson().size();
        Assert.assertFalse("Empty array returned", classesCount == 0);
      }
    });
  }

  @Test
  public void updateClassTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_CLASSES;
        // Create a provisional class
        OntologyClass createdClass = createClass();
        // Update the created class
        String updatedLabel = "new label";
        JsonNode changes = Json.newObject().put("label", updatedLabel);
        // Update
        String classUrl = url + "/" + createdClass.getId();
        WSResponse wsResponseUpdate = WS.url(classUrl).setHeader("Authorization", authHeader).patch(changes).get
            (TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(NO_CONTENT, wsResponseUpdate.getStatus());
        // Retrieve the class
        String findUrl = SERVER_URL_BIOPORTAL + BP_ONTOLOGIES + "/" + Util.getShortIdentifier(class1.getOntology()) +
            "/" + BP_CLASSES + "/" + createdClass.getId();
        WSResponse wsResponseFind = WS.url(findUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        ObjectMapper mapper = new ObjectMapper();
        OntologyClass retrievedClass = null;
        try {
          retrievedClass = mapper.treeToValue(wsResponseFind.asJson(), OntologyClass.class);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        // Check that the modifications have been done correctly
        Assert.assertNotNull(retrievedClass.getLabel());
        Assert.assertTrue("The class has not been updated correctly", updatedLabel.compareTo
            (retrievedClass.getLabel()) == 0);
      }
    });
  }

  @Test
  public void deleteClassTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_CLASSES;
        // Create a provisional class
        OntologyClass createdClass = createClass();
        // Delete the class that has been created
        String classUrl = url + "/" + createdClass.getId();
        WSResponse wsResponseDelete = WS.url(classUrl).setHeader("Authorization", authHeader).delete().get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(NO_CONTENT, wsResponseDelete.getStatus());
        // Try to retrieve the class to check that it has been deleted correctly
        WSResponse wsResponseFind = WS.url(classUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        // Check not found
        Assert.assertEquals(wsResponseFind.getStatus(), NOT_FOUND);
      }
    });
  }

  /**
   * Relations
   **/

  @Test
  public void createRelationTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        OntologyClass createdClass = createClass();
        // Create provisional relation
        relation1.setSourceClassId(createdClass.getLdId());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode relation1Json = mapper.valueToTree(relation1);
        WSResponse wsResponseCreate = WS.url(SERVER_URL_BIOPORTAL + BP_RELATIONS).setHeader
            ("Authorization", authHeader).setContentType
            ("application/json").post(relation1Json).get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
        Relation created = null;
        try {
          created = mapper.treeToValue(wsResponseCreate.asJson(), Relation.class);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        // Store the id to delete the relation after the test
        createdRelations.add(created);
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponseCreate.getHeader("Content-Type"));
        // Check fields
        Relation expected = relation1;
        Assert.assertNotNull(created.getId());
        Assert.assertNotNull(created.getLdId());
        Assert.assertNotNull(created.getCreated());
        Assert.assertEquals(expected.getSourceClassId(), created.getSourceClassId());
        Assert.assertEquals(expected.getRelationType(), created.getRelationType());
        Assert.assertEquals(expected.getTargetClassId(), created.getTargetClassId());
        Assert.assertEquals(expected.getTargetClassOntology(), created.getTargetClassOntology());
      }
    });
  }

  @Test
  public void findRelationTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_RELATIONS;
        // Create a provisional relation
        Relation created = createRelation();
        // Find the provisional relation by id
        String relationUrl = url + "/" + created.getId();
        WSResponse wsResponseFind = WS.url(relationUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        // Check response is OK
        Assert.assertEquals(wsResponseFind.getStatus(), OK);
        // Check Content-Type
        Assert.assertEquals(wsResponseFind.getHeader("Content-Type"), "application/json; charset=utf-8");
        // Check the element retrieved
        ObjectMapper mapper = new ObjectMapper();
        Relation found = null;
        try {
          found = mapper.treeToValue(wsResponseFind.asJson(), Relation.class);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        Assert.assertEquals(created.getId(), found.getId());
        Assert.assertEquals(created.getLdId(), found.getLdId());
        Assert.assertEquals(created.getSourceClassId(), found.getSourceClassId());
        Assert.assertEquals(created.getRelationType(), found.getRelationType());
        Assert.assertEquals(created.getTargetClassId(), found.getTargetClassId());
        Assert.assertEquals(created.getTargetClassOntology(), found.getTargetClassOntology());
        Assert.assertEquals(created.getCreated(), found.getCreated());
      }
    });
  }

  @Test
  public void deleteRelationTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_RELATIONS;
        // Create a provisional relation
        Relation created = createRelation();
        // Delete the relation that has been created
        String relationUrl = url + "/" + created.getId();
        WSResponse wsResponseDelete = WS.url(relationUrl).setHeader("Authorization", authHeader).delete().get
            (TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(NO_CONTENT, wsResponseDelete.getStatus());
        // Try to retrieve the relation to check that it has been deleted correctly
        WSResponse wsResponseFind = WS.url(relationUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        // Check not found
        Assert.assertEquals(wsResponseFind.getStatus(), NOT_FOUND);
      }
    });
  }

  /**
   * Value Sets
   **/

  @Test
  public void createValueSetTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(vs1
            .getVsCollection()) + "/" + BP_VALUE_SETS;
        ObjectMapper mapper = new ObjectMapper();
        JsonNode vs1Json = mapper.valueToTree(vs1);
        // Service invocation - Create
        WSResponse wsResponseCreate = WS.url(url).setHeader("Authorization", authHeader).setContentType
            ("application/json").post(vs1Json).get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
        ValueSet created = null;
        try {
          created = mapper.treeToValue(wsResponseCreate.asJson(), ValueSet.class);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        // Store the id to delete the class after the test
        createdValueSets.add(created);
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponseCreate.getHeader("Content-Type"));
        // Check fields
        ValueSet expected = vs1;
        Assert.assertNotNull(created.getId());
        Assert.assertNotNull(created.getLdId());
        Assert.assertNotNull(created.getCreated());
        Assert.assertEquals(expected.getLabel(), created.getLabel());
        Assert.assertEquals(expected.getCreator(), created.getCreator());
        Assert.assertEquals(expected.getVsCollection(), created.getVsCollection());
        Assert.assertEquals(expected.getDefinitions(), created.getDefinitions());
        Assert.assertEquals(expected.getSynonyms(), created.getSynonyms());
        Assert.assertEquals(expected.getRelations(), created.getRelations());
        Assert.assertEquals(expected.isProvisional(), created.isProvisional());
      }
    });
  }

  @Test
  // TODO: test find for regular value sets
  public void findValueSetTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(vs1
            .getVsCollection()) + "/" + BP_VALUE_SETS;
        // Create a provisional value set
        ValueSet created = createValueSet();
        // Find the provisional vs by id
        String vsUrl = url + "/" + created.getId();
        WSResponse wsResponseFind = WS.url(vsUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        // Check response is OK
        Assert.assertEquals(wsResponseFind.getStatus(), OK);
        // Check Content-Type
        Assert.assertEquals(wsResponseFind.getHeader("Content-Type"), "application/json; charset=utf-8");
        // Check the element retrieved
        ObjectMapper mapper = new ObjectMapper();
        ValueSet found = null;
        try {
          found = mapper.treeToValue(wsResponseFind.asJson(), ValueSet.class);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        // Check fields
        Assert.assertEquals(created.getId(), found.getId());
        Assert.assertEquals(created.getLdId(), found.getLdId());
        Assert.assertEquals(created.getLabel(), found.getLabel());
        Assert.assertEquals(created.getCreator(), found.getCreator());
        Assert.assertEquals(created.getVsCollection(), found.getVsCollection());
        Assert.assertEquals(new HashSet<>(created.getDefinitions()), new HashSet<>(found.getDefinitions()));
        Assert.assertEquals(new HashSet<>(created.getSynonyms()), new HashSet<>(found.getSynonyms()));
        Assert.assertEquals(new HashSet<>(created.getRelations()), new HashSet<>(found.getRelations()));
        Assert.assertEquals(created.isProvisional(), found.isProvisional());
        Assert.assertEquals(created.getCreated(), found.getCreated());
      }
    });
  }

  @Test
  public void updateValueSetTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_VALUE_SETS;
        // Create a provisional value set
        ValueSet created = createValueSet();
        // Update the created vs
        String updatedLabel = "new label";
        JsonNode changes = Json.newObject().put("label", updatedLabel);
        // Update
        String vsUrl = url + "/" + created.getId();
        WSResponse wsResponseUpdate = WS.url(vsUrl).setHeader("Authorization", authHeader).patch(changes).get
            (TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(NO_CONTENT, wsResponseUpdate.getStatus());
        // Retrieve the value set
        String findUrl = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(created
            .getVsCollection()) +
            "/" + BP_VALUE_SETS + "/" + created.getId();
        WSResponse wsResponseFind = WS.url(findUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        ObjectMapper mapper = new ObjectMapper();
        ValueSet retrievedVs = null;
        try {
          retrievedVs = mapper.treeToValue(wsResponseFind.asJson(), ValueSet.class);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        // Check that the modifications have been done correctly
        Assert.assertNotNull(retrievedVs.getLabel());
        Assert.assertTrue("The value set has not been updated correctly", updatedLabel.compareTo
            (retrievedVs.getLabel()) == 0);
      }
    });
  }

  @Test
  public void deleteValueSetTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_VALUE_SETS;
        // Create a provisional value set
        ValueSet created = createValueSet();
        // Delete the vs that has been created
        String vsUrl = url + "/" + created.getId();
        WSResponse wsResponseDelete = WS.url(vsUrl).setHeader("Authorization", authHeader).delete().get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(NO_CONTENT, wsResponseDelete.getStatus());
        // Try to retrieve the vs to check that it has been deleted correctly
        String findUrl = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(created
            .getVsCollection()) +
            "/" + BP_VALUE_SETS + "/" + created.getId();
        WSResponse wsResponseFind = WS.url(findUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        // Check not found
        Assert.assertEquals(wsResponseFind.getStatus(), NOT_FOUND);
      }
    });
  }

  /**
   * Values
   **/

  @Test
  public void createValueTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        ValueSet createdVs = createValueSet();
        // Create provisional value
        value1.setVsId(createdVs.getLdId());
        String url = null;
        try {
          url = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(value1
              .getVsCollection()) + "/" + BP_VALUE_SETS + "/" + Util.encodeIfNeeded(value1.getVsId()) + "/" +
              BP_VALUES;
        } catch (UnsupportedEncodingException e) {
          e.printStackTrace();
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode value1Json = mapper.valueToTree(value1);
        WSResponse wsResponseCreate = WS.url(url).setHeader
            ("Authorization", authHeader).setContentType
            ("application/json").post(value1Json).get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
        Value created = null;
        try {
          created = mapper.treeToValue(wsResponseCreate.asJson(), Value.class);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        // Store the id to delete the object after the test
        createdValues.add(created);
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponseCreate.getHeader("Content-Type"));
        // Check fields
        Value expected = value1;
        Assert.assertNotNull(created.getId());
        Assert.assertNotNull(created.getLdId());
        Assert.assertNotNull(created.getCreated());
        Assert.assertEquals(expected.getLabel(), created.getLabel());
        Assert.assertEquals(expected.getCreator(), created.getCreator());
        Assert.assertEquals(expected.getVsId(), created.getVsId());
        Assert.assertEquals(expected.getVsCollection(), created.getVsCollection());
        Assert.assertEquals(new HashSet<>(expected.getDefinitions()), new HashSet<>(created.getDefinitions()));
        Assert.assertEquals(new HashSet<>(expected.getSynonyms()), new HashSet<>(created.getSynonyms()));
        Assert.assertEquals(new HashSet<>(expected.getRelations()), new HashSet<>(created.getRelations()));
        Assert.assertEquals(expected.isProvisional(), created.isProvisional());
      }
    });
  }

  @Test
  public void findValueTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        // Create a provisional value
        Value created = createValue();
        String url = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(created
            .getVsCollection()) + "/" + BP_VALUES;
        // Find the provisional value by id
        String valueUrl = url + "/" + created.getId();
        WSResponse wsResponseFind = WS.url(valueUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        // Check response is OK
        Assert.assertEquals(wsResponseFind.getStatus(), OK);
        // Check Content-Type
        Assert.assertEquals(wsResponseFind.getHeader("Content-Type"), "application/json; charset=utf-8");
        // Check the element retrieved
        ObjectMapper mapper = new ObjectMapper();
        Value found = null;
        try {
          found = mapper.treeToValue(wsResponseFind.asJson(), Value.class);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        Assert.assertEquals(created.getId(), found.getId());
        Assert.assertEquals(created.getLdId(), found.getLdId());
        Assert.assertEquals(created.getLabel(), found.getLabel());
        Assert.assertEquals(created.getCreator(), found.getCreator());
        Assert.assertEquals(created.getVsId(), found.getVsId());
        Assert.assertEquals(created.getVsCollection(), found.getVsCollection());
        Assert.assertEquals(new HashSet<>(created.getDefinitions()), new HashSet<>(found.getDefinitions()));
        Assert.assertEquals(new HashSet<>(created.getSynonyms()), new HashSet<>(found.getSynonyms()));
        Assert.assertEquals(new HashSet<>(created.getRelations()), new HashSet<>(found.getRelations()));
        Assert.assertEquals(created.isProvisional(), found.isProvisional());
        Assert.assertEquals(created.getCreated(), found.getCreated());
      }
    });
  }

  @Test
  public void updateValueTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_VALUES;
        // Create a provisional value
        Value created = createValue();
        // Update the created value
        String updatedLabel = "new label";
        JsonNode changes = Json.newObject().put("label", updatedLabel);
        // Update
        String valueUrl = url + "/" + created.getId();
        WSResponse wsResponseUpdate = WS.url(valueUrl).setHeader("Authorization", authHeader).patch(changes).get
            (TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(NO_CONTENT, wsResponseUpdate.getStatus());
        // Retrieve the value
        String findUrl = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(created.
            getVsCollection()) + "/" + BP_VALUES + "/" + created.getId();
        WSResponse wsResponseFind = WS.url(findUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        ObjectMapper mapper = new ObjectMapper();
        Value retrievedValue = null;
        try {
          retrievedValue = mapper.treeToValue(wsResponseFind.asJson(), Value.class);
        } catch (JsonProcessingException e) {
          e.printStackTrace();
        }
        // Check that the modifications have been done correctly
        Assert.assertNotNull(retrievedValue.getLabel());
        Assert.assertTrue("The value has not been updated correctly", updatedLabel.compareTo
            (retrievedValue.getLabel()) == 0);
      }
    });
  }

  @Test
  public void deleteValueTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        String url = SERVER_URL_BIOPORTAL + BP_VALUES;
        // Create a provisional value
        Value created = createValue();
        // Delete the value that has been created
        String valueUrl = url + "/" + created.getId();
        WSResponse wsResponseDelete = WS.url(valueUrl).setHeader("Authorization", authHeader).delete().get
            (TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(NO_CONTENT, wsResponseDelete.getStatus());
        // Try to retrieve the relation to check that it has been deleted correctly
        WSResponse wsResponseFind = WS.url(valueUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
        // Check not found
        Assert.assertEquals(wsResponseFind.getStatus(), NOT_FOUND);
      }
    });
  }

  /**
   * Utils
   **/
  private static OntologyClass createClass() {
    String url = SERVER_URL_BIOPORTAL + BP_ONTOLOGIES + "/" + Util.getShortIdentifier(class1.getOntology()) + "/" +
        BP_CLASSES;
    ObjectMapper mapper = new ObjectMapper();
    JsonNode class1Json = mapper.valueToTree(class1);
    // Service invocation - Create
    WSResponse wsResponseCreate = WS.url(url).setHeader("Authorization", authHeader).setContentType
        ("application/json").post(class1Json).get(TIMEOUT_MS);
    // Check HTTP response
    Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
    OntologyClass created = null;
    try {
      created = mapper.treeToValue(wsResponseCreate.asJson(), OntologyClass.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    // Store the id to delete the class after the test
    createdClasses.add(created);
    return created;
  }

  private static Relation createRelation() {
    String url = SERVER_URL_BIOPORTAL + BP_RELATIONS;
    OntologyClass createdClass = createClass();
    // Create provisional relation
    relation1.setSourceClassId(createdClass.getId());
    ObjectMapper mapper = new ObjectMapper();
    JsonNode relation1Json = mapper.valueToTree(relation1);
    WSResponse wsResponseCreate = WS.url(url).setHeader
        ("Authorization", authHeader).setContentType
        ("application/json").post(relation1Json).get(TIMEOUT_MS);
    // Check HTTP response
    Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
    Relation created = null;
    try {
      created = mapper.treeToValue(wsResponseCreate.asJson(), Relation.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    // Store the id to delete the relation after the test
    createdRelations.add(created);
    return created;
  }

  private static ValueSet createValueSet() {
    String url = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(vs1
        .getVsCollection()) + "/" + BP_VALUE_SETS;
    ObjectMapper mapper = new ObjectMapper();
    JsonNode vs1Json = mapper.valueToTree(vs1);
    // Service invocation - Create
    WSResponse wsResponseCreate = WS.url(url).setHeader("Authorization", authHeader).setContentType
        ("application/json").post(vs1Json).get(TIMEOUT_MS);
    // Check HTTP response
    Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
    ValueSet created = null;
    try {
      created = mapper.treeToValue(wsResponseCreate.asJson(), ValueSet.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    // Store the id to delete the class after the test
    createdValueSets.add(created);
    return created;
  }

  private static Value createValue() {
    ValueSet createdVs = createValueSet();
    // Create provisional value
    value1.setVsId(createdVs.getLdId());
    String url = null;
    try {
      url = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(value1
          .getVsCollection()) + "/" + BP_VALUE_SETS + "/" + Util.encodeIfNeeded(value1.getVsId()) + "/" +
          BP_VALUES;
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    ObjectMapper mapper = new ObjectMapper();
    JsonNode value1Json = mapper.valueToTree(value1);
    WSResponse wsResponseCreate = WS.url(url).setHeader
        ("Authorization", authHeader).setContentType
        ("application/json").post(value1Json).get(TIMEOUT_MS);
    // Check HTTP response
    Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
    Value created = null;
    try {
      created = mapper.treeToValue(wsResponseCreate.asJson(), Value.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    // Store the id to delete the object after the test
    createdValues.add(created);
    return created;
  }

  private static void deleteCreatedClasses() {
    for (OntologyClass c : createdClasses) {
      // Check if the class still exists
      String findUrl = SERVER_URL_BIOPORTAL + BP_ONTOLOGIES + "/" + Util.getShortIdentifier(c.getOntology()) +
          "/" + BP_CLASSES + "/" + c.getId();
      String deleteUrl = SERVER_URL_BIOPORTAL + BP_CLASSES + "/" + c.getId();
      if (WS.url(findUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS).getStatus() == OK) {
        WS.url(deleteUrl).setHeader("Authorization", authHeader).delete().get(TIMEOUT_MS);
//        Logger.info("Deleted class: " + c.getId());
      }
    }
  }

  private static void deleteCreatedRelations() {
    for (Relation r : createdRelations) {
      // Check if the relation still exists
      String relationUrl = SERVER_URL_BIOPORTAL + BP_RELATIONS + "/" + r.getId();
      if (WS.url(relationUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS).getStatus() == OK) {
        WS.url(relationUrl).setHeader("Authorization", authHeader).delete().get(TIMEOUT_MS);
//        Logger.info("Deleted relation: " + r.getId());
      }
    }
  }

  private static void deleteCreatedValueSets() {
    for (ValueSet vs : createdValueSets) {
      // Check if the value set still exists
      String findUrl = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(vs
          .getVsCollection()) +
          "/" + BP_VALUE_SETS + "/" + vs.getId();
      String deleteUrl = SERVER_URL_BIOPORTAL + BP_CLASSES + "/" + vs.getId();
      if (WS.url(findUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS).getStatus() == OK) {
        WS.url(deleteUrl).setHeader("Authorization", authHeader).delete().get(TIMEOUT_MS);
//        Logger.info("Deleted value set: " + vs.getId());
      }
    }
  }

  private static void deleteCreatedValues() {
    for (Value v : createdValues) {
      // Check if the value still exists
      String findUrl = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(v
          .getVsCollection()) + "/" + BP_VALUES + "/" + v.getId();
      String deleteUrl = SERVER_URL_BIOPORTAL + BP_VALUES + "/" + v.getId();
      if (WS.url(findUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS).getStatus() == OK) {
        WS.url(deleteUrl).setHeader("Authorization", authHeader).delete().get(TIMEOUT_MS);
//        Logger.info("Deleted value: " + v.getId());
      }
    }
  }

}




























