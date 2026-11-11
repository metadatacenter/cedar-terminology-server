package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.Value;

import java.util.List;

import static org.metadatacenter.cedar.terminology.resources.FakeBioPortal.*;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_ALL_VALUES;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_TREE;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_VALUES;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_VALUE_SETS;

/**
 * The value routes: a value read alone, a value set's values a page at a time, and every value of a value set
 * reached through one of them.
 *
 * <p>The routes do not all take the same path through the service. A value and a page of values are read from
 * CEDARVS, the collection CEDAR publishes. The routes that resolve a value's containing value set go through
 * {@code findValueSetByValue}, which treats every CEDARVS value as provisional and so cannot serve a published one.
 * They are read from NLMVS, whose values take the other branch, and the CEDARVS failure is pinned separately below.
 * A value's tree and a value set's values are covered by {@code BioPortalSuccessPathsTest.valueSetsAndTheirValuesAreRead}.
 */
public class ValueResourceTest extends AbstractTerminologyServerResourceTest {

  private String valueUrl(String vsCollection, String valueIri) {
    return baseUrlBpVSCollections + "/" + vsCollection + "/" + BP_VALUES + "/" + encode(valueIri);
  }

  private String valueSetUrl(String vsCollection, String valueSetIri) {
    return baseUrlBpVSCollections + "/" + vsCollection + "/" + BP_VALUE_SETS + "/" + encode(valueSetIri);
  }

  private static List<String> ldIds(PagedResults<Value> values) {
    return values.getCollection().stream().map(Value::getLdId).toList();
  }

  @Test
  public void findValueTest() {
    Value value = read(valueUrl(CEDARVS, STUDY_MEDICATION), Value.class);
    Assertions.assertEquals(STUDY_MEDICATION, value.getLdId(), "Wrong value IRI");
    Assertions.assertEquals("Study_Medication", value.getId(), "Wrong short identifier");
    Assertions.assertEquals("Study Medication", value.getPrefLabel(), "Wrong preferred label");
    Assertions.assertFalse(value.isProvisional(), "A published CEDARVS value is not provisional");
  }

  @Test
  public void findValuesByValueSetHonoursPageSizeTest() {
    PagedResults<Value> values =
        read(valueSetUrl(CEDARVS, STUDY_FILE_TYPE) + "/" + BP_VALUES + "?page=1&pageSize=3", new GenericType<>() {});
    Assertions.assertEquals(List.of("Study_Medication", "Adverse_Events", "Demographics"),
        values.getCollection().stream().map(Value::getId).toList(), "The page size was not applied");
    assertPaging(values, 1, 2, 3, 4, null, 2);
  }

  @Test
  public void findAllValuesInValueSetByValueTest() {
    PagedResults<Value> fromValue = read(valueUrl(NLMVS, MRNA_VACCINE) + "/" + BP_ALL_VALUES, new GenericType<>() {});
    // Reaching the values through one of them and through the value set itself must give the same answer. The
    // route exists only to save the caller the first hop.
    PagedResults<Value> fromValueSet =
        read(valueSetUrl(NLMVS, CORONAVIRUS_VACCINES) + "/" + BP_VALUES, new GenericType<>() {});
    Assertions.assertEquals(List.of(MRNA_VACCINE, VECTOR_VACCINE, INACTIVATED_VACCINE), ldIds(fromValue));
    Assertions.assertEquals(ldIds(fromValueSet), ldIds(fromValue),
        "Reaching the values by value and by value set disagreed");
  }

  /**
   * Pins a defect rather than the intended behaviour: neither route that resolves a value's containing value set
   * can serve a published CEDARVS value.
   *
   * <p>Both call {@code TerminologyService.findValueSetByValue}, which branches on the collection and, for CEDARVS
   * alone, looks the value up as a provisional class. CEDARVS holds published values too — every member of Study
   * File Type is one — and for those the provisional lookup finds nothing, so the routes answer 404 for a value that
   * {@link #findValueTest()} retrieves without trouble. The same two routes answer for NLMVS, which takes the other
   * branch. Replace these assertions with ones like the NLMVS assertions when the branch is fixed.
   */
  @Test
  public void valueSetResolutionFailsForAPublishedCedarvsValueTest() {
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(), statusOf(valueUrl(CEDARVS, STUDY_MEDICATION) + "/" + BP_TREE),
        "The value tree now resolves for a published CEDARVS value; assert the NLMVS behaviour instead");
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(),
        statusOf(valueUrl(CEDARVS, STUDY_MEDICATION) + "/" + BP_ALL_VALUES),
        "The sibling values now resolve for a published CEDARVS value; assert the NLMVS behaviour instead");
  }

}
