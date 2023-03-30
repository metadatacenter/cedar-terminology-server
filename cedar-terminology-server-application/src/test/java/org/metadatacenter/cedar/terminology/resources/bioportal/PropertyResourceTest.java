package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyProperty;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.util.Util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class PropertyResourceTest extends AbstractTerminologyServerResourceTest {

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
  public void findPropertyTest() {
    // First we do a search to retrieve some properties and pick the first one. Then we try to retrieve it by id
    String q = "title";
    PagedResults<SearchResult> properties = searchProperties(q);
    Assert.assertTrue("No properties found to perform the test", properties.getCollection().size() > 0);
    // Pick the first one
    SearchResult p = properties.getCollection().get(0);
    // Find it by id
    String encodedPropertyId = null;
    try {
      encodedPropertyId = URLEncoder.encode(p.getId(), "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String url =
        baseUrlBpOntologies + "/" + Util.getShortIdentifier(p.getSource()) + "/" + BP_PROPERTIES + "/" + encodedPropertyId;
    // Service invocation
    Response findResponse =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the element retrieved
    OntologyProperty found = findResponse.readEntity(OntologyProperty.class);
    findResponse.close();
    // Check id
    Assert.assertTrue("Wrong property id: " + found.getId(), p.getId().equals(found.getId()));
  }

  @Test
  public void findAllPropertiesForOntologyTest() {
    String ontology = "BIBFRAME";
    int approxPropertiesCount = 198;
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_PROPERTIES;
    // Service invocation
    Response findResponse =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    List<OntologyProperty> properties = findResponse.readEntity(new GenericType<List<OntologyProperty>>() {
    });
    findResponse.close();
    Assert.assertTrue("The number of properties found (" + properties.size() + ") is lower than expected (" +
        approxPropertiesCount + ")", properties.size() >= approxPropertiesCount);
    Assert.assertTrue("The number of properties found (" + properties.size() + ") is higher than expected (" +
        approxPropertiesCount + ")", properties.size() < approxPropertiesCount * 2);
  }

  @Test
  public void findPropertyTreeTest() {
    String ontology = "BIBFRAME";
    // Property "Copyright date" from BIBFRAME (The parent property is "Date")
    String propertyId = "http://id.loc.gov/ontologies/bibframe/copyrightDate";
    String parentPropertyId = "http://id.loc.gov/ontologies/bibframe/date";
    String encodedPropertyId = null;
    try {
      encodedPropertyId = URLEncoder.encode(propertyId, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_PROPERTIES + "/" + encodedPropertyId + "/" + BP_TREE;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the tree is not empty and that it is correctly expanded to the given class
    List<TreeNode> tree = response.readEntity(new GenericType<List<TreeNode>>() {
    });
    response.close();
    Assert.assertTrue("Empty tree", tree.size() > 0);
    TreeNode foundChild = null;
    for (TreeNode node : tree) {
      // If "Date"
      if (node.getLdId().equals(parentPropertyId)) {
        Assert.assertTrue("The 'hasChildren' property for this resource should be set to 'true'",
            node.getHasChildren() == true);
        Assert.assertTrue("The number of children returned for this resource should be greater than 0",
            node.getChildren().size() > 0);
        for (TreeNode childrenNode : node.getChildren()) {
          // If "Copyright date"
          if (childrenNode.getLdId().equals(propertyId)) {
            foundChild = childrenNode;
            break;
          }
        }
      }
    }
    Assert.assertTrue("Given property not found in the returned tree", foundChild != null);
    Assert.assertTrue("Preferred label not found for child property",
        foundChild.getPrefLabel() != null && foundChild.getPrefLabel().length() > 0);
  }

  @Test
  public void findPropertyChildrenTest() {
    String ontology = "BIBFRAME";
    // Class "Date" from BIBFRAME. One of its children is "Copyright date".
    String propertyId = "http://id.loc.gov/ontologies/bibframe/date";
    String childPropertyId = "http://id.loc.gov/ontologies/bibframe/copyrightDate";
    String encodedPropertyId = null;
    try {
      encodedPropertyId = URLEncoder.encode(propertyId, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String url =
        baseUrlBpOntologies + "/" + ontology + "/" + BP_PROPERTIES + "/" + encodedPropertyId + "/" + BP_CHILDREN;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns some children and that one of them is "Copyright date".
    // Note that this check is done with a property that has less children than the default page size. Otherwise,
    // we should iterate over all pages.
    List<OntologyProperty> children = response.readEntity(new GenericType<List<OntologyProperty>>() {
    });
    response.close();
    Assert.assertTrue("No children returned", children.size() > 0);
    boolean childFound = false;
    for (OntologyProperty property : children) {
      if (property.getLdId().equals(childPropertyId)) {
        childFound = true;
      }
    }
    Assert.assertTrue("Child " + childPropertyId + " not found for the given property" + propertyId, childFound);
  }

  @Test
  public void findPropertyDescendantsTest() {
    String ontology = "BIBFRAME";
    // Property "Related artifact" from BIBFRAME (http://id.loc.gov/ontologies/bibframe/relatedTo)
    //   - 1st level descendant: "Accompanied by" (http://id.loc.gov/ontologies/bibframe/accompaniedBy)
    //   - 2nd level descendant: "Supplement" (http://id.loc.gov/ontologies/bibframe/supplement)
    String propertyId = "http://id.loc.gov/ontologies/bibframe/relatedTo";
    String descendant1PropertyId = "http://id.loc.gov/ontologies/bibframe/accompaniedBy";
    String descendant2PropertyId = "http://id.loc.gov/ontologies/bibframe/supplement";
    String encodedPropertyId = null;
    try {
      encodedPropertyId = URLEncoder.encode(propertyId, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String url =
        baseUrlBpOntologies + "/" + ontology + "/" + BP_PROPERTIES + "/" + encodedPropertyId + "/" + BP_DESCENDANTS;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns some children and that those are right.
    // Note that this check is done with a class that has less descendants than the default page size. Otherwise,
    // we should iterate over all pages.
    List<OntologyProperty> descendants = response.readEntity(new GenericType<List<OntologyProperty>>() {
    });
    response.close();
    Assert.assertTrue("No descendants returned", descendants.size() > 0);
    boolean descendant1Found = false;
    boolean descendant2Found = false;
    for (OntologyProperty property : descendants) {
      if (property.getLdId().equals(descendant1PropertyId)) {
        descendant1Found = true;
      } else if (property.getLdId().equals(descendant2PropertyId)) {
        descendant2Found = true;
      }
    }
    Assert.assertTrue("Descendant " + descendant1PropertyId + " not found for the given property " + propertyId,
        descendant1Found);
    Assert.assertTrue("Descendant " + descendant2PropertyId + " not found for the given property " + propertyId,
        descendant2Found);
  }

  @Test
  public void findClassParentsTest() {
    String ontology = "BIBFRAME";
    // Class "Copyright date" from BIBFRAME. Its parent is "Date".
    String propertyId = "http://id.loc.gov/ontologies/bibframe/copyrightDate";
    String parentPropertyId = "http://id.loc.gov/ontologies/bibframe/date";
    String encodedPropertyId = null;
    try {
      encodedPropertyId = URLEncoder.encode(propertyId, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String url =
        baseUrlBpOntologies + "/" + ontology + "/" + BP_PROPERTIES + "/" + encodedPropertyId + "/" + BP_PARENTS;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns the expected parent
    List<OntologyProperty> parents = response.readEntity(new GenericType<List<OntologyProperty>>() {
    });
    response.close();
    Assert.assertTrue("No parents returned", parents.size() > 0);
    boolean parentFound = false;
    for (OntologyProperty property : parents) {
      if (property.getLdId().equals(parentPropertyId)) {
        parentFound = true;
      }
    }
    Assert.assertTrue("Parent " + parentPropertyId + " not found for the given property " + propertyId, parentFound);
  }

}
