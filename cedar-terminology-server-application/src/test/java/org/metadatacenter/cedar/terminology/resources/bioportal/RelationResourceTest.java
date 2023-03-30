package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.*;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;

import static org.metadatacenter.cedar.terminology.utils.Constants.BP_RELATIONS;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */

public class RelationResourceTest extends AbstractTerminologyServerResourceTest {

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
    Response response =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(relation1));
    // Check HTTP response
    Assert.assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Store class to delete the class after the test
    Relation created = response.readEntity(Relation.class);
    response.close();
    // Note: the following line it's not currently needed, but it's kept for safety. When the class is deleted,
    // BioPortal will delete the relation too.
    createdRelations.add(created);
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

  @Test
  public void findRelationTest() {
    // Create a provisional relation
    Relation created = createRelation(class1, relation1);
    // Find the provisional relation by id
    String url = baseUrlBp + "/" + BP_RELATIONS + "/" + created.getId();
    // Service invocation
    Response findResponse =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the element retrieved
    Relation found = findResponse.readEntity(Relation.class);
    findResponse.close();
    // Check fields
    Assert.assertEquals(created.getId(), found.getId());
    Assert.assertEquals(created.getLdId(), found.getLdId());
    Assert.assertEquals(created.getSourceClassId(), found.getSourceClassId());
    Assert.assertEquals(created.getRelationType(), found.getRelationType());
    Assert.assertEquals(created.getTargetClassId(), found.getTargetClassId());
    Assert.assertEquals(created.getTargetClassOntology(), found.getTargetClassOntology());
    Assert.assertEquals(created.getCreated(), found.getCreated());
  }

  @Test
  public void deleteRelationTest() {
    // Create a provisional relation
    Relation created = createRelation(class1, relation1);
    // Delete the relation that has been created
    String url = baseUrlBp + "/" + BP_RELATIONS + "/" + created.getId();
    Response deleteResponse = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).delete();
    // Check HTTP response
    Assert.assertEquals(Status.NO_CONTENT.getStatusCode(), deleteResponse.getStatus());
    // Remove relation from the list of created relations. It has been already deleted
    createdRelations.remove(created);
    // Try to retrieve the relation to check that it has been deleted correctly
    String findUrl = baseUrlBp + "/" + BP_RELATIONS + "/" + created.getId();
    Response findResponse = clientBuilder.build().target(findUrl).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).get();
    // Check not found
    Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), findResponse.getStatus());
  }

}
