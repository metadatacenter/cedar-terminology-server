package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;

import static org.metadatacenter.cedar.terminology.utils.Constants.BP_RELATIONS;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */

// Exercises live BioPortal; excluded from the default build (surefire excludedGroups).
// Run with -DexcludedGroups= (or a bioportal profile) when a BioPortal API key is configured.
@Tag("bioportal")
public class RelationResourceTest extends AbstractTerminologyServerResourceTest {

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeAll
  public static void oneTimeSetUp() {
  }

  /**
   * Sets up the test fixture.
   * (Called before every test case method.)
   */
  @BeforeEach
  public void setUp() {
  }

  /**
   * Tears down the test fixture.
   * (Called after every test case method.)
   */
  @AfterEach
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
    Assertions.assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Store class to delete the class after the test
    Relation created = response.readEntity(Relation.class);
    response.close();
    // Note: the following line it's not currently needed, but it's kept for safety. When the class is deleted,
    // BioPortal will delete the relation too.
    createdRelations.add(created);
    // Check fields
    Relation expected = relation1;
    Assertions.assertNotNull(created.getId());
    Assertions.assertNotNull(created.getLdId());
    Assertions.assertNotNull(created.getCreated());
    Assertions.assertEquals(expected.getSourceClassId(), created.getSourceClassId());
    Assertions.assertEquals(expected.getRelationType(), created.getRelationType());
    Assertions.assertEquals(expected.getTargetClassId(), created.getTargetClassId());
    Assertions.assertEquals(expected.getTargetClassOntology(), created.getTargetClassOntology());
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
    Assertions.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the element retrieved
    Relation found = findResponse.readEntity(Relation.class);
    findResponse.close();
    // Check fields
    Assertions.assertEquals(created.getId(), found.getId());
    Assertions.assertEquals(created.getLdId(), found.getLdId());
    Assertions.assertEquals(created.getSourceClassId(), found.getSourceClassId());
    Assertions.assertEquals(created.getRelationType(), found.getRelationType());
    Assertions.assertEquals(created.getTargetClassId(), found.getTargetClassId());
    Assertions.assertEquals(created.getTargetClassOntology(), found.getTargetClassOntology());
    Assertions.assertEquals(created.getCreated(), found.getCreated());
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
    Assertions.assertEquals(Status.NO_CONTENT.getStatusCode(), deleteResponse.getStatus());
    // Remove relation from the list of created relations. It has been already deleted
    createdRelations.remove(created);
    // Try to retrieve the relation to check that it has been deleted correctly
    String findUrl = baseUrlBp + "/" + BP_RELATIONS + "/" + created.getId();
    Response findResponse = clientBuilder.build().target(findUrl).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).get();
    // Check not found
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(), findResponse.getStatus());
  }

}
