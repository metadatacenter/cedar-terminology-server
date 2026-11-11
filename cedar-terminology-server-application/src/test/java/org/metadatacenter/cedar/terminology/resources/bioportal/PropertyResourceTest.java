package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyProperty;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.util.Util;

import java.util.List;

import static org.metadatacenter.cedar.terminology.resources.FakeBioPortal.*;
import static org.metadatacenter.cedar.terminology.utils.Constants.*;

/**
 * The property routes: a property found by a search and then read, a property's tree, and its descendants. An
 * ontology's properties and a property's children and parents are covered by
 * {@code BioPortalSuccessPathsTest.aPropertyIsReadWithItsHierarchy}.
 */
public class PropertyResourceTest extends AbstractTerminologyServerResourceTest {

  private String propertyUrl(String ontology, String propertyIri) {
    return baseUrlBpOntologies + "/" + ontology + "/" + BP_PROPERTIES + "/" + encode(propertyIri);
  }

  @Test
  public void findPropertyTest() {
    // A property found by a search is read again from the source the search names.
    PagedResults<SearchResult> properties =
        read(baseUrlBpPropertySearch + "?q=" + encode("has host"), new GenericType<>() {});
    Assertions.assertFalse(properties.getCollection().isEmpty(), "No properties found to perform the test");
    SearchResult p = properties.getCollection().get(0);
    OntologyProperty found =
        read(propertyUrl(Util.getShortIdentifier(p.getSource()), p.getId()), OntologyProperty.class);
    Assertions.assertEquals(HAS_HOST, p.getId());
    Assertions.assertEquals(p.getId(), found.getId(), "Wrong property id");
    Assertions.assertEquals("has host", found.getPrefLabel());
  }

  @Test
  public void findPropertyTreeTest() {
    List<TreeNode> tree = read(propertyUrl(VIRUSES, HAS_HUMAN_HOST) + "/" + BP_TREE, new GenericType<>() {});
    Assertions.assertEquals(List.of(RELATED_TO, HAS_HOST_RANGE), tree.stream().map(TreeNode::getLdId).toList(),
        "The tree should list the ontology's root properties");
    TreeNode relatedTo = tree.get(0);
    Assertions.assertTrue(relatedTo.getHasChildren(), "related to should report that it has children");
    TreeNode hasHost = child(relatedTo, HAS_HOST);
    Assertions.assertEquals("has host", hasHost.getPrefLabel());
    Assertions.assertEquals("has human host", child(hasHost, HAS_HUMAN_HOST).getPrefLabel());
  }

  @Test
  public void findPropertyDescendantsTest() {
    List<OntologyProperty> descendants =
        read(propertyUrl(VIRUSES, RELATED_TO) + "/" + BP_DESCENDANTS, new GenericType<>() {});
    // The descendants reach the grandchild has human host as well as the child has host.
    Assertions.assertEquals(List.of(HAS_HOST, HAS_HUMAN_HOST),
        descendants.stream().map(OntologyProperty::getLdId).toList());
  }

}
