package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.util.Util;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.metadatacenter.cedar.terminology.resources.FakeBioPortal.*;

/**
 * Searches for classes, value sets and values, and for properties. In this world "vaccine" matches two classes of
 * VIRONT, the value set Coronavirus Vaccines and its three values, so each scope is told apart by what it leaves out.
 * A class found by its label or by a synonym, and the kind given to each result from a value-set collection, are
 * covered by {@code BioPortalSuccessPathsTest.aSearchClassifiesWhatItFinds}.
 */
public class SearchResourceTest extends AbstractTerminologyServerResourceTest {

  private static final Set<String> VACCINE_CLASSES = Set.of(VACCINE, CORONAVIRUS_VACCINE);
  private static final Set<String> VACCINE_VALUES = Set.of(MRNA_VACCINE, VECTOR_VACCINE, INACTIVATED_VACCINE);

  private static PagedResults<SearchResult> search(String query) {
    return read(baseUrlBpSearch + "?" + query, new GenericType<>() {});
  }

  private static PagedResults<SearchResult> searchProperties(String query) {
    return read(baseUrlBpPropertySearch + "?" + query, new GenericType<>() {});
  }

  private static List<String> ids(PagedResults<SearchResult> results) {
    return results.getCollection().stream().map(SearchResult::getLdId).toList();
  }

  private static Set<String> types(PagedResults<SearchResult> results) {
    return results.getCollection().stream().map(SearchResult::getType).collect(Collectors.toSet());
  }

  private static Set<String> sources(PagedResults<SearchResult> results) {
    return results.getCollection().stream().map(r -> Util.getShortIdentifier(r.getSource()))
        .collect(Collectors.toSet());
  }

  private static Set<String> union(Set<String> a, Set<String> b) {
    return Stream.concat(a.stream(), b.stream()).collect(Collectors.toSet());
  }

  @Test
  public void searchAllTest() {
    PagedResults<SearchResult> results = search("q=vaccine");
    Assertions.assertEquals(union(union(VACCINE_CLASSES, VACCINE_VALUES), Set.of(CORONAVIRUS_VACCINES)),
        Set.copyOf(ids(results)));
    Assertions.assertEquals(Set.of("OntologyClass", "ValueSet", "Value"), types(results));
  }

  @Test
  public void searchClassesTest() {
    PagedResults<SearchResult> results = search("q=vaccine&scope=classes");
    Assertions.assertEquals(VACCINE_CLASSES, Set.copyOf(ids(results)));
    Assertions.assertEquals(Set.of("OntologyClass"), types(results));
  }

  /** A class is found by a property value, because the server asks BioPortal to search property values too. */
  @Test
  public void searchClassByPropertyValue() {
    // 229E is the strain of Human coronavirus, which neither its label nor a synonym holds.
    PagedResults<SearchResult> results = search("q=229e&scope=classes&sources=" + VIRUSES);
    Assertions.assertEquals(List.of(HUMAN_CORONAVIRUS), ids(results));
  }

  @Test
  public void searchClassesAndValuesTest() {
    PagedResults<SearchResult> results = search("q=vaccine&scope=classes,values");
    Assertions.assertEquals(union(VACCINE_CLASSES, VACCINE_VALUES), Set.copyOf(ids(results)),
        "The value set should be left out");
    Assertions.assertEquals(Set.of("OntologyClass", "Value"), types(results));
  }

  @Test
  public void searchClassesBySourceTest() {
    // "organ" matches Organ in TESTONT and Organism in VIRONT.
    Assertions.assertEquals(Set.of(ONTOLOGY, VIRUSES), sources(search("q=organ&scope=classes")));
    PagedResults<SearchResult> results = search("q=organ&scope=classes&sources=" + VIRUSES);
    Assertions.assertEquals(List.of(ORGANISM), ids(results));
    Assertions.assertEquals(Set.of(VIRUSES), sources(results), "Class source does not match the expected source");
  }

