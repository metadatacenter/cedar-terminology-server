package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.junit.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.terms.domainObjects.ValueSet;
import org.metadatacenter.terms.util.Util;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class ValueResourceTest extends AbstractTerminologyServerResourceTest {

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
  public void createValueTest() {
    // Create value set and value
    ValueSet createdVs = createValueSet(vs1);
    value1.setVsId(createdVs.getLdId());
    String url = null;
    try {
      url = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(value1.getVsCollection()) + "/"
          + BP_VALUE_SETS + "/" + Util.encodeIfNeeded(value1.getVsId()) + "/" + BP_VALUES;
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).post(Entity.json(value1));
    // Check HTTP response
    Assert.assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Store value to delete it after the test
    Value created = response.readEntity(Value.class);
    createdValues.add(created);
    // Check fields
    Value expected = value1;
    Assert.assertNotNull(created.getId());
    Assert.assertNotNull(created.getLdId());
    Assert.assertNotNull(created.getCreated());
    Assert.assertEquals(expected.getPrefLabel(), created.getPrefLabel());
    Assert.assertEquals(expected.getCreator(), created.getCreator());
    Assert.assertEquals(expected.getVsId(), created.getVsId());
    Assert.assertEquals(expected.getVsCollection(), created.getVsCollection());
    Assert.assertEquals(new HashSet<>(expected.getDefinitions()), new HashSet<>(created.getDefinitions()));
    Assert.assertEquals(new HashSet<>(expected.getSynonyms()), new HashSet<>(created.getSynonyms()));
    Assert.assertEquals(new HashSet<>(expected.getRelations()), new HashSet<>(created.getRelations()));
    Assert.assertEquals(expected.isProvisional(), created.isProvisional());
  }

  @Test
  public void findValueTest() {
    // Create a provisional value
    ValueSet createdVs = createValueSet(vs1);
    Value created = createValue(createdVs.getLdId(), value1);
    // Find the value by id
    String findUrl = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(created.getVsCollection()) + "/" +
        BP_VALUES + "/" + created.getId();
    // Service invocation
    Response findResponse = client.target(findUrl).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Response.Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the element retrieved
    Value found = findResponse.readEntity(Value.class);
    // Check fields
    Assert.assertEquals(created.getId(), found.getId());
    Assert.assertEquals(created.getLdId(), found.getLdId());
    Assert.assertEquals(created.getPrefLabel(), found.getPrefLabel());
    Assert.assertEquals(created.getCreator(), found.getCreator());
    Assert.assertEquals(created.getVsId(), found.getVsId());
    Assert.assertEquals(created.getVsCollection(), found.getVsCollection());
    Assert.assertEquals(new HashSet<>(created.getDefinitions()), new HashSet<>(found.getDefinitions()));
    Assert.assertEquals(new HashSet<>(created.getSynonyms()), new HashSet<>(found.getSynonyms()));
    Assert.assertEquals(new HashSet<>(created.getRelations()), new HashSet<>(found.getRelations()));
    Assert.assertEquals(created.isProvisional(), found.isProvisional());
    Assert.assertEquals(created.getCreated(), found.getCreated());
  }

  // TODO: test it for non-provisional values and value sets
//  @Test
//  public void findValueTreeTest() {
//    ValueSet createdVs = createValueSet(vs1);
//    Value createdValue1 = createValue(createdVs.getLdId(), value1);
//    Value createdValue2 = createValue(createdVs.getLdId(), value2);
//    String encodedValue1Id = null;
//    try {
//      encodedValue1Id = URLEncoder.encode(createdValue1.getLdId(), "UTF-8");
//    } catch (UnsupportedEncodingException e) {
//      e.printStackTrace();
//    }
//    String url = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(createdValue1.getVsCollection())
//        + "/" + BP_VALUES + "/" + encodedValue1Id + "/" + BP_TREE;
//    // Wait to be sure that the BioPortal search index was updated
//    try {
//      Thread.sleep(1000);
//    } catch (InterruptedException e) {
//      e.printStackTrace();
//    }
//    // Service invocation
//    Response response = client.target(url).request().header("Authorization", authHeader).get();
//    // Check HTTP response
//    Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getPublicationStatus());
//    // Check Content-Type
//    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
//    // Check that the tree is not empty
//    TreeNode tree = response.readEntity(TreeNode.class);
//    Assert.assertTrue("No children", tree.getChildren().size() > 0);
//    // Check that the root is the expected value set
//    Assert.assertTrue("The tree root does not correspond to the expected value set", tree.getId().equals(createdVs.getId()));
//    // Check that the children correspond to the created values
//    boolean v1Found = false;
//    boolean v2Found = false;
//    for (TreeNode node : tree.getChildren()) {
//      if (node.getId().equals(createdValue1.getId())) {
//        v1Found = true;
//      } else if (node.getId().equals(createdValue2.getId())) {
//        v2Found = true;
//      }
//    }
//    Assert.assertTrue("Expected values not found in the returned tree", v1Found && v2Found);
//  }

  // TODO: test it for non-provisional values and value sets
  @Test
  public void findAllValuesInValueSetByValueTest() {
    ValueSet createdVs = createValueSet(vs1);
    Value createdValue1 = createValue(createdVs.getLdId(), value1);
    Value createdValue2 = createValue(createdVs.getLdId(), value2);
    String encodedValue1Id = null;
    try {
      encodedValue1Id = URLEncoder.encode(createdValue1.getLdId(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String url = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(createdValue1.getVsCollection())
        + "/" + BP_VALUES + "/" + encodedValue1Id + "/" + BP_ALL_VALUES;
    // Wait to be sure that the BioPortal search index was updated
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that there are some results
    PagedResults<Value> values = response.readEntity(new GenericType<PagedResults<Value>>(){});
    Assert.assertTrue("No values found", values.getCollection().size() == 2);
    boolean v1Found = false;
    boolean v2Found = false;
    for (Value v : values.getCollection()) {
      if (v.getId().equals(createdValue1.getId())) {
        v1Found = true;
      } else if (v.getId().equals(createdValue2.getId())) {
        v2Found = true;
      }
    }
    Assert.assertTrue("Expected values not found", v1Found && v2Found);
  }

  @Test
  public void updateValueTest() {
    // Create a provisional value
    ValueSet createdValueSet = createValueSet(vs1);
    Value createdValue = createValue(createdValueSet.getLdId(), value1);
    Value updatedValue = new Value(createdValue.getId(), createdValue.getLdId(), "new label",
        createdValue.getCreator(), createdValue.getVsId(), createdValue.getVsCollection(), createdValue.getDefinitions(),
        createdValue.getSynonyms(), createdValue.getRelations(), createdValue.isProvisional(), createdValue.getCreated());
    String url = baseUrlBp + "/" + BP_VALUES + "/" + createdValue.getId();
    // Service invocation
    Response updateResponse = client.target(url).request().header("Authorization", authHeader).put(Entity.json(updatedValue));
    // Check HTTP response
    Assert.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), updateResponse.getStatus());
    // Retrieve the value
    String findUrl = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(createdValue.getVsCollection()) + "/" +
        BP_VALUES + "/" + createdValue.getId();
    Response findResponse = client.target(findUrl).request().header("Authorization", authHeader).get();
    Value found = findResponse.readEntity(Value.class);
    // Check that the modifications have been done correctly
    Value expected = updatedValue;
    Assert.assertEquals(expected.getId(), found.getId());
    Assert.assertEquals(expected.getLdId(), found.getLdId());
    Assert.assertEquals(expected.getPrefLabel(), found.getPrefLabel());
    Assert.assertEquals(expected.getCreator(), found.getCreator());
    Assert.assertEquals(expected.getVsId(), found.getVsId());
    Assert.assertEquals(expected.getVsCollection(), found.getVsCollection());
    Assert.assertEquals(new HashSet<>(expected.getDefinitions()), new HashSet<>(found.getDefinitions()));
    Assert.assertEquals(new HashSet<>(expected.getSynonyms()), new HashSet<>(found.getSynonyms()));
    Assert.assertEquals(new HashSet<>(expected.getRelations()), new HashSet<>(found.getRelations()));
    Assert.assertEquals(expected.isProvisional(), found.isProvisional());
    Assert.assertEquals(expected.getCreated(), found.getCreated());
  }

  @Test
  public void deleteValueTest() {
    // Create a provisional value
    ValueSet createdVs = createValueSet(vs1);
    Value created = createValue(createdVs.getLdId(), value1);
    // Delete the value that has been created
    String classUrl = baseUrlBp + "/" + BP_VALUES + "/" + created.getId();
    Response deleteResponse = client.target(classUrl).request().header("Authorization", authHeader).delete();
    // Check HTTP response
    Assert.assertEquals(Response.Status.NO_CONTENT.getStatusCode(), deleteResponse.getStatus());
    // Remove value from the list of created values. It has already been deleted
    createdValues.remove(created);
    // Try to retrieve the value to check that it has been deleted correctly
    String findUrl = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(created.getVsCollection()) + "/" +
        BP_VALUES + "/" + created.getId();
    Response findResponse = client.target(findUrl).request().header("Authorization", authHeader).get();
    // Check not found
    Assert.assertEquals(Response.Status.NOT_FOUND.getStatusCode(), findResponse.getStatus());
  }

}
