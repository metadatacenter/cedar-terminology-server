package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.ValueSet;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.metadatacenter.cedar.terminology.resources.FakeBioPortal.*;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_VALUES;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_VALUE_SETS;

/**
 * The value-set routes: a value set read alone, a collection's value sets a page at a time, every value set of every
 * collection, and the gate of the route that resolves a value's containing value set.
 *
 * <p>The value sets read here come from CEDARVS, the collection CEDAR publishes. The route that resolves a value's
 * containing value set is asked of a CEDARVS value here. A collection's value sets, a value's
 * value set and a value set's tree are covered by {@code BioPortalSuccessPathsTest.valueSetsAndTheirValuesAreRead}.
 */
public class ValueSetResourceTest extends AbstractTerminologyServerResourceTest {

  private String valueSetsUrl(String vsCollection) {
    return baseUrlBpVSCollections + "/" + vsCollection + "/" + BP_VALUE_SETS;
  }

  private String valueSetByValueUrl(String vsCollection, String valueIri) {
    return baseUrlBpVSCollections + "/" + vsCollection + "/" + BP_VALUES + "/" + encode(valueIri) + "/value-set";
  }

  @Test
  public void findValueSetTest() {
    ValueSet valueSet = read(valueSetsUrl(CEDARVS) + "/" + encode(STUDY_FILE_TYPE), ValueSet.class);
    Assertions.assertEquals(STUDY_FILE_TYPE, valueSet.getLdId(), "Wrong value set IRI");
    Assertions.assertEquals("Study_File_Type", valueSet.getId(), "Wrong short identifier");
    Assertions.assertEquals("Study File Type", valueSet.getPrefLabel(), "Wrong preferred label");
    Assertions.assertFalse(valueSet.isProvisional(), "A CEDARVS value set is not provisional");
  }

  @Test
  public void findValueSetsByVsCollectionHonoursPageSizeTest() {
    PagedResults<ValueSet> valueSets = read(valueSetsUrl(CEDARVS) + "?page=1&pageSize=2", new GenericType<>() {});
    Assertions.assertEquals(List.of(STUDY_FILE_TYPE, STUDY_TYPE),
        valueSets.getCollection().stream().map(ValueSet::getLdId).toList(), "The page size was not applied");
    assertPaging(valueSets, 1, 2, 2, 3, null, 2);
  }

  /**
   * A published CEDARVS value resolves to the value set holding it. The lookup once treated every CEDARVS value as
   * provisional, which no member of Study File Type is, so the route answered 404.
   */
  @Test
  public void findValueSetByValueTest() {
    ValueSet valueSet = read(valueSetByValueUrl(CEDARVS, STUDY_MEDICATION), ValueSet.class);
    Assertions.assertEquals(STUDY_FILE_TYPE, valueSet.getLdId(), "Wrong value set IRI");
    Assertions.assertEquals("Study File Type", valueSet.getPrefLabel(), "Wrong preferred label");
    Assertions.assertFalse(valueSet.isProvisional(), "A CEDARVS value set is not provisional");
  }

  /** A value set has no parent, so no value set holds it, and the route answers 404 rather than failing. */
  @Test
  public void findValueSetByValueOfAValueSetIsNotFoundTest() {
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(), statusOf(valueSetByValueUrl(CEDARVS, STUDY_FILE_TYPE)));
  }

  /**
   * The route refuses an anonymous caller.
   *
   * <p>It was for a long time the one route across both value resources that never built a request context, so it
   * asked no user to be logged in and spent the server's own BioPortal key on behalf of whoever called. The gate is
   * also probed without BioPortal, alongside every other gated route, by
   * {@code TerminologyServerApplicationSmokeTest.everyTerminologyRouteRequiresAuthentication}. This asserts it on a
   * collection the route can serve, so a gate that passed the probe by failing earlier would still be caught.
   */
  @Test
  public void findValueSetByValueRequiresAuthenticationTest() {
    try (Response response = client.target(valueSetByValueUrl(NLMVS, MRNA_VACCINE)).request().get()) {
      Assertions.assertEquals(Status.UNAUTHORIZED.getStatusCode(), response.getStatus(),
          "An anonymous caller should be refused before any BioPortal call");
    }
  }

  @Test
  public void findAllValueSetsTest() {
    List<ValueSet> valueSets = read(baseUrlBp + "/" + BP_VALUE_SETS, new GenericType<>() {});
    Set<String> found = valueSets.stream().map(ValueSet::getLdId).collect(Collectors.toSet());
    // The listing reads every collection.
    Assertions.assertEquals(Set.of(VALUE_SET, STUDY_FILE_TYPE, STUDY_TYPE, ROLE_IN_STUDY, CORONAVIRUS_VACCINES), found);
  }

}
