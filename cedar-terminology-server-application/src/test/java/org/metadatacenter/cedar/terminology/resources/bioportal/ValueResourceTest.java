package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.domainObjects.Value;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.metadatacenter.cedar.terminology.utils.Constants.BP_ALL_VALUES;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_TREE;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_VALUES;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_VALUE_SETS;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests for the four value endpoints, done by starting a test server so the real HTTP
 * stack is exercised.
 *
 * <p>Fixtures come from two of the three readable value-set collections, because the endpoints do
 * not all take the same path through the service. CEDARVS, the collection CEDAR publishes, supplies
 * the "Study_File_Type" value set and its member "Study_Medication" — the examples the endpoints'
 * own API documentation gives. The two endpoints that resolve a value's containing value set go
 * through {@code findValueSetByValue}, which treats every CEDARVS value as provisional and so
 * cannot serve a regular one; they are tested against NLMVS, whose values take the other branch,
 * and the CEDARVS failure is pinned separately below.
 *
 * <p>Membership assertions are containment and a floor on the total rather than set equality, so a
 * value added upstream does not fail the suite.
 */
// Exercises live BioPortal; excluded from the default build (surefire excludedGroups).
// Run with -DexcludedGroups= to clear the exclusion when a BioPortal API key is configured.
@Tag("bioportal")
public class ValueResourceTest extends AbstractTerminologyServerResourceTest {

  private static final String CEDAR_VS = "CEDARVS";
  private static final String CEDARVS_IRI = "http://www.semanticweb.org/jgraybeal/ontologies/2015/7/cedarvaluesets#";
  private static final String STUDY_FILE_TYPE = CEDARVS_IRI + "Study_File_Type";
  private static final String STUDY_MEDICATION = CEDARVS_IRI + "Study_Medication";
  /** Members of Study_File_Type that have been stable since the collection was published. */
  private static final Set<String> STUDY_FILE_TYPE_MEMBERS =
      Set.of("Study_Medication", "Adverse_Events", "Demographics", "Lab_Test_Results");
  private static final int STUDY_FILE_TYPE_MEMBER_COUNT = 12;

  private static final String NLM_VS = "NLMVS";
  /** "Fluoride Varnish Application for Children", an NLMVS value set with six members. */
  private static final String FLUORIDE_VARNISH =
      "http://purl.bioontology.org/ontology/NLMVS/2.16.840.1.113883.3.464.1003.125.12.1002";
  /**
   * "topical application of fluoride varnish", a member of that value set. NLMVS draws its values
   * from other ontologies, and this one is reached through exactly one value set, so the first
   * parent {@code findValueSetByValue} keeps is unambiguous.
   */
  private static final String FLUORIDE_VARNISH_VALUE = "http://purl.bioontology.org/ontology/CDT/D1206";
  private static final Set<String> FLUORIDE_VARNISH_MEMBERS =
      Set.of("D1206", "D1208", "234723000", "313042009", "35889000", "70468009");

  private static String encode(String iri) {
    return URLEncoder.encode(iri, StandardCharsets.UTF_8);
  }

  private String valueUrl(String vsCollection, String valueIri) {
    return baseUrlBpVSCollections + "/" + vsCollection + "/" + BP_VALUES + "/" + encode(valueIri);
  }

  private String valueSetUrl(String vsCollection, String valueSetIri) {
    return baseUrlBpVSCollections + "/" + vsCollection + "/" + BP_VALUE_SETS + "/" + encode(valueSetIri);
  }

  private Response get(String url) {
    return clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
  }

  private static Set<String> shortIds(PagedResults<Value> values) {
    return values.getCollection().stream().map(Value::getId).collect(Collectors.toSet());
  }

  @Test
  public void findValueTest() {
    Response response = get(valueUrl(CEDAR_VS, STUDY_MEDICATION));
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    Value value = response.readEntity(Value.class);
    response.close();
    Assertions.assertEquals(STUDY_MEDICATION, value.getLdId(), "Wrong value IRI");
    Assertions.assertEquals("Study_Medication", value.getId(), "Wrong short identifier");
    Assertions.assertEquals("Study_Medication", value.getPrefLabel(), "Wrong preferred label");
    Assertions.assertFalse(value.isProvisional(), "A published CEDARVS value is not provisional");
  }

  @Test
  public void findValuesByValueSetTest() {
    Response response = get(valueSetUrl(CEDAR_VS, STUDY_FILE_TYPE) + "/" + BP_VALUES);
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    PagedResults<Value> values = response.readEntity(new GenericType<PagedResults<Value>>() {});
    response.close();
    Assertions.assertTrue(values.getTotalCount() >= STUDY_FILE_TYPE_MEMBER_COUNT,
        "Study_File_Type should hold at least " + STUDY_FILE_TYPE_MEMBER_COUNT + " values, got "
            + values.getTotalCount());
    Assertions.assertTrue(shortIds(values).containsAll(STUDY_FILE_TYPE_MEMBERS),
        "Values missing from the value set: " + shortIds(values));
  }

