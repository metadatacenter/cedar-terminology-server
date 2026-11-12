package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.GenericType;
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
 * {@code findValueSetByValue}, and are asked of both CEDARVS and NLMVS. A value's tree and a value set's values are covered by {@code BioPortalSuccessPathsTest.valueSetsAndTheirValuesAreRead}.
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
   * A published CEDARVS value resolves through its value set, as an NLMVS value does. The lookup once treated every
   * CEDARVS value as provisional, which no member of Study File Type is, so both routes answered 404.
   */
  @Test
  public void aPublishedCedarvsValueResolvesThroughItsValueSetTest() {
    PagedResults<Value> fromValue =
        read(valueUrl(CEDARVS, STUDY_MEDICATION) + "/" + BP_ALL_VALUES, new GenericType<>() {});
    PagedResults<Value> fromValueSet =
        read(valueSetUrl(CEDARVS, STUDY_FILE_TYPE) + "/" + BP_VALUES, new GenericType<>() {});
    Assertions.assertTrue(ldIds(fromValue).contains(STUDY_MEDICATION), "The value is missing from its own value set");
    Assertions.assertEquals(ldIds(fromValueSet), ldIds(fromValue),
        "Reaching the values by value and by value set disagreed");

    JsonNode tree = read(valueUrl(CEDARVS, STUDY_MEDICATION) + "/" + BP_TREE, JsonNode.class);
    Assertions.assertEquals("Study File Type", tree.get("prefLabel").asText(), tree.toString());
    Assertions.assertEquals(ldIds(fromValueSet).size(), tree.get("children").size(), tree.toString());
  }

}
