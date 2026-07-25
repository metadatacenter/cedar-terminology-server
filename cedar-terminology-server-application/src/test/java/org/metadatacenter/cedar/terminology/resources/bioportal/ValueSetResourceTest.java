package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.ValueSet;
import org.metadatacenter.terms.util.Util;

import java.util.HashSet;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.BP_VALUE_SETS;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
@Disabled
public class ValueSetResourceTest extends AbstractTerminologyServerResourceTest {

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

  @Disabled
  @Test
  public void createValueSetTest() {
    String url = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(vs1.getVsCollection()) + "/" + BP_VALUE_SETS;
    // Service invocation
    Response response =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(vs1));
    // Check HTTP response
    Assertions.assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Store value set to delete it after the test
    ValueSet created = response.readEntity(ValueSet.class);
    response.close();
    createdValueSets.add(created);
    // Check fields
    ValueSet expected = vs1;
    Assertions.assertNotNull(created.getId());
    Assertions.assertNotNull(created.getLdId());
    Assertions.assertEquals(expected.getPrefLabel(), created.getPrefLabel());
    Assertions.assertEquals(expected.getCreator(), created.getCreator());
    Assertions.assertEquals(expected.getVsCollection(), created.getVsCollection());
    Assertions.assertEquals(new HashSet<>(expected.getDefinitions()), new HashSet<>(created.getDefinitions()));
    Assertions.assertEquals(new HashSet<>(expected.getSynonyms()), new HashSet<>(created.getSynonyms()));
    Assertions.assertEquals(new HashSet<>(expected.getRelations()), new HashSet<>(created.getRelations()));
    Assertions.assertEquals(expected.isProvisional(), created.isProvisional());
  }

  @Disabled
  @Test
  // TODO: test find for regular value sets too
  public void findValueSetTest() {
    // Create a provisional value set
    ValueSet created = createValueSet(vs1);
    // Find the provisional value set by id
    String url = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(created.getVsCollection()) + "/" +
        BP_VALUE_SETS + "/" + created.getId();
    // Service invocation
    Response findResponse =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the element retrieved
    ValueSet found = findResponse.readEntity(ValueSet.class);
    findResponse.close();
    // Check fields
    Assertions.assertEquals(created.getId(), found.getId());
    Assertions.assertEquals(created.getLdId(), found.getLdId());
    Assertions.assertEquals(created.getPrefLabel(), found.getPrefLabel());
    Assertions.assertEquals(created.getCreator(), found.getCreator());
    Assertions.assertEquals(created.getVsCollection(), found.getVsCollection());
    Assertions.assertEquals(new HashSet<>(created.getDefinitions()), new HashSet<>(found.getDefinitions()));
    Assertions.assertEquals(new HashSet<>(created.getSynonyms()), new HashSet<>(found.getSynonyms()));
    Assertions.assertEquals(new HashSet<>(created.getRelations()), new HashSet<>(found.getRelations()));
    Assertions.assertEquals(created.isProvisional(), found.isProvisional());
    Assertions.assertEquals(created.getCreated(), found.getCreated());
  }

  @Disabled
  @Test
  public void findValueSetsByVsCollectionTest() {
    // Create two provisional value sets
    ValueSet created1 = createValueSet(vs1);
    createValueSet(vs1);
    // Find url
    String url = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(created1.getVsCollection()) + "/" +
        BP_VALUE_SETS;
    // Service invocation
    Response findResponse =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of elements retrieved
    PagedResults<ValueSet> valueSets = findResponse.readEntity(new GenericType<PagedResults<ValueSet>>() {
    });
    findResponse.close();
    int resultsCount = valueSets.getCollection().size();
    Assertions.assertTrue( resultsCount > 1,"Wrong number of value sets retrieved");
  }

//  @Test
//  public void findValueSetByValueTest() {
//    // Create a provisional value set
//    ValueSet created1 = createValueSet(vs1);
//
//  }

  @Disabled
  @Test
  public void findAllValueSetsTest() {
    // Find url
    String url = baseUrlBp + "/" + BP_VALUE_SETS;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the results returned
    List<ValueSet> valueSets = response.readEntity(new GenericType<List<ValueSet>>() {
    });
    response.close();
    Assertions.assertTrue( valueSets.size() > 1,"Wrong number of value sets returned");
  }

  @Disabled
  @Test
  public void updateValueSetTest() {
    // Create a provisional value set
    ValueSet created = createValueSet(vs1);
    // Update the vs that has been created
    String url = baseUrlBp + "/" + BP_VALUE_SETS + "/" + created.getId();
    ValueSet updatedValueSet = new ValueSet(created.getId(), created.getLdId(), "new label", created.getCreator(),
        created.getVsCollection(), created.getDefinitions(), created.getSynonyms(),
        created.getRelations(), created.isProvisional(), created.getCreated());
    // Service invocation
    Response updateResponse = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).put(Entity.json
        (updatedValueSet));
    // Check HTTP response
    Assertions.assertEquals(Status.NO_CONTENT.getStatusCode(), updateResponse.getStatus());
    String findUrl = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(created.getVsCollection()) + "/" +
        BP_VALUE_SETS + "/" + created.getId();
    // Service invocation
    Response findResponse = clientBuilder.build().target(findUrl).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).get();
    // Check the element retrieved
    ValueSet found = findResponse.readEntity(ValueSet.class);
    findResponse.close();
    // Check fields
    ValueSet expected = updatedValueSet;
    Assertions.assertEquals(expected.getId(), found.getId());
    Assertions.assertEquals(expected.getLdId(), found.getLdId());
    Assertions.assertEquals(expected.getPrefLabel(), found.getPrefLabel());
    Assertions.assertEquals(expected.getCreator(), found.getCreator());
    Assertions.assertEquals(expected.getVsCollection(), found.getVsCollection());
    Assertions.assertEquals(new HashSet<>(expected.getDefinitions()), new HashSet<>(found.getDefinitions()));
    Assertions.assertEquals(new HashSet<>(expected.getSynonyms()), new HashSet<>(found.getSynonyms()));
    Assertions.assertEquals(new HashSet<>(expected.getRelations()), new HashSet<>(found.getRelations()));
    Assertions.assertEquals(expected.isProvisional(), found.isProvisional());
    Assertions.assertEquals(expected.getCreated(), found.getCreated());
  }

  @Disabled
  @Test
  public void deleteValueSetTest() {
    // Create a provisional value set
    ValueSet created = createValueSet(vs1);
    // Delete the vs that has been created
    String url = baseUrlBp + "/" + BP_VALUE_SETS + "/" + created.getId();
    Response deleteResponse = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).delete();
    // Check HTTP response
    Assertions.assertEquals(Status.NO_CONTENT.getStatusCode(), deleteResponse.getStatus());
    // Try to retrieve the vs to check that it has been deleted correctly
    String findUrl = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(created.getVsCollection()) +
        "/" + BP_VALUE_SETS + "/" + created.getId();
    Response findResponse = clientBuilder.build().target(findUrl).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).get();
    // Check not found
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(), findResponse.getStatus());
  }

}
