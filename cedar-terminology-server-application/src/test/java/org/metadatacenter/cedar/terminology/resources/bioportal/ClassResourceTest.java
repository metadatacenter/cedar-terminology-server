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
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.util.Util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class ClassResourceTest extends AbstractTerminologyServerResourceTest {

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
  public void createClassTest() {
    String url = baseUrlBpOntologies + "/" + Util.getShortIdentifier(class1.getOntology()) + "/" + BP_CLASSES;
    // Service invocation
    Response response =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(class1));
    // Check HTTP response
    Assertions.assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Store class to delete the class after the test
    OntologyClass created = response.readEntity(OntologyClass.class);
    response.close();
    createdClasses.add(created);
    // Check fields
    OntologyClass expected = class1;
    Assertions.assertNotNull(created.getId());
    Assertions.assertNotNull(created.getLdId());
    Assertions.assertNotNull(created.getCreated());
    Assertions.assertEquals(expected.getPrefLabel(), created.getPrefLabel());
    Assertions.assertEquals(expected.getCreator(), created.getCreator());
    Assertions.assertEquals(expected.getOntology(), created.getOntology());
    Assertions.assertEquals(expected.getDefinitions(), created.getDefinitions());
    Assertions.assertEquals(expected.getSynonyms(), created.getSynonyms());
    Assertions.assertEquals(expected.getSubclassOf(), created.getSubclassOf());
    Assertions.assertEquals(expected.getRelations(), created.getRelations());
    Assertions.assertEquals(expected.isProvisional(), created.isProvisional());
  }

  // TODO: test regular classes
  @Test
  public void findClassTest() {
    // Create a provisional class
    OntologyClass created = createClass(class1);
    // Find the provisional class by id
    String classUrl = baseUrlBpOntologies + "/" + Util.getShortIdentifier(class1.getOntology()) + "/" + BP_CLASSES +
        "/" + created.getId();
    // Service invocation
    Response findResponse = clientBuilder.build().target(classUrl).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the element retrieved
    OntologyClass found = findResponse.readEntity(OntologyClass.class);
    findResponse.close();
    // Check fields
    Assertions.assertEquals(created.getId(), found.getId());
    Assertions.assertEquals(created.getLdId(), found.getLdId());
    Assertions.assertEquals(created.getPrefLabel(), found.getPrefLabel());
    Assertions.assertEquals(created.getCreator(), found.getCreator());
    Assertions.assertEquals(created.getOntology(), found.getOntology());
    // Convert list to set because order is irrelevant
    Assertions.assertEquals(new HashSet<>(created.getDefinitions()), new HashSet<>(found.getDefinitions()));
    Assertions.assertEquals(new HashSet<>(created.getSynonyms()), new HashSet<>(found.getSynonyms()));
    Assertions.assertEquals(created.getSubclassOf(), found.getSubclassOf());
    Assertions.assertEquals(new HashSet<>(created.getRelations()), new HashSet<>(found.getRelations()));
    Assertions.assertEquals(created.isProvisional(), found.isProvisional());
    Assertions.assertEquals(created.getCreated(), found.getCreated());
  }

  // TODO: check that provisional classes are returned too
  @Test
  public void findAllClassesForOntologyTest() {
    String ontology = "NCIT";
    int ontologySize = 157000;
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES;
    // Service invocation
    Response findResponse =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), findResponse.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, findResponse.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<OntologyClass> classes = findResponse.readEntity(new GenericType<>() {
    });
    findResponse.close();
    int numClassesFound = classes.getPageSize() * classes.getPageCount();
    Assertions.assertTrue( numClassesFound >= ontologySize,"The number of classes found (" + numClassesFound + ") is lower than expected (" + ontologySize + ")");
  }

  // TODO: test it for provisional classes too
  @Test
  public void findClassTreeTest() {
    String ontology = "NCIT";
    // Class "Cellular Process" from NCIT (The parent class is "Biological Process")
    String classId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C20480";
    String parentClassId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C17828";
    String encodedClassId = null;
    encodedClassId = URLEncoder.encode(classId, StandardCharsets.UTF_8);
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES + "/" + encodedClassId + "/" + BP_TREE;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the tree is not empty and that it is correctly expanded to the given class
    List<TreeNode> tree = response.readEntity(new GenericType<List<TreeNode>>() {
    });
    response.close();
    Assertions.assertTrue( !tree.isEmpty(),"Empty tree");
    boolean classFound = false;
    for (TreeNode node : tree) {
      // If "Biological Process"
      if (node.getLdId().equals(parentClassId)) {
        Assertions.assertTrue(
            node.getHasChildren(),"The 'hasChildren' property for this resource should be set to 'true'");
        Assertions.assertTrue(
            !node.getChildren().isEmpty(),"The number of children returned for this resource shouldn't be 0");
        for (TreeNode childrenNode : node.getChildren()) {
          // If "Cellular Process"
          if (childrenNode.getLdId().equals(classId)) {
            classFound = true;
            break;
          }
        }
      } else {
        Assertions.assertTrue(
            node.getChildren().isEmpty(),"The number of children returned for this resource should be 0");
      }
    }
    Assertions.assertTrue( classFound,"Given class not found in the returned tree");
  }

  // TODO: test it for provisional classes too
  @Test
  public void findClassChildrenTest() {
    String ontology = "NCIT";
    // Class "Biological Process" from NCIT. One of its children is "Cellular Process".
    String classId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C17828";
    String childClassId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C20480";
    String encodedClassId = null;
    encodedClassId = URLEncoder.encode(classId, StandardCharsets.UTF_8);
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES + "/" + encodedClassId + "/" + BP_CHILDREN;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns some children and that one of them is "Cellular Process".
    // Note that this check is done with a class that has less children than the default page size. Otherwise,
    // we should iterate over all pages.
    PagedResults<OntologyClass> children = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {
    });
    response.close();
    Assertions.assertTrue( !children.getCollection().isEmpty(),"No children returned");
    boolean childFound = false;
    for (OntologyClass c : children.getCollection()) {
      if (c.getLdId().equals(childClassId)) {
        childFound = true;
        break;
      }
    }
    Assertions.assertTrue( childFound,"Child " + childClassId + " not found for the given class" + classId);
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
    encodedClassId = URLEncoder.encode(classId, StandardCharsets.UTF_8);
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES + "/" + encodedClassId + "/" + BP_DESCENDANTS;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns some children and that those children are the expected ones.
    // Note that this check is done with a class that has less descendants than the default page size. Otherwise,
    // we should iterate over all pages.
    PagedResults<OntologyClass> descendants = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {
    });
    response.close();
    Assertions.assertTrue( descendants.getCollection().size() > 0,"No descendants returned");
    boolean descendant1Found = false;
    boolean descendant2Found = false;
    for (OntologyClass c : descendants.getCollection()) {
      if (c.getLdId().equals(descendant1ClassId)) {
        descendant1Found = true;
      } else if (c.getLdId().equals(descendant2ClassId)) {
        descendant2Found = true;
      }
    }
    Assertions.assertTrue(
        descendant1Found,"Descendant " + descendant1ClassId + " not found for the given class " + classId);
    Assertions.assertTrue(
        descendant2Found,"Descendant " + descendant2ClassId + " not found for the given class " + classId);
  }

  // TODO: test it for provisional classes too
  @Test
  public void findClassParentsTest() {
    String ontology = "NCIT";
    // Class "Cellular Process" (C20480) from NCIT. Its parent is "Biological Process" (C17828).
    String classId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C20480";
    String parentClassId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C17828";
    String encodedClassId = null;
    encodedClassId = URLEncoder.encode(classId, StandardCharsets.UTF_8);
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES + "/" + encodedClassId + "/" + BP_PARENTS;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns the expected parent
    List<OntologyClass> parents = response.readEntity(new GenericType<List<OntologyClass>>() {
    });
    response.close();
    Assertions.assertTrue( !parents.isEmpty(),"No parents returned");
    boolean parentFound = false;
    for (OntologyClass c : parents) {
      if (c.getLdId().equals(parentClassId)) {
        parentFound = true;
        break;
      }
    }
    Assertions.assertTrue( parentFound,"Parent " + parentClassId + " not found for the given class " + classId);
  }

  @Test
  public void findAllProvisionalClassesTest() {
    String url = baseUrlBp + "/" + BP_PROVISIONAL_CLASSES;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the array returned is not empty
    PagedResults<OntologyClass> results = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {
    });
    response.close();
    Assertions.assertTrue( !results.getCollection().isEmpty(),"Empty array returned");
    // Check that the classes returned are provisional
    for (OntologyClass pc : results.getCollection()) {
      Assertions.assertTrue( pc.isProvisional(),"Provisional class expected, but non provisional class found");
    }
  }

  @Test
  public void findAllProvisionalClassesForOntologyTest() {
    // Create a provisional class
    OntologyClass createdClass = createClass(class1);
    String url =
        baseUrlBpOntologies + "/" + Util.getShortIdentifier(createdClass.getOntology()) + "/" + BP_PROVISIONAL_CLASSES;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the array returned is not empty
    PagedResults<OntologyClass> results = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {
    });
    response.close();
    Assertions.assertTrue( !results.getCollection().isEmpty(),"Empty array returned");
    // Check that the classes returned are provisional
    for (OntologyClass pc : results.getCollection()) {
      Assertions.assertTrue( pc.isProvisional(),"Provisional class expected, but non provisional class found");
    }
  }

  @Test
  public void updateClassTest() {
    // Create a provisional class
    OntologyClass createdClass = createClass(class1);
    OntologyClass updatedClass = new OntologyClass(createdClass.getId(), createdClass.getLdId(), "new label",
        createdClass.getCreator(), createdClass.getOntology(), createdClass.getDefinitions(),
        createdClass.getSynonyms(),
        createdClass.getSubclassOf(), createdClass.getRelations(), createdClass.isProvisional(),
        createdClass.getCreated(),
        createdClass.getHasChildren());
    String url = baseUrlBp + "/" + BP_CLASSES + "/" + createdClass.getId();
    // Service invocation
    Response updateResponse = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).put(Entity.json
        (updatedClass));
    // Check HTTP response
    Assertions.assertEquals(Status.NO_CONTENT.getStatusCode(), updateResponse.getStatus());
    // Retrieve the class
    String findUrl = baseUrlBpOntologies + "/" + Util.getShortIdentifier(createdClass.getOntology())
        + "/" + BP_CLASSES + "/" + createdClass.getId();
    Response findResponse = clientBuilder.build().target(findUrl).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).get();
    OntologyClass found = findResponse.readEntity(OntologyClass.class);
    findResponse.close();
    // Check that the modifications have been done correctly
    OntologyClass expected = updatedClass;
    Assertions.assertEquals(expected.getId(), found.getId());
    Assertions.assertEquals(expected.getLdId(), found.getLdId());
    Assertions.assertEquals(expected.getPrefLabel(), found.getPrefLabel());
    Assertions.assertEquals(expected.getCreator(), found.getCreator());
    Assertions.assertEquals(expected.getOntology(), found.getOntology());
    Assertions.assertTrue(expected.getDefinitions().containsAll(found.getDefinitions()) && found.getDefinitions().containsAll(expected.getDefinitions()));
    Assertions.assertTrue(expected.getSynonyms().containsAll(found.getSynonyms()) && found.getSynonyms().containsAll(expected.getSynonyms()));
    Assertions.assertEquals(expected.getSubclassOf(), found.getSubclassOf());
    Assertions.assertEquals(expected.getRelations(), found.getRelations());
    Assertions.assertEquals(expected.isProvisional(), found.isProvisional());
    Assertions.assertEquals(expected.getCreated(), found.getCreated());
    Assertions.assertEquals(expected.getHasChildren(), found.getHasChildren());
  }

  @Test
  public void deleteClassTest() {
    // Create a provisional class
    OntologyClass createdClass = createClass(class1);
    // Delete the class that has been created
    String classUrl = baseUrlBp + "/" + BP_CLASSES + "/" + createdClass.getId();
    Response deleteResponse = clientBuilder.build().target(classUrl).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).delete();
    // Check HTTP response
    Assertions.assertEquals(Status.NO_CONTENT.getStatusCode(), deleteResponse.getStatus());
    // Remove class from the list of created classes
    createdClasses.remove(createdClass);
    // Try to retrieve the class to check that it has been deleted correctly
    String findUrl =
        baseUrlBpOntologies + "/" + Util.getShortIdentifier(createdClass.getOntology()) + "/" + BP_CLASSES + "/" + createdClass.getId();
    Response findResponse = clientBuilder.build().target(findUrl).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).get();
    // Check not found
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(), findResponse.getStatus());
  }

}