  @Test
  public void searchClassesBySourceCheckMatchTypePrefLabelTest() {
    PagedResults<SearchResult> results = search("q=coronavirus&scope=classes&sources=" + VIRUSES);
    Assertions.assertTrue(results.getCollection().size() > 1, "The number of search results is lower than expected");
    SearchResult first = results.getCollection().get(0);
    Assertions.assertEquals(CORONAVIRUS, first.getLdId());
    Assertions.assertEquals("prefLabel", first.getMatchType(), "Returned 'matchType' does not match the expected value");
    Assertions.assertEquals(List.of(), first.getMatchedSynonyms(), "A match by label should name no synonyms");
  }

  @Test
  public void searchClassesByWrongSourceTest() {
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(),
        statusOf(baseUrlBpSearch + "?q=virus&scope=classes&sources=WRONG-SOURCE"));
  }

  @Test
  public void searchValueSetTest() {
    PagedResults<SearchResult> results = search("q=vaccine&scope=value_sets");
    Assertions.assertEquals(List.of(CORONAVIRUS_VACCINES), ids(results));
    Assertions.assertEquals(Set.of("ValueSet"), types(results));
  }

  @Test
  public void searchValuesTest() {
    PagedResults<SearchResult> results = search("q=vaccine&scope=values");
    Assertions.assertTrue(ids(results).containsAll(VACCINE_VALUES), "Values missing from the results: " + ids(results));
    Assertions.assertFalse(types(results).contains("OntologyClass"), "A values search returned a class");
  }

  @Test
  public void searchPropertiesTest() {
    PagedResults<SearchResult> results = searchProperties("q=has");
    Assertions.assertEquals(Set.of(HAS_PART, HAS_CHAMBER, HAS_HOST, HAS_HUMAN_HOST, HAS_HOST_RANGE),
        Set.copyOf(ids(results)));
    Assertions.assertEquals(Set.of(ONTOLOGY, VIRUSES), sources(results), "An unscoped search reads every ontology");
  }

  @Test
  public void searchWrongPropertyTest() {
    PagedResults<SearchResult> results = searchProperties("q=wrongproperty333");
    Assertions.assertTrue(results.getCollection().isEmpty());
    Assertions.assertEquals(0, results.getPageSize());
    Assertions.assertEquals(0, results.getTotalCount());
  }

  @Test
  public void searchPropertiesBySourceTest() {
    PagedResults<SearchResult> results = searchProperties("q=has&sources=" + VIRUSES);
    Assertions.assertEquals(Set.of(HAS_HOST, HAS_HUMAN_HOST, HAS_HOST_RANGE), Set.copyOf(ids(results)));
    Assertions.assertEquals(Set.of(VIRUSES), sources(results), "Property source does not match the expected source");
  }

  @Test
  public void searchPropertiesBySourceExactMatchTest() {
    String query = "q=" + encode("has host") + "&sources=" + VIRUSES;
    Assertions.assertEquals(List.of(HAS_HOST, HAS_HOST_RANGE), ids(searchProperties(query)));
    Assertions.assertEquals(List.of(HAS_HOST), ids(searchProperties(query + "&exact_match=true")),
        "exact_match should keep only the property whose label is the query");
  }

  @Test
  public void searchPropertiesByWrongSourceTest() {
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(),
        statusOf(baseUrlBpPropertySearch + "?q=has&sources=WRONG-SOURCE"));
  }

  @Test
  public void searchPropertiesRequireDefinitionsTest() {
    Assertions.assertTrue(ids(searchProperties("q=host")).contains(HAS_HOST_RANGE),
        "Without the flag, the property with no definition is found");
    PagedResults<SearchResult> results = searchProperties("q=host&require_definitions=true");
    Assertions.assertEquals(List.of(HAS_HOST, HAS_HUMAN_HOST), ids(results));
    for (SearchResult r : results.getCollection()) {
      Assertions.assertFalse(r.getDefinition() == null || r.getDefinition().isEmpty(),
          "A property with no definitions has been returned: " + r.getLdId());
    }
  }

}
