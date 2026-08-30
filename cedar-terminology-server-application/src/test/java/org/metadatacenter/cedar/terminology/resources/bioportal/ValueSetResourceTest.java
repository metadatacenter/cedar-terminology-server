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
import org.metadatacenter.terms.domainObjects.ValueSet;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.metadatacenter.cedar.terminology.utils.Constants.BP_TREE;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_VALUES;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_VALUE_SETS;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests for the five value-set endpoints, done by starting a test server so the real
 * HTTP stack is exercised.
 *
 * <p>Fixtures come from two of the three readable value-set collections, because the endpoints do
 * not all take the same path through the service. CEDARVS, the collection CEDAR publishes, supplies
 * the "Study_File_Type" value set the endpoints' own API documentation gives as an example. The one
 * endpoint that resolves a value's containing value set goes through {@code findValueSetByValue},
 * which treats every CEDARVS value as provisional and so cannot serve a published one; it is tested
 * against NLMVS, whose values take the other branch, and the CEDARVS failure is pinned separately
 * below.
 *
 * <p>Membership assertions are containment and a floor on the total rather than set equality, so a
 * value set added upstream does not fail the suite.
 */
// Exercises live BioPortal; excluded from the default build (surefire excludedGroups).
// Run with -DexcludedGroups= to clear the exclusion when a BioPortal API key is configured.
@Tag("bioportal")
public class ValueSetResourceTest extends AbstractTerminologyServerResourceTest {

  private static final String CEDAR_VS = "CEDARVS";
  private static final String CEDARVS_IRI = "http://www.semanticweb.org/jgraybeal/ontologies/2015/7/cedarvaluesets#";
  private static final String STUDY_FILE_TYPE = CEDARVS_IRI + "Study_File_Type";
  private static final String STUDY_MEDICATION = CEDARVS_IRI + "Study_Medication";
  /** Value sets that have been stable since CEDARVS was published. */
  private static final Set<String> KNOWN_VALUE_SETS =
      Set.of("Study_File_Type", "Study_Type", "Role_In_Study", "Criterion_Category", "Titration_Process");

  private static final String NLM_VS = "NLMVS";
  /** "Fluoride Varnish Application for Children", an NLMVS value set with six members. */
  private static final String FLUORIDE_VARNISH =
      "http://purl.bioontology.org/ontology/NLMVS/2.16.840.1.113883.3.464.1003.125.12.1002";
  /**
   * A member of that value set. NLMVS draws its values from other ontologies, and this one is
   * reached through exactly one value set, so the first parent {@code findValueSetByValue} keeps is
   * unambiguous.
   */
  private static final String FLUORIDE_VARNISH_VALUE = "http://purl.bioontology.org/ontology/CDT/D1206";

  private static String encode(String iri) {
    return URLEncoder.encode(iri, StandardCharsets.UTF_8);
  }

  private String valueSetsUrl(String vsCollection) {
    return baseUrlBpVSCollections + "/" + vsCollection + "/" + BP_VALUE_SETS;
  }

  private String valueSetUrl(String vsCollection, String valueSetIri) {
    return valueSetsUrl(vsCollection) + "/" + encode(valueSetIri);
  }

  private String valueSetByValueUrl(String vsCollection, String valueIri) {
    return baseUrlBpVSCollections + "/" + vsCollection + "/" + BP_VALUES + "/" + encode(valueIri) + "/value-set";
  }

  private Response get(String url) {
    return clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
  }

  @Test
  public void findValueSetTest() {
    Response response = get(valueSetUrl(CEDAR_VS, STUDY_FILE_TYPE));
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    ValueSet valueSet = response.readEntity(ValueSet.class);
    response.close();
    Assertions.assertEquals(STUDY_FILE_TYPE, valueSet.getLdId(), "Wrong value set IRI");
    Assertions.assertEquals("Study_File_Type", valueSet.getId(), "Wrong short identifier");
    Assertions.assertEquals("Study_File_Type", valueSet.getPrefLabel(), "Wrong preferred label");
    Assertions.assertFalse(valueSet.isProvisional(), "A CEDARVS value set is not provisional");
  }

  @Test
  public void findValueSetsByVsCollectionTest() {
    Response response = get(valueSetsUrl(CEDAR_VS));
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    PagedResults<ValueSet> valueSets = response.readEntity(new GenericType<PagedResults<ValueSet>>() {});
    response.close();
    Set<String> found = valueSets.getCollection().stream().map(ValueSet::getId).collect(Collectors.toSet());
    Assertions.assertTrue(found.containsAll(KNOWN_VALUE_SETS), "Value sets missing from CEDARVS: " + found);
  }

