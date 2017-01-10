package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.junit.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class RelationResourceTest extends AbstractTest {

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
  public void createRelationTest() {
    String url = baseUrlBp + "/" + BP_RELATIONS;
    // Create provisional class
    OntologyClass createdClass = createClass(class1);
    // Create provisional relation
    relation1.setSourceClassId(createdClass.getLdId());
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).post(Entity.json(relation1));
    // Check HTTP response
    Assert.assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Store class to delete the class after the test
    Relation created = response.readEntity(Relation.class);
    // Note: the following line it's not needed. When the class is deleted, BioPortal will delete the relation too.
    //createdRelations.add(created);
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

  //  @Test
//  public void createRelationTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        OntologyClass createdClass = createClass();
//        // Create provisional relation
//        relation1.setSourceClassId(createdClass.getLdId());
//        ObjectMapper mapper = new ObjectMapper();
//        JsonNode relation1Json = mapper.valueToTree(relation1);
//        WSResponse wsResponseCreate = WS.url(SERVER_URL_BIOPORTAL + BP_RELATIONS).setHeader
//            ("Authorization", authHeader).setContentType
//            ("application/json").post(relation1Json).get(TIMEOUT_MS);
//        // Check HTTP response
//        Assert.assertEquals(CREATED, wsResponseCreate.getStatus());
//        Relation created = null;
//        try {
//          created = mapper.treeToValue(wsResponseCreate.asJson(), Relation.class);
//        } catch (JsonProcessingException e) {
//          e.printStackTrace();
//        }
//        // Store the id to delete the relation after the test
//        createdRelations.add(created);
//        // Check Content-Type
//        Assert.assertEquals("application/json; charset=utf-8", wsResponseCreate.getHeader("Content-Type"));
//        // Check fields
//        Relation expected = relation1;
//        Assert.assertNotNull(created.getId());
//        Assert.assertNotNull(created.getLdId());
//        Assert.assertNotNull(created.getCreated());
//        Assert.assertEquals(expected.getSourceClassId(), created.getSourceClassId());
//        Assert.assertEquals(expected.getRelationType(), created.getRelationType());
//        Assert.assertEquals(expected.getTargetClassId(), created.getTargetClassId());
//        Assert.assertEquals(expected.getTargetClassOntology(), created.getTargetClassOntology());
//      }
//    });
//  }
//
//  @Test
//  public void findRelationTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        String url = SERVER_URL_BIOPORTAL + BP_RELATIONS;
//        // Create a provisional relation
//        Relation created = createRelation();
//        // Find the provisional relation by id
//        String relationUrl = url + "/" + created.getId();
//        WSResponse wsResponseFind = WS.url(relationUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
//        // Check response is OK
//        Assert.assertEquals(OK, wsResponseFind.getStatus());
//        // Check Content-Type
//        Assert.assertEquals(wsResponseFind.getHeader("Content-Type"), "application/json; charset=utf-8");
//        // Check the element retrieved
//        ObjectMapper mapper = new ObjectMapper();
//        Relation found = null;
//        try {
//          found = mapper.treeToValue(wsResponseFind.asJson(), Relation.class);
//        } catch (JsonProcessingException e) {
//          e.printStackTrace();
//        }
//        Assert.assertEquals(created.getId(), found.getId());
//        Assert.assertEquals(created.getLdId(), found.getLdId());
//        Assert.assertEquals(created.getSourceClassId(), found.getSourceClassId());
//        Assert.assertEquals(created.getRelationType(), found.getRelationType());
//        Assert.assertEquals(created.getTargetClassId(), found.getTargetClassId());
//        Assert.assertEquals(created.getTargetClassOntology(), found.getTargetClassOntology());
//        Assert.assertEquals(created.getCreated(), found.getCreated());
//      }
//    });
//  }
//
//  @Test
//  public void deleteRelationTest() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        String url = SERVER_URL_BIOPORTAL + BP_RELATIONS;
//        // Create a provisional relation
//        Relation created = createRelation();
//        // Delete the relation that has been created
//        String relationUrl = url + "/" + created.getId();
//        WSResponse wsResponseDelete = WS.url(relationUrl).setHeader("Authorization", authHeader).delete().get
//            (TIMEOUT_MS);
//        // Check HTTP response
//        Assert.assertEquals(NO_CONTENT, wsResponseDelete.getStatus());
//        // Try to retrieve the relation to check that it has been deleted correctly
//        WSResponse wsResponseFind = WS.url(relationUrl).setHeader("Authorization", authHeader).get().get(TIMEOUT_MS);
//        // Check not found
//        Assert.assertEquals(NOT_FOUND, wsResponseFind.getStatus());
//      }
//    });
//  }
//



}