  @Test
  public void findValuesByValueSetHonoursPageSizeTest() {
    Response response = get(valueSetUrl(CEDAR_VS, STUDY_FILE_TYPE) + "/" + BP_VALUES + "?page=1&pageSize=5");
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    PagedResults<Value> values = response.readEntity(new GenericType<PagedResults<Value>>() {});
    response.close();
    Assertions.assertEquals(1, values.getPage(), "Wrong page reported");
    Assertions.assertEquals(5, values.getCollection().size(), "The page size was not applied");
    Assertions.assertTrue(values.getPageCount() > 1,
        "A 5-value page over " + values.getTotalCount() + " values should report more than one page");
  }

  @Test
  public void findValueTreeTest() {
    Response response = get(valueUrl(NLM_VS, FLUORIDE_VARNISH_VALUE) + "/" + BP_TREE);
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    TreeNode tree = response.readEntity(TreeNode.class);
    response.close();
    // The tree of a value is its containing value set expanded one level, not the value itself.
    Assertions.assertEquals(FLUORIDE_VARNISH, tree.getLdId(),
        "The tree should be rooted at the containing value set");
    Assertions.assertTrue(tree.getHasChildren(), "The containing value set should report that it has children");
    Set<String> children = tree.getChildren().stream().map(TreeNode::getLdId).collect(Collectors.toSet());
    Assertions.assertTrue(children.contains(FLUORIDE_VARNISH_VALUE),
        "The tree omits the value it was asked about: " + children);
  }

  @Test
  public void findAllValuesInValueSetByValueTest() {
    Response byValue = get(valueUrl(NLM_VS, FLUORIDE_VARNISH_VALUE) + "/" + BP_ALL_VALUES);
    Assertions.assertEquals(Status.OK.getStatusCode(), byValue.getStatus());
    Assertions.assertEquals(MediaType.APPLICATION_JSON, byValue.getHeaderString(HttpHeaders.CONTENT_TYPE));
    PagedResults<Value> fromValue = byValue.readEntity(new GenericType<PagedResults<Value>>() {});
    byValue.close();

    // Reaching the values through one of them and through the value set itself must give the same
    // answer: this endpoint exists only to save the caller the first hop.
    Response byValueSet = get(valueSetUrl(NLM_VS, FLUORIDE_VARNISH) + "/" + BP_VALUES);
    Assertions.assertEquals(Status.OK.getStatusCode(), byValueSet.getStatus());
    PagedResults<Value> fromValueSet = byValueSet.readEntity(new GenericType<PagedResults<Value>>() {});
    byValueSet.close();

    Assertions.assertTrue(shortIds(fromValue).containsAll(FLUORIDE_VARNISH_MEMBERS),
        "Values missing from the sibling list: " + shortIds(fromValue));
    Assertions.assertEquals(shortIds(fromValueSet), shortIds(fromValue),
        "Reaching the values by value and by value set disagreed");
  }

  /**
   * Pins a defect rather than the intended behaviour: neither endpoint that resolves a value's
   * containing value set can serve a published CEDARVS value.
   *
   * <p>Both call {@code TerminologyService.findValueSetByValue}, which branches on the collection
   * and, for CEDARVS alone, looks the value up as a provisional class. CEDARVS holds published
   * values too — every member of Study_File_Type is one — and for those the provisional lookup
   * finds nothing, so the endpoints answer 404 for a value that {@link #findValueTest()} retrieves
   * without trouble. The same two endpoints answer correctly for NLMVS, which takes the other
   * branch. Replace these assertions with the NLMVS ones when the branch is fixed.
   */
  @Test
  public void valueSetResolutionFailsForAPublishedCedarvsValueTest() {
    Response tree = get(valueUrl(CEDAR_VS, STUDY_MEDICATION) + "/" + BP_TREE);
    int treeStatus = tree.getStatus();
    tree.close();
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(), treeStatus,
        "The value tree now resolves for a published CEDARVS value; assert the NLMVS behaviour instead");

    Response allValues = get(valueUrl(CEDAR_VS, STUDY_MEDICATION) + "/" + BP_ALL_VALUES);
    int allValuesStatus = allValues.getStatus();
    allValues.close();
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(), allValuesStatus,
        "The sibling values now resolve for a published CEDARVS value; assert the NLMVS behaviour instead");
  }

}
