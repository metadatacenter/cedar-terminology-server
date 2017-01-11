package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.junit.*;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.ValueSet;
import org.metadatacenter.terms.util.Util;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import java.util.HashSet;

import static org.metadatacenter.cedar.terminology.utils.Constants.BP_VALUE_SETS;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class ValueSetResourceTest extends AbstractTest {

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
  public void createValueSetTest() {
    String url = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(vs1.getVsCollection()) + "/" + BP_VALUE_SETS;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).post(Entity.json(vs1));
    // Check HTTP response
    Assert.assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Store value set to delete it after the test
    ValueSet created = response.readEntity(ValueSet.class);
    createdValueSets.add(created);
    // Check fields
    ValueSet expected = vs1;
    Assert.assertNotNull(created.getId());
    Assert.assertNotNull(created.getLdId());
    Assert.assertEquals(expected.getPrefLabel(), created.getPrefLabel());
    Assert.assertEquals(expected.getCreator(), created.getCreator());
    Assert.assertEquals(expected.getVsCollection(), created.getVsCollection());
    Assert.assertEquals(new HashSet<>(expected.getDefinitions()), new HashSet<>(created.getDefinitions()));
    Assert.assertEquals(new HashSet<>(expected.getSynonyms()), new HashSet<>(created.getSynonyms()));
    Assert.assertEquals(new HashSet<>(expected.getRelations()), new HashSet<>(created.getRelations()));
    Assert.assertEquals(expected.isProvisional(), created.isProvisional());
  }

 

  //  @Test
//  // TODO: test find for regular value sets
//  public void findValueSetTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        String url = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(vs1
//            .getVsCollection()) + "/" + BP_VALUE_SETS;
//        // Create a provisional value set
//        ValueSet created = createValueSet();
//        // Find the provisional vs by id
//        String vsUrl = url + "/" + created.getId();
//        WSResponse wsResponseFind = WS.url(vsUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
//        // Check response is OK
//        Assert.assertEquals(OK, wsResponseFind.getStatus());
//        // Check Content-Type
//        Assert.assertEquals(wsResponseFind.getHeader("Content-Type"), "application/json; charset=utf-8");
//        // Check the element retrieved
//        ObjectMapper mapper = new ObjectMapper();
//        ValueSet found = null;
//        try {
//          found = mapper.treeToValue(wsResponseFind.asJson(), ValueSet.class);
//        } catch (JsonProcessingException e) {
//          e.printStackTrace();
//        }
//        // Check fields
//        Assert.assertEquals(created.getId(), found.getId());
//        Assert.assertEquals(created.getLdId(), found.getLdId());
//        Assert.assertEquals(created.getPrefLabel(), found.getPrefLabel());
//        Assert.assertEquals(created.getCreator(), found.getCreator());
//        Assert.assertEquals(created.getVsCollection(), found.getVsCollection());
//        Assert.assertEquals(new HashSet<>(created.getDefinitions()), new HashSet<>(found.getDefinitions()));
//        Assert.assertEquals(new HashSet<>(created.getSynonyms()), new HashSet<>(found.getSynonyms()));
//        Assert.assertEquals(new HashSet<>(created.getRelations()), new HashSet<>(found.getRelations()));
//        Assert.assertEquals(created.isProvisional(), found.isProvisional());
//        Assert.assertEquals(created.getCreated(), found.getCreated());
//      }
//    });
//  }

  //  @Test
//  public void findValueSetsByVsCollectionTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        // Create two provisional value sets
//        ValueSet created1 = createValueSet();
//        createValueSet();
//        // Find url
//        String url = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(created1
//            .getVsCollection()) + "/" + BP_VALUE_SETS;
//        WSResponse wsResponseFind = WS.url(url).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
//        // Check response is OK
//        Assert.assertEquals(OK, wsResponseFind.getStatus());
//        // Check Content-Type
//        Assert.assertEquals(wsResponseFind.getHeader("Content-Type"), "application/json; charset=utf-8");
//        // Check the number of elements retrieved
//        int resultsCount = wsResponseFind.asJson().get("collection").size();
//        Assert.assertTrue("Wrong number of value sets retrieved", resultsCount > 1);
//      }
//    });
//  }
//
//  @Test
//  public void findAllValueSetsTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        // Create two provisional value sets
//        createValueSet();
//        createValueSet();
//        // Find url
//        String url = SERVER_URL_BIOPORTAL + BP_VALUE_SETS;
//        WSResponse wsResponseFind = WS.url(url).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
//        // Check response is OK
//        Assert.assertEquals(OK, wsResponseFind.getStatus());
//        // Check Content-Type
//        Assert.assertEquals(wsResponseFind.getHeader("Content-Type"), "application/json; charset=utf-8");
//        // Check the number of elements retrieved
//        int resultsCount = wsResponseFind.asJson().size();
//        Assert.assertTrue("Wrong number of value sets retrieved", resultsCount > 1);
//      }
//    });
//  }
//
//  @Test
//  public void updateValueSetTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        String url = SERVER_URL_BIOPORTAL + BP_VALUE_SETS;
//        // Create a provisional value set
//        ValueSet created = createValueSet();
//        // Update the created vs
//        String updatedLabel = "new label";
//        JsonNode changes = Json.newObject().put("prefLabel", updatedLabel);
//        // Update
//        String vsUrl = url + "/" + created.getId();
//        WSResponse wsResponseUpdate = WS.url(vsUrl).setHeader("Authorization", authHeader).patch(changes).get
//            (TIMEOUT_MS);
//        // Check HTTP response
//        Assert.assertEquals(NO_CONTENT, wsResponseUpdate.getStatus());
//        // Retrieve the value set
//        String findUrl = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(created
//            .getVsCollection()) +
//            "/" + BP_VALUE_SETS + "/" + created.getId();
//        WSResponse wsResponseFind = WS.url(findUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
//        ObjectMapper mapper = new ObjectMapper();
//        ValueSet retrievedVs = null;
//        try {
//          retrievedVs = mapper.treeToValue(wsResponseFind.asJson(), ValueSet.class);
//        } catch (JsonProcessingException e) {
//          e.printStackTrace();
//        }
//        // Check that the modifications have been done correctly
//        Assert.assertNotNull(retrievedVs.getPrefLabel());
//        Assert.assertTrue("The value set has not been updated correctly", updatedLabel.compareTo
//            (retrievedVs.getPrefLabel()) == 0);
//      }
//    });
//  }
//
//  @Test
//  public void deleteValueSetTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        String url = SERVER_URL_BIOPORTAL + BP_VALUE_SETS;
//        // Create a provisional value set
//        ValueSet created = createValueSet();
//        // Delete the vs that has been created
//        String vsUrl = url + "/" + created.getId();
//        WSResponse wsResponseDelete = WS.url(vsUrl).setHeader("Authorization", authHeader).delete().get(TIMEOUT_MS);
//        // Check HTTP response
//        Assert.assertEquals(NO_CONTENT, wsResponseDelete.getStatus());
//        // Try to retrieve the vs to check that it has been deleted correctly
//        String findUrl = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(created
//            .getVsCollection()) +
//            "/" + BP_VALUE_SETS + "/" + created.getId();
//        WSResponse wsResponseFind = WS.url(findUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
//        // Check not found
//        Assert.assertEquals(NOT_FOUND, wsResponseFind.getStatus());
//      }
//    });
//  }


}