  @Test
  public void findValueSetsByVsCollectionHonoursPageSizeTest() {
    Response response = get(valueSetsUrl(CEDAR_VS) + "?page=1&pageSize=2");
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    PagedResults<ValueSet> valueSets = response.readEntity(new GenericType<PagedResults<ValueSet>>() {});
    response.close();
    Assertions.assertEquals(1, valueSets.getPage(), "Wrong page reported");
    Assertions.assertEquals(2, valueSets.getCollection().size(), "The page size was not applied");
    Assertions.assertTrue(valueSets.getPageCount() > 1,
        "A 2-value-set page over " + valueSets.getTotalCount() + " value sets should report more than one page");
  }

  @Test
  public void findValueSetByValueTest() {
    Response response = get(valueSetByValueUrl(NLM_VS, FLUORIDE_VARNISH_VALUE));
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    ValueSet valueSet = response.readEntity(ValueSet.class);
    response.close();
    Assertions.assertEquals(FLUORIDE_VARNISH, valueSet.getLdId(),
        "The value belongs to the fluoride varnish value set, not " + valueSet.getLdId());
  }

  /**
   * Pins a defect rather than the intended behaviour: this endpoint cannot resolve a published
   * CEDARVS value.
   *
   * <p>{@code TerminologyService.findValueSetByValue} branches on the collection and, for CEDARVS
   * alone, looks the value up as a provisional class. CEDARVS holds published values too — every
   * member of Study_File_Type is one — so the lookup finds nothing and the endpoint answers 404 for
   * a value {@code ValueResourceTest.findValueTest} retrieves without trouble. Replace this with
   * the NLMVS assertion above when the branch is fixed.
   */
  @Test
  public void findValueSetByValueFailsForAPublishedCedarvsValueTest() {
    Response response = get(valueSetByValueUrl(CEDAR_VS, STUDY_MEDICATION));
    int status = response.getStatus();
    response.close();
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(), status,
        "A published CEDARVS value now resolves to its value set; assert that instead of the 404");
  }

  /**
   * The endpoint refuses an anonymous caller.
   *
   * <p>It was for a long time the one route across both value resources that never built a request
   * context, so it asked no user to be logged in and spent the server's own BioPortal key on behalf
   * of whoever called. The gate is also probed without BioPortal, alongside its eight siblings, by
   * {@code TerminologyServerApplicationSmokeTest.everyValueAndValueSetRouteRequiresAuthentication};
   * this asserts it on the collection the endpoint can actually serve, so a gate that passed the
   * probe by failing earlier would still be caught.
   */
  @Test
  public void findValueSetByValueRequiresAuthenticationTest() {
    Response response = clientBuilder.build().target(valueSetByValueUrl(NLM_VS, FLUORIDE_VARNISH_VALUE))
        .request().get();
    int status = response.getStatus();
    response.close();
    Assertions.assertEquals(Status.UNAUTHORIZED.getStatusCode(), status,
        "An anonymous caller should be refused before any BioPortal call");
  }

  @Test
  public void findValueSetTreeTest() {
    Response response = get(valueSetUrl(CEDAR_VS, STUDY_FILE_TYPE) + "/" + BP_TREE);
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    TreeNode tree = response.readEntity(TreeNode.class);
    response.close();
    Assertions.assertEquals(STUDY_FILE_TYPE, tree.getLdId(), "The tree should be rooted at the value set");
    Assertions.assertTrue(tree.getHasChildren(), "Study_File_Type should report that it has children");
    Set<String> children = tree.getChildren().stream().map(TreeNode::getLdId).collect(Collectors.toSet());
    Assertions.assertTrue(children.contains(STUDY_MEDICATION),
        "The value set's tree omits one of its values: " + children);
  }

  @Test
  public void findAllValueSetsTest() {
    String url = baseUrlBp + "/" + BP_VALUE_SETS;
    Response response = get(url);
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    List<ValueSet> valueSets = response.readEntity(new GenericType<List<ValueSet>>() {});
    response.close();
    Assertions.assertFalse(valueSets.isEmpty(), "No value sets were returned");
    Set<String> found = valueSets.stream().map(ValueSet::getId).collect(Collectors.toSet());
    Assertions.assertTrue(found.containsAll(KNOWN_VALUE_SETS),
        "The unscoped listing omits CEDARVS value sets: " + found);
  }

}
