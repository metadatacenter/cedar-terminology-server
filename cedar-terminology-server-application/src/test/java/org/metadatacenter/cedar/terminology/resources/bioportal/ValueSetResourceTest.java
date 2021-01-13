package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.junit.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.ValueSet;
import org.metadatacenter.terms.util.Util;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.HashSet;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.BP_VALUE_SETS;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
@Ignore
public class ValueSetResourceTest extends AbstractTerminologyServerResourceTest {

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
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(vs1));
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

  @Test
  // TODO: test find for regular value sets too
  public void findValueSetTest() {
    // Create a provisional value set
    ValueSet created = createValueSet(vs1);
    // Find the provisional value set by id
    String url = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(created.getVsCollection()) + "/" +
        BP_VALUE_SETS + "/" + created.getId();
    // Service invocation
    Response findResponse = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the element retrieved
    ValueSet found = findResponse.readEntity(ValueSet.class);
    // Check fields
    Assert.assertEquals(created.getId(), found.getId());
    Assert.assertEquals(created.getLdId(), found.getLdId());
    Assert.assertEquals(created.getPrefLabel(), found.getPrefLabel());
    Assert.assertEquals(created.getCreator(), found.getCreator());
    Assert.assertEquals(created.getVsCollection(), found.getVsCollection());
    Assert.assertEquals(new HashSet<>(created.getDefinitions()), new HashSet<>(found.getDefinitions()));
    Assert.assertEquals(new HashSet<>(created.getSynonyms()), new HashSet<>(found.getSynonyms()));
    Assert.assertEquals(new HashSet<>(created.getRelations()), new HashSet<>(found.getRelations()));
    Assert.assertEquals(created.isProvisional(), found.isProvisional());
    Assert.assertEquals(created.getCreated(), found.getCreated());
  }

  @Test
  public void findValueSetsByVsCollectionTest() {
    // Create two provisional value sets
    ValueSet created1 = createValueSet(vs1);
    createValueSet(vs1);
    // Find url
    String url = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(created1.getVsCollection()) + "/" +
        BP_VALUE_SETS;
    // Service invocation
    Response findResponse = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of elements retrieved
    PagedResults<ValueSet> valueSets = findResponse.readEntity(new GenericType<PagedResults<ValueSet>>() {
    });
    int resultsCount = valueSets.getCollection().size();
    Assert.assertTrue("Wrong number of value sets retrieved", resultsCount > 1);
  }

//  @Test
//  public void findValueSetByValueTest() {
//    // Create a provisional value set
//    ValueSet created1 = createValueSet(vs1);
//
//  }

  @Test
  public void findAllValueSetsTest() {
    // Find url
    String url = baseUrlBp + "/" + BP_VALUE_SETS;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the results returned
    List<ValueSet> valueSets = response.readEntity(new GenericType<List<ValueSet>>() {
    });
    Assert.assertTrue("Wrong number of value sets returned", valueSets.size() > 1);
  }

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
    Response updateResponse = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).put(Entity.json
        (updatedValueSet));
    // Check HTTP response
    Assert.assertEquals(Status.NO_CONTENT.getStatusCode(), updateResponse.getStatus());
    String findUrl = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(created.getVsCollection()) + "/" +
        BP_VALUE_SETS + "/" + created.getId();
    // Service invocation
    Response findResponse = clientBuilder.build().target(findUrl).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check the element retrieved
    ValueSet found = findResponse.readEntity(ValueSet.class);
    // Check fields
    ValueSet expected = updatedValueSet;
    Assert.assertEquals(expected.getId(), found.getId());
    Assert.assertEquals(expected.getLdId(), found.getLdId());
    Assert.assertEquals(expected.getPrefLabel(), found.getPrefLabel());
    Assert.assertEquals(expected.getCreator(), found.getCreator());
    Assert.assertEquals(expected.getVsCollection(), found.getVsCollection());
    Assert.assertEquals(new HashSet<>(expected.getDefinitions()), new HashSet<>(found.getDefinitions()));
    Assert.assertEquals(new HashSet<>(expected.getSynonyms()), new HashSet<>(found.getSynonyms()));
    Assert.assertEquals(new HashSet<>(expected.getRelations()), new HashSet<>(found.getRelations()));
    Assert.assertEquals(expected.isProvisional(), found.isProvisional());
    Assert.assertEquals(expected.getCreated(), found.getCreated());
  }

  @Test
  public void deleteValueSetTest() {
    // Create a provisional value set
    ValueSet created = createValueSet(vs1);
    // Delete the vs that has been created
    String url = baseUrlBp + "/" + BP_VALUE_SETS + "/" + created.getId();
    Response deleteResponse = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).delete();
    // Check HTTP response
    Assert.assertEquals(Status.NO_CONTENT.getStatusCode(), deleteResponse.getStatus());
    // Try to retrieve the vs to check that it has been deleted correctly
    String findUrl = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(created.getVsCollection()) +
        "/" + BP_VALUE_SETS + "/" + created.getId();
    Response findResponse = clientBuilder.build().target(findUrl).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check not found
    Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), findResponse.getStatus());
  }

}
