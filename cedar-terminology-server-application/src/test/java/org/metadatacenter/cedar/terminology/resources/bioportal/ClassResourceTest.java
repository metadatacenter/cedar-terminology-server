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
import org.junit.jupiter.api.Tag;
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
// Exercises live BioPortal; excluded from the default build (surefire excludedGroups).
// Run with -DexcludedGroups= (or a bioportal profile) when a BioPortal API key is configured.
@Tag("bioportal")
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

  /* ------------------------------------------------------------------------------------------------
   * The two provisional-class reads. Provisional classes are user-created and BioPortal holds
   * thousands of them, so these assert the shape of the answer and the scoping, never a particular
   * class: any assertion naming one would fail the day its author deleted it.
   * --------------------------------------------------------------------------------------------- */

  @Test
  public void findAllProvisionalClassesTest() {
    String url = baseUrlBp + "/" + BP_PROVISIONAL_CLASSES + "?page=1&pageSize=5";
    Response response =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    PagedResults<OntologyClass> classes = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {
    });
    response.close();
    Assertions.assertTrue(classes.getTotalCount() > 0, "No provisional classes were returned");
    Assertions.assertEquals(5, classes.getCollection().size(), "The page size was not applied");
    Assertions.assertTrue(classes.getPageCount() > 1,
        "A 5-class page over " + classes.getTotalCount() + " classes should report more than one page");
    for (OntologyClass c : classes.getCollection()) {
      Assertions.assertTrue(c.isProvisional(), "A provisional listing returned a published class: " + c.getLdId());
    }
  }

  @Test
  public void findAllProvisionalClassesForOntologyTest() {
    // CEDARPC is where the Workbench put every class a user created, so it is the one ontology whose
    // provisional listing is reliably non-empty.
    String ontology = "CEDARPC";
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_PROVISIONAL_CLASSES + "?page=1&pageSize=5";
    Response response =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    PagedResults<OntologyClass> classes = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {
    });
    response.close();
    Assertions.assertTrue(classes.getTotalCount() > 0, "No provisional classes were returned for " + ontology);
    for (OntologyClass c : classes.getCollection()) {
      Assertions.assertTrue(c.isProvisional(), "A provisional listing returned a published class: " + c.getLdId());
      // A handful of provisional classes carry no ontology upstream, so this reads the field where
      // BioPortal set one rather than requiring it. The scoping itself is asserted below.
      if (c.getOntology() != null) {
        Assertions.assertEquals(ontology, Util.getShortIdentifier(c.getOntology()),
            "A listing scoped to " + ontology + " returned a class from another ontology: " + c.getLdId());
      }
    }
  }

  @Test
  public void findAllProvisionalClassesForOntologyAppliesTheScopeTest() {
    // The scope reaches BioPortal as a different route, not as a filter applied here, so an ontology
    // nobody has added a provisional class to must come back empty rather than with the whole corpus.
    String url = baseUrlBpOntologies + "/NCIT/" + BP_PROVISIONAL_CLASSES;
    Response response =
        clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    PagedResults<OntologyClass> classes = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {
    });
    response.close();
    Assertions.assertEquals(0, classes.getTotalCount(),
        "NCIT holds no provisional classes, so a scoped listing should be empty");
  }

}
