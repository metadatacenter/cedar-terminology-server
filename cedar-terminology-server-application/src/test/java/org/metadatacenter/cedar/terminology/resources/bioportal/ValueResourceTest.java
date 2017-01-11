package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.junit.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.util.Util;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class ValueResourceTest extends AbstractTest {

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

  //  @Test
//  public void createValueTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        ValueSet createdVs = createValueSet();
//        // Create provisional value
//        value1.setVsId(createdVs.getLdId());
//        String url = null;
//        try {
//          url = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(value1
//              .getVsCollection()) + "/" + BP_VALUE_SETS + "/" + Util.encodeIfNeeded(value1.getVsId()) + "/" +
//              BP_VALUES;
//        } catch (UnsupportedEncodingException e) {
//          e.printStackTrace();
//        }
//        ObjectMapper mapper = new ObjectMapper();
//        JsonNode value1Json = mapper.valueToTree(value1);
//        WSResponse wsResponseCreate = WS.url(url).setHeader
//            ("Authorization", authHeader).setContentType
//            ("application/json").post(value1Json).get(TIMEOUT_MS);
//        // Check HTTP response
//        Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
//        Value created = null;
//        try {
//          created = mapper.treeToValue(wsResponseCreate.asJson(), Value.class);
//        } catch (JsonProcessingException e) {
//          e.printStackTrace();
//        }
//        // Store the id to delete the object after the test
//        createdValues.add(created);
//        // Check Content-Type
//        Assert.assertEquals("application/json; charset=utf-8", wsResponseCreate.getHeader("Content-Type"));
//        // Check fields
//        Value expected = value1;
//        Assert.assertNotNull(created.getId());
//        Assert.assertNotNull(created.getLdId());
//        Assert.assertNotNull(created.getCreated());
//        Assert.assertEquals(expected.getPrefLabel(), created.getPrefLabel());
//        Assert.assertEquals(expected.getCreator(), created.getCreator());
//        Assert.assertEquals(expected.getVsId(), created.getVsId());
//        Assert.assertEquals(expected.getVsCollection(), created.getVsCollection());
//        Assert.assertEquals(new HashSet<>(expected.getDefinitions()), new HashSet<>(created.getDefinitions()));
//        Assert.assertEquals(new HashSet<>(expected.getSynonyms()), new HashSet<>(created.getSynonyms()));
//        Assert.assertEquals(new HashSet<>(expected.getRelations()), new HashSet<>(created.getRelations()));
//        Assert.assertEquals(expected.isProvisional(), created.isProvisional());
//      }
//    });
//  }
//
//  @Test
//  public void findValueTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        // Create a provisional value
//        Value created = createValue();
//        String url = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(created
//            .getVsCollection()) + "/" + BP_VALUES;
//        // Find the provisional value by id
//        String valueUrl = url + "/" + created.getId();
//        WSResponse wsResponseFind = WS.url(valueUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
//        // Check response is OK
//        Assert.assertEquals(OK, wsResponseFind.getStatus());
//        // Check Content-Type
//        Assert.assertEquals(wsResponseFind.getHeader("Content-Type"), "application/json; charset=utf-8");
//        // Check the element retrieved
//        ObjectMapper mapper = new ObjectMapper();
//        Value found = null;
//        try {
//          found = mapper.treeToValue(wsResponseFind.asJson(), Value.class);
//        } catch (JsonProcessingException e) {
//          e.printStackTrace();
//        }
//        Assert.assertEquals(created.getId(), found.getId());
//        Assert.assertEquals(created.getLdId(), found.getLdId());
//        Assert.assertEquals(created.getPrefLabel(), found.getPrefLabel());
//        Assert.assertEquals(created.getCreator(), found.getCreator());
//        Assert.assertEquals(created.getVsId(), found.getVsId());
//        Assert.assertEquals(created.getVsCollection(), found.getVsCollection());
//        Assert.assertEquals(new HashSet<>(created.getDefinitions()), new HashSet<>(found.getDefinitions()));
//        Assert.assertEquals(new HashSet<>(created.getSynonyms()), new HashSet<>(found.getSynonyms()));
//        Assert.assertEquals(new HashSet<>(created.getRelations()), new HashSet<>(found.getRelations()));
//        Assert.assertEquals(created.isProvisional(), found.isProvisional());
//        Assert.assertEquals(created.getCreated(), found.getCreated());
//      }
//    });
//  }
//
//  @Test
//  public void updateValueTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        String url = SERVER_URL_BIOPORTAL + BP_VALUES;
//        // Create a provisional value
//        Value created = createValue();
//        // Update the created value
//        String updatedLabel = "new label";
//        JsonNode changes = Json.newObject().put("prefLabel", updatedLabel);
//        // Update
//        String valueUrl = url + "/" + created.getId();
//        WSResponse wsResponseUpdate = WS.url(valueUrl).setHeader("Authorization", authHeader).patch(changes).get
//            (TIMEOUT_MS);
//        // Check HTTP response
//        Assert.assertEquals(NO_CONTENT, wsResponseUpdate.getStatus());
//        // Retrieve the value
//        String findUrl = SERVER_URL_BIOPORTAL + BP_VALUE_SET_COLLECTIONS + "/" + Util.getShortIdentifier(created.
//            getVsCollection()) + "/" + BP_VALUES + "/" + created.getId();
//        WSResponse wsResponseFind = WS.url(findUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
//        ObjectMapper mapper = new ObjectMapper();
//        Value retrievedValue = null;
//        try {
//          retrievedValue = mapper.treeToValue(wsResponseFind.asJson(), Value.class);
//        } catch (JsonProcessingException e) {
//          e.printStackTrace();
//        }
//        // Check that the modifications have been done correctly
//        Assert.assertNotNull(retrievedValue.getPrefLabel());
//        Assert.assertTrue("The value has not been updated correctly", updatedLabel.compareTo
//            (retrievedValue.getPrefLabel()) == 0);
//      }
//    });
//  }
//
//  @Test
//  public void deleteValueTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        String url = SERVER_URL_BIOPORTAL + BP_VALUES;
//        // Create a provisional value
//        Value created = createValue();
//        // Delete the value that has been created
//        String valueUrl = url + "/" + created.getId();
//        WSResponse wsResponseDelete = WS.url(valueUrl).setHeader("Authorization", authHeader).delete().get
//            (TIMEOUT_MS);
//        // Check HTTP response
//        Assert.assertEquals(NO_CONTENT, wsResponseDelete.getStatus());
//        // Try to retrieve the relation to check that it has been deleted correctly
//        WSResponse wsResponseFind = WS.url(valueUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
//        // Check not found
//        Assert.assertEquals(NOT_FOUND, wsResponseFind.getStatus());
//      }
//    });
//  }


}
