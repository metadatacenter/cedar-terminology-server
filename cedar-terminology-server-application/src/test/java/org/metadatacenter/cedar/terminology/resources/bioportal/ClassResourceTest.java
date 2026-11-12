package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.util.Util;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.metadatacenter.cedar.terminology.resources.FakeBioPortal.*;
import static org.metadatacenter.cedar.terminology.utils.Constants.*;

/**
 * The class routes: an ontology's classes a page at a time, a class's tree and descendants, and the provisional
 * classes. A class's children and parents are covered by {@code BioPortalSuccessPathsTest.aClassIsReadWithItsHierarchy}.
 */
public class ClassResourceTest extends AbstractTerminologyServerResourceTest {

  private String classUrl(String ontology, String classIri) {
    return baseUrlBpOntologies + "/" + ontology + "/" + BP_CLASSES + "/" + encode(classIri);
  }

  @Test
  public void findAllClassesForOntologyTest() {
    PagedResults<OntologyClass> classes =
        read(baseUrlBpOntologies + "/" + VIRUSES + "/" + BP_CLASSES + "?page=1&pageSize=4", new GenericType<>() {});
    Assertions.assertEquals(4, classes.getCollection().size(), "The page size was not applied");
    Assertions.assertEquals(9, classes.getTotalCount(), "VIRONT holds nine classes");
    Assertions.assertEquals(3, classes.getPageCount(), "Three pages of four should hold the nine classes");
    Assertions.assertEquals(2, classes.getNextPage());
  }

  @Test
  public void findClassTreeTest() {
    List<TreeNode> tree = read(classUrl(VIRUSES, HUMAN_CORONAVIRUS) + "/" + BP_TREE, new GenericType<>() {});
    Assertions.assertEquals(List.of(ORGANISM, VACCINE), tree.stream().map(TreeNode::getLdId).toList(),
        "The tree should list the ontology's roots");
    TreeNode coronavirus = child(child(tree.get(0), VIRUS), CORONAVIRUS);
    Assertions.assertEquals("Human coronavirus", child(coronavirus, HUMAN_CORONAVIRUS).getPrefLabel());
    // Only the branch down to the class is expanded, so the other root is listed with its children unread.
    TreeNode vaccine = tree.get(1);
    Assertions.assertTrue(vaccine.getHasChildren(), "Vaccine should report that it has children");
    Assertions.assertTrue(vaccine.getChildren().isEmpty(), "A root off the branch was expanded");
  }

  @Test
  public void findClassDescendantsTest() {
    PagedResults<OntologyClass> descendants =
        read(classUrl(VIRUSES, VIRUS) + "/" + BP_DESCENDANTS, new GenericType<>() {});
    // Virus has two children, and Coronavirus has two more.
    Assertions.assertEquals(Set.of(CORONAVIRUS, INFLUENZA_VIRUS, HUMAN_CORONAVIRUS, BAT_CORONAVIRUS),
        descendants.getCollection().stream().map(OntologyClass::getLdId).collect(Collectors.toSet()));
  }

  @Test
  public void findAllProvisionalClassesTest() {
    PagedResults<OntologyClass> classes =
        read(baseUrlBp + "/" + BP_PROVISIONAL_CLASSES + "?page=1&pageSize=1", new GenericType<>() {});
    Assertions.assertEquals(2, classes.getTotalCount());
    Assertions.assertEquals(1, classes.getCollection().size(), "The page size was not applied");
    Assertions.assertEquals(2, classes.getPageCount(), "A one-class page over two classes should report two pages");
    Assertions.assertTrue(classes.getCollection().get(0).isProvisional());
  }

  @Test
  public void findAllProvisionalClassesForOntologyTest() {
    PagedResults<OntologyClass> classes = read(baseUrlBpOntologies + "/" + ONTOLOGY + "/" + BP_PROVISIONAL_CLASSES
        + "?page=1&pageSize=5", new GenericType<>() {});
    Assertions.assertEquals(List.of(PROVISIONAL_CLASS, PROVISIONAL_LUNG),
        classes.getCollection().stream().map(OntologyClass::getLdId).toList());
    for (OntologyClass c : classes.getCollection()) {
      Assertions.assertTrue(c.isProvisional(), "A provisional listing returned a published class: " + c.getLdId());
      Assertions.assertEquals(ONTOLOGY, Util.getShortIdentifier(c.getOntology()),
          "A listing scoped to " + ONTOLOGY + " returned a class from another ontology: " + c.getLdId());
    }
  }

  @Test
  public void findAllProvisionalClassesForOntologyAppliesTheScopeTest() {
    // The scope reaches BioPortal as a different route, not as a filter applied here, so an ontology nobody has
    // added a provisional class to must come back empty rather than with every provisional class.
    PagedResults<OntologyClass> classes =
        read(baseUrlBpOntologies + "/" + VIRUSES + "/" + BP_PROVISIONAL_CLASSES, new GenericType<>() {});
    Assertions.assertEquals(0, classes.getTotalCount(), VIRUSES + " holds no provisional classes");
  }

}
