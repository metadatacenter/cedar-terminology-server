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
public class ClassResourceTest extends AbstractTest {

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
  public void createClassTest() {
    String url = baseUrlBpOntologies + "/" + Util.getShortIdentifier(class1.getOntology()) + "/" + BP_CLASSES;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).post(Entity.json(class1));
    // Check HTTP response
    Assert.assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Store class to delete the class after the test
    OntologyClass created = response.readEntity(OntologyClass.class);
    createdClasses.add(created);
    // Check fields
    OntologyClass expected = class1;
    Assert.assertNotNull(created.getId());
    Assert.assertNotNull(created.getLdId());
    Assert.assertNotNull(created.getCreated());
    Assert.assertEquals(expected.getPrefLabel(), created.getPrefLabel());
    Assert.assertEquals(expected.getCreator(), created.getCreator());
    Assert.assertEquals(expected.getOntology(), created.getOntology());
    Assert.assertEquals(expected.getDefinitions(), created.getDefinitions());
    Assert.assertEquals(expected.getSynonyms(), created.getSynonyms());
    Assert.assertEquals(expected.getSubclassOf(), created.getSubclassOf());
    Assert.assertEquals(expected.getRelations(), created.getRelations());
    Assert.assertEquals(expected.isProvisional(), created.isProvisional());
  }

  // TODO: test regular classes
  @Test
  public void findClassTest() {
    // Create a provisional class
    OntologyClass created = createClass(class1);
    // Find the provisional class by id
    String classUrl = baseUrlBpOntologies + "/" + Util.getShortIdentifier(class1.getOntology()) + "/" + BP_CLASSES + "/" + created.getId();
    // Service invocation
    Response findResponse = client.target(classUrl).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the element retrieved
    OntologyClass found = findResponse.readEntity(OntologyClass.class);
    // Check fields
    Assert.assertEquals(created.getId(), found.getId());
    Assert.assertEquals(created.getLdId(), found.getLdId());
    Assert.assertEquals(created.getPrefLabel(), found.getPrefLabel());
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

  // TODO: check that provisional classes are returned too
  @Test
  public void findAllClassesForOntologyTest() {
    String ontology = "NCIT";
    int ontologySize = 108000;
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES;
    // Service invocation
    Response findResponse = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<OntologyClass> classes = findResponse.readEntity(new GenericType<PagedResults<OntologyClass>>() {});
    int numClassesFound = classes.getPageSize() * classes.getPageCount();
    Assert.assertTrue("The number of classes found (" +  numClassesFound + ") is lower than expected (" + ontologySize + ")" , numClassesFound >= ontologySize);
  }

  // TODO: test it for provisional classes too
  @Test
  public void findClassTreeTest() {
    String ontology = "NCIT";
    // Class "Cellular Process" from NCIT (The parent class is "Biological Process")
    String classId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C20480";
    String parentClassId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C17828";
    String encodedClassId = null;
    try {
      encodedClassId = URLEncoder.encode(classId, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES + "/" + encodedClassId + "/" + BP_TREE;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the tree is not empty and that it is correctly expanded to the given class
    List<TreeNode> tree = response.readEntity(new GenericType<List<TreeNode>>() {});
    Assert.assertTrue("Empty tree", tree.size() > 0);
    boolean classFound = false;
    for (TreeNode node : tree) {
      // If "Biological Process"
      if (node.getLdId().equals(parentClassId)) {
        Assert.assertTrue("The 'hasChildren' property for this node should be set to 'true'", node.getHasChildren() == true);
        Assert.assertTrue("The number of children returned for this node shouldn't be 0", node.getChildren().size() > 0);
        for (TreeNode childrenNode : node.getChildren()) {
          // If "Cellular Process"
          if (childrenNode.getLdId().equals(classId)) {
            classFound = true;
          }
        }
      } else {
        Assert.assertTrue("The number of children returned for this node should be 0", node.getChildren().size() == 0);
      }
    }
    Assert.assertTrue("Given class not found in the returned tree", classFound);
  }

  // TODO: test it for provisional classes too
  @Test
  public void findClassChildrenTest() {
    String ontology = "NCIT";
    // Class "Biological Process" from NCIT. One of its children is "Cellular Process".
    String classId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C17828";
    String childClassId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C20480";
    String encodedClassId = null;
    try {
      encodedClassId = URLEncoder.encode(classId, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES + "/" + encodedClassId + "/" + BP_CHILDREN;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns some children and that one of them is "Cellular Process".
    // Note that this check is done with a class that has less children than the default page size. Otherwise,
    // we should iterate over all pages.
    PagedResults<OntologyClass> children = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {});
    Assert.assertTrue("No children returned", children.getCollection().size() > 0);
    boolean childFound = false;
    for (OntologyClass c : children.getCollection()) {
      if (c.getLdId().equals(childClassId)) {
        childFound = true;
      }
    }
    Assert.assertTrue("Child " + childClassId + " not found for the given class" + classId, childFound);
  }

  // TODO: test it for provisional classes too
  @Test
  public void findClassDescendantsTest() {
    String ontology = "NCIT";
    // Class "Mobiluncus" from NCIT (C86517)
    //   - 1st level descendant: Mobiluncus curtisii (C86518)
    //   - 2nd level descendant: Mobiluncus curtisii subsp holmesii (C86897)
    String classId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C86517";
    String descendant1ClassId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C86518";
    String descendant2ClassId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C86897";
    String encodedClassId = null;
    try {
      encodedClassId = URLEncoder.encode(classId, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES + "/" + encodedClassId + "/" + BP_DESCENDANTS;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns some children and that one of them is "Cellular Process".
    // Note that this check is done with a class that has less descendants than the default page size. Otherwise,
    // we should iterate over all pages.
    PagedResults<OntologyClass> descendants = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {});
    Assert.assertTrue("No descendants returned", descendants.getCollection().size() > 0);
    boolean descendant1Found = false;
    boolean descendant2Found = false;
    for (OntologyClass c : descendants.getCollection()) {
      if (c.getLdId().equals(descendant1ClassId)) {
        descendant1Found = true;
      }
      else if (c.getLdId().equals(descendant2ClassId)) {
        descendant2Found = true;
      }
    }
    Assert.assertTrue("Descendant " + descendant1ClassId + " not found for the given class " + classId, descendant1Found);
    Assert.assertTrue("Descendant " + descendant2ClassId + " not found for the given class " + classId, descendant2Found);
  }

  // TODO: test it for provisional classes too
  @Test
  public void findClassParentsTest() {
    String ontology = "NCIT";
    // Class "Cellular Process" (C20480) from NCIT. Its parent is "Biological Process" (C17828).
    String classId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C20480";
    String parentClassId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C17828";
    String encodedClassId = null;
    try {
      encodedClassId = URLEncoder.encode(classId, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES + "/" + encodedClassId + "/" + BP_PARENTS;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns the expected parent
    List<OntologyClass> parents = response.readEntity(new GenericType<List<OntologyClass>>() {});
    Assert.assertTrue("No parents returned", parents.size() > 0);
    boolean parentFound = false;
    for (OntologyClass c : parents) {
      if (c.getLdId().equals(parentClassId)) {
        parentFound = true;
      }
    }
    Assert.assertTrue("Parent " + parentClassId + " not found for the given class " + classId, parentFound);
  }

  @Test
  public void findAllProvisionalClassesTest() {
    String url = baseUrlBp + "/" + BP_PROVISIONAL_CLASSES;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the array returned is not empty
    PagedResults<OntologyClass> results = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {});
    Assert.assertTrue("Empty array returned", results.getCollection().size() > 0);
    // Check that the classes returned are provisional
    for (OntologyClass pc : results.getCollection()) {
      Assert.assertTrue("Provisional class expected, but non provisional class found", pc.isProvisional());
    }
  }

  @Test
  public void findAllProvisionalClassesForOntologyTest() {
    // Create a provisional class
    OntologyClass createdClass = createClass(class1);
    String url = baseUrlBpOntologies + "/" + Util.getShortIdentifier(createdClass.getOntology()) + "/" + BP_PROVISIONAL_CLASSES;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the array returned is not empty
    PagedResults<OntologyClass> results = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {});
    Assert.assertTrue("Empty array returned", results.getCollection().size() > 0);
    // Check that the classes returned are provisional
    for (OntologyClass pc : results.getCollection()) {
      Assert.assertTrue("Provisional class expected, but non provisional class found", pc.isProvisional());
    }
  }

  @Test
  public void updateClassTest() {
    // Create a provisional class
    OntologyClass createdClass = createClass(class1);
    OntologyClass updatedClass = new OntologyClass(createdClass.getId(), createdClass.getLdId(), "new label",
        createdClass.getCreator(), createdClass.getOntology(), createdClass.getDefinitions(), createdClass.getSynonyms(),
        createdClass.getSubclassOf(), createdClass.getRelations(), createdClass.isProvisional(), createdClass.getCreated(),
        createdClass.getHasChildren());
    String url = baseUrlBp + "/" + BP_CLASSES + "/" + createdClass.getId();
    // Service invocation
    Response updateResponse = client.target(url).request().header("Authorization", authHeader).put(Entity.json
        (updatedClass));
    // Check HTTP response
    Assert.assertEquals(Status.NO_CONTENT.getStatusCode(), updateResponse.getStatus());
    // Retrieve the class
    String findUrl = baseUrlBpOntologies + "/" + Util.getShortIdentifier(createdClass.getOntology())
        + "/" + BP_CLASSES + "/" + createdClass.getId();
    Response findResponse = client.target(findUrl).request().header("Authorization", authHeader).get();
    OntologyClass found = findResponse.readEntity(OntologyClass.class);
    // Check that the modifications have been done correctly
    OntologyClass expected = updatedClass;
    Assert.assertEquals(expected.getId(), found.getId());
    Assert.assertEquals(expected.getLdId(), found.getLdId());
    Assert.assertEquals(expected.getPrefLabel(), found.getPrefLabel());
    Assert.assertEquals(expected.getCreator(), found.getCreator());
    Assert.assertEquals(expected.getOntology(), found.getOntology());
    Assert.assertTrue(expected.getDefinitions().containsAll(found.getDefinitions()) && found.getDefinitions().containsAll(expected.getDefinitions()));
    Assert.assertTrue(expected.getSynonyms().containsAll(found.getSynonyms()) && found.getSynonyms().containsAll(expected.getSynonyms()));
    Assert.assertEquals(expected.getSubclassOf(), found.getSubclassOf());
    Assert.assertEquals(expected.getRelations(), found.getRelations());
    Assert.assertEquals(expected.isProvisional(), found.isProvisional());
    Assert.assertEquals(expected.getCreated(), found.getCreated());
    Assert.assertEquals(expected.getHasChildren(), found.getHasChildren());
  }

  @Test
  public void deleteClassTest() {
    // Create a provisional class
    OntologyClass createdClass = createClass(class1);
    // Delete the class that has been created
    String classUrl = baseUrlBp + "/" + BP_CLASSES + "/" + createdClass.getId();
    Response deleteResponse = client.target(classUrl).request().header("Authorization", authHeader).delete();
    // Check HTTP response
    Assert.assertEquals(Status.NO_CONTENT.getStatusCode(), deleteResponse.getStatus());
    // Remove class from the list of created classes
    createdClasses.remove(createdClass);
    // Try to retrieve the class to check that it has been deleted correctly
    String findUrl = baseUrlBpOntologies + "/" + Util.getShortIdentifier(createdClass.getOntology()) + "/" + BP_CLASSES + "/" + createdClass.getId();
    Response findResponse = client.target(findUrl).request().header("Authorization", authHeader).get();
    // Check not found
    Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), findResponse.getStatus());
  }

}
