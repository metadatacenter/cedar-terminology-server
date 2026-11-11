package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ClassValueConstraint;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.util.Util;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.metadatacenter.cedar.terminology.resources.FakeBioPortal.*;
import static org.metadatacenter.cedar.terminology.util.Constants.*;
import static org.metadatacenter.constant.HttpConstants.BAD_REQUEST;
import static org.metadatacenter.model.ModelNodeNames.*;

/**
 * The integrated search, which finds a field's terms from its value constraints: an ontology, a branch, a value set,
 * a list of enumerated classes, or several of these at once. Enumerated classes describe themselves, so a search of
 * them alone makes no BioPortal call, and neither does a request the server refuses.
 */
public class IntegratedSearchResourceTest extends AbstractTerminologyServerResourceTest {

  private static final String ENUMERATED_CLASS_1_URI = "http://purl.obolibrary.org/obo/DOID_0050134";
  private static final String ENUMERATED_CLASS_2_URI = "http://purl.obolibrary.org/obo/DOID_0080014";
  private static final String ENUMERATED_CLASS_3_URI = "http://purl.obolibrary.org/obo/DOID_0060072";
  private static final String ENUMERATED_CLASS_TYPE = "OntologyClass";
  private static final String ENUMERATED_CLASS_SOURCE = "DOID";

  /**
   * Invalid input
   */

  @Test
  public void searchEmptyBody() {
    ObjectNode emptyBody = mapper.createObjectNode();
    Assertions.assertEquals(STATUS_CODE_UNPROCESSABLE_ENTITY, statusOfPost(baseUrlBpIntegratedSearch, emptyBody));
  }

  @Test
  public void retrieveEmptyBody() {
    ObjectNode emptyBody = mapper.createObjectNode();
    Assertions.assertEquals(STATUS_CODE_UNPROCESSABLE_ENTITY, statusOfPost(baseUrlBpIntegratedRetrieve, emptyBody));
  }

  @Test
  public void searchEmptyParamObject() {
    ObjectNode requestBody = mapper.createObjectNode();
    requestBody.set(BP_INTEGRATED_SEARCH_PARAMS_FIELD, mapper.createObjectNode());
    Assertions.assertEquals(STATUS_CODE_UNPROCESSABLE_ENTITY, statusOfPost(baseUrlBpIntegratedSearch, requestBody));
  }

  @Test
  public void searchEmptyConstraints() {
    ObjectNode requestBody = mapper.createObjectNode();
    ObjectNode parameterObject = mapper.createObjectNode();
    parameterObject.set(BP_INTEGRATED_SEARCH_PARAM_VALUE_CONSTRAINTS, mapper.createObjectNode());
    requestBody.set(BP_INTEGRATED_SEARCH_PARAMS_FIELD, parameterObject);
    Assertions.assertEquals(STATUS_CODE_UNPROCESSABLE_ENTITY, statusOfPost(baseUrlBpIntegratedSearch, requestBody));
  }

  @Test
  public void searchInvalidField() {
    ObjectNode requestBody = mapper.createObjectNode();
    requestBody.put("invalidField", "invalidFieldValue");
    Assertions.assertEquals(BAD_REQUEST, statusOfPost(baseUrlBpIntegratedSearch, requestBody));
  }

  /**
   * Search Classes in Ontologies
   */

  @Test
  public void searchClassesInOntologyEmptyInputText() { // It should retrieve all the ontology classes, by label
    PagedResults<SearchResult> results = search(generateRequestBody("", ontology(ONTOLOGY), none(), none(), none(),
        Optional.empty(), Optional.of(3)));
    Assertions.assertEquals(List.of("Heart", "Organ", "Root"), labels(results));
    assertPaging(results, 1, 2, 3, 4, null, 2);
  }

  @Test
  public void searchClassesInOntologyMissingInputText() { // It should retrieve all the ontology classes, by label
    PagedResults<SearchResult> results = search(generateRequestBody(null, ontology(ONTOLOGY), none(), none(), none(),
        Optional.empty(), Optional.of(3)));
    Assertions.assertEquals(List.of("Heart", "Organ", "Root"), labels(results));
    assertPaging(results, 1, 2, 3, 4, null, 2);
  }

  @Test
  public void searchClassesInOntologyWrongLabel() {
    String inputText = "aaabbbcccddd"; // Non-existing class label
    PagedResults<SearchResult> results = search(generateRequestBody(inputText, ontology(ONTOLOGY), none(), none(),
        none(), Optional.empty(), Optional.of(10)));
    Assertions.assertTrue(results.getCollection().isEmpty(), "Wrong number of results");
    assertPaging(results, 1, 0, 0, 0, null, null);
  }

  @Test
  public void searchClassesInOntology() {
    String inputText = "virus";
    PagedResults<SearchResult> results = search(generateRequestBody(inputText, ontology(VIRUSES), none(), none(),
        none(), Optional.empty(), Optional.of(4)));
    Assertions.assertEquals(4, results.getCollection().size(), "Wrong number of results");
    for (SearchResult r : results.getCollection()) {
      Assertions.assertTrue(r.getPrefLabel().toLowerCase(Locale.ROOT).contains(inputText),
          "Unexpected result: " + r.getPrefLabel());
      Assertions.assertEquals(VIRUSES, Util.getShortIdentifier(r.getSource()),
          "Class source does not match the expected source");
    }
    // Six classes of VIRONT have "virus" in their label, so a second page holds the other two.
    assertPaging(results, 1, 2, 4, 6, null, 2);
  }

  /**
   * Search Classes in Branches
   */

  @Test
  public void searchClassesInBranchEmptyInputText() { // It should retrieve all the classes in the branch, by label
    PagedResults<SearchResult> results = search(generateRequestBody("", none(), branch(VIRUSES, VIRUS), none(), none(),
        Optional.empty(), Optional.of(3)));
    Assertions.assertEquals(List.of("Bat coronavirus", "Coronavirus", "Human coronavirus"), labels(results));
    assertPaging(results, 1, 2, 3, 4, null, 2);
  }

  @Test
  public void searchClassesInBranchMissingInputText() { // It should retrieve all the classes in the branch, by label
    PagedResults<SearchResult> results = search(generateRequestBody(null, none(), branch(VIRUSES, VIRUS), none(),
        none(), Optional.empty(), Optional.of(3)));
    Assertions.assertEquals(List.of("Bat coronavirus", "Coronavirus", "Human coronavirus"), labels(results));
    assertPaging(results, 1, 2, 3, 4, null, 2);
  }

  @Test
  public void searchClassesInBranch() {
    String inputText = "coronavirus";
    PagedResults<SearchResult> results = search(generateRequestBody(inputText, none(), branch(VIRUSES, VIRUS), none(),
        none(), Optional.empty(), Optional.of(100)));
    // Coronavirus vaccine matches too, but lies outside the Virus branch.
    Assertions.assertEquals(Set.of(CORONAVIRUS, HUMAN_CORONAVIRUS, BAT_CORONAVIRUS),
        results.getCollection().stream().map(SearchResult::getLdId).collect(Collectors.toSet()));
    String first = results.getCollection().get(0).getPrefLabel();
    Assertions.assertTrue(first.toLowerCase(Locale.ROOT).contains(inputText), "Unexpected result: " + first);
    for (SearchResult r : results.getCollection()) {
      Assertions.assertEquals(VIRUSES, Util.getShortIdentifier(r.getSource()),
          "Class source does not match the expected source");
    }
    assertPaging(results, 1, 1, 3, 3, null, null);
  }

  @Test
  public void searchClassesInBranchWrongLabel() {
    String inputText = "aaabbbcccddd"; // Non-existing class label
    PagedResults<SearchResult> results = search(generateRequestBody(inputText, none(), branch(VIRUSES, VIRUS), none(),
        none(), Optional.empty(), Optional.of(1)));
    Assertions.assertTrue(results.getCollection().isEmpty(), "Wrong number of results");
    assertPaging(results, 1, 0, 0, 0, null, null);
  }

  /**
   * Search Values in Value Sets
   */

  @Test
  public void searchValuesNonProvisionalValueSetEmptyInputText() { // It should retrieve all the values, by label
    PagedResults<SearchResult> results = search(generateRequestBody("", none(), none(),
        valueSet(NLMVS, CORONAVIRUS_VACCINES), none(), Optional.empty(), Optional.of(200)));
    Assertions.assertEquals(List.of("Inactivated virus vaccine", "mRNA coronavirus vaccine",
        "Viral vector coronavirus vaccine"), labels(results));
    assertPaging(results, 1, 1, 3, 3, null, null);
  }

  @Test
  public void searchValuesNonProvisionalValueSet() { // Search for values that match the inputText in the value set
    PagedResults<SearchResult> results = search(generateRequestBody("coronavirus", none(), none(),
        valueSet(NLMVS, CORONAVIRUS_VACCINES), none(), Optional.empty(), Optional.empty()));
    Assertions.assertEquals(List.of(MRNA_VACCINE, VECTOR_VACCINE),
        results.getCollection().stream().map(SearchResult::getLdId).toList());
    assertPaging(results, 1, 1, 2, 2, null, null);
  }

  /**
   * Search Enumerated Classes
   */

  @Test
  public void searchEnumeratedClassesEmptySearch() throws IOException {
    ArrayNode enumeratedClasses = enumeratedClasses();
    PagedResults<SearchResult> results = search(generateRequestBody("", none(), none(), none(), enumeratedClasses,
        Optional.empty(), Optional.of(10)));
    Assertions.assertEquals(enumeratedClasses.size(), results.getCollection().size(), "Wrong number of results");
    assertEnumeratedClassesInLabelOrder(enumeratedClasses, results.getCollection());
    assertPaging(results, 1, 1, 3, 3, null, null);
  }

  @Test
  public void searchEnumeratedClassesEmptySearchTwoPages() throws IOException {
    ArrayNode enumeratedClasses = enumeratedClasses();
    PagedResults<SearchResult> resultsPage1 = search(generateRequestBody("", none(), none(), none(), enumeratedClasses,
        Optional.of(1), Optional.of(2)));
    PagedResults<SearchResult> resultsPage2 = search(generateRequestBody("", none(), none(), none(), enumeratedClasses,
        Optional.of(2), Optional.of(2)));
    Assertions.assertEquals(2, resultsPage1.getCollection().size(), "Wrong number of results");
    Assertions.assertEquals(1, resultsPage2.getCollection().size(), "Wrong number of results");

    List<SearchResult> results = new ArrayList<>(resultsPage1.getCollection());
    results.addAll(resultsPage2.getCollection());
    assertEnumeratedClassesInLabelOrder(enumeratedClasses, results);
    assertPaging(resultsPage1, 1, 2, 2, 3, null, 2);
    assertPaging(resultsPage2, 2, 2, 1, 3, 1, null);
  }

  @Test
  public void searchEnumeratedClasses() {
    PagedResults<SearchResult> results = search(generateRequestBody("Mycosis", none(), none(), none(),
        enumeratedClasses(), Optional.empty(), Optional.of(10)));
    Assertions.assertEquals(1, results.getCollection().size(), "Wrong number of results");
    assertEnumeratedClass(ENUMERATED_CLASS_1_URI, "cutaneous mycosis", results.getCollection().get(0));
    assertPaging(results, 1, 1, 1, 1, null, null);
  }

  /**
   * Search based on multiple constraints
   */

  @Test
  public void searchEnumeratedClassesAndOntologyEmptySearch() {
    // The labels "000" and "zzz" sort the two enumerated classes to either end of the ontology's classes.
    ArrayNode enumeratedClasses = mapper.createArrayNode()
        .add(enumeratedClass(ENUMERATED_CLASS_1_URI, "000"))
        .add(enumeratedClass(ENUMERATED_CLASS_2_URI, "zzz"));
    PagedResults<SearchResult> results = search(generateRequestBody("", ontology(ONTOLOGY), none(), none(),
        enumeratedClasses, Optional.empty(), Optional.of(3)));

    Assertions.assertEquals(List.of("000", "Heart", "Organ"), labels(results));
    assertEnumeratedClass(ENUMERATED_CLASS_1_URI, "000", results.getCollection().get(0));
    Assertions.assertEquals(List.of(HEART, ORGAN),
        results.getCollection().subList(1, 3).stream().map(SearchResult::getLdId).toList());
    for (SearchResult r : results.getCollection().subList(1, 3)) {
      Assertions.assertEquals(ONTOLOGY, Util.getShortIdentifier(r.getSource()), "Wrong source");
    }
    // Both sources are sorted together and paged as one list: two enumerated classes and four of the ontology.
    assertPaging(results, 1, 2, 3, 6, null, 2);
  }

  /**
   * Utility methods
   */

  private static ArrayNode none() {
    return mapper.createArrayNode();
  }

  private static ArrayNode ontology(String acronym) {
    ObjectNode ontology = mapper.createObjectNode().put(VALUE_CONSTRAINTS_ACRONYM, acronym);
    return mapper.createArrayNode().add(ontology);
  }

  private static ArrayNode branch(String acronym, String uri) {
    ObjectNode branch = mapper.createObjectNode().put(VALUE_CONSTRAINTS_URI, uri).put(VALUE_CONSTRAINTS_ACRONYM, acronym);
    return mapper.createArrayNode().add(branch);
  }

  private static ArrayNode valueSet(String vsCollection, String uri) {
    ObjectNode valueSet =
        mapper.createObjectNode().put(VALUE_CONSTRAINTS_URI, uri).put(VALUE_CONSTRAINTS_VS_COLLECTION, vsCollection);
    return mapper.createArrayNode().add(valueSet);
  }

  private static ObjectNode enumeratedClass(String uri, String prefLabel) {
    ObjectNode enumeratedClass = mapper.createObjectNode();
    enumeratedClass.put(VALUE_CONSTRAINTS_URI, uri);
    enumeratedClass.put(VALUE_CONSTRAINTS_PREFLABEL, prefLabel);
    enumeratedClass.put(VALUE_CONSTRAINTS_TYPE, ENUMERATED_CLASS_TYPE);
    enumeratedClass.put(VALUE_CONSTRAINTS_LABEL, prefLabel);
    enumeratedClass.put(VALUE_CONSTRAINTS_SOURCE, ENUMERATED_CLASS_SOURCE);
    return enumeratedClass;
  }

  private static ArrayNode enumeratedClasses() {
    return mapper.createArrayNode()
        .add(enumeratedClass(ENUMERATED_CLASS_1_URI, "cutaneous mycosis"))
        .add(enumeratedClass(ENUMERATED_CLASS_2_URI, "chromosomal disease"))
        .add(enumeratedClass(ENUMERATED_CLASS_3_URI, "benign neoplasm"));
  }

  /** Asserts that a search result is the enumerated class it was given, described as the constraint describes it. */
  private static void assertEnumeratedClass(String uri, String prefLabel, SearchResult result) {
    Assertions.assertEquals(uri, result.getLdId(), "Wrong ldId");
    Assertions.assertEquals(Util.getShortIdentifier(uri), result.getId(), "Wrong id");
    Assertions.assertEquals(ENUMERATED_CLASS_TYPE, result.getType(), "Wrong type");
    Assertions.assertEquals(BP_TYPE_BASE + ENUMERATED_CLASS_TYPE, result.getLdType(), "Wrong ldType");
    Assertions.assertEquals(prefLabel, result.getPrefLabel(), "Wrong prefLabel");
    Assertions.assertEquals(BP_API_BASE + BP_ONTOLOGIES + ENUMERATED_CLASS_SOURCE, result.getSource(), "Wrong source");
    Assertions.assertNull(result.getDefinition(), "Wrong definition");
  }

  /** Asserts that the results are the enumerated classes, sorted by label. */
  private static void assertEnumeratedClassesInLabelOrder(ArrayNode enumeratedClasses, List<SearchResult> results)
      throws IOException {
    List<ClassValueConstraint> classes =
        new ArrayList<>(Arrays.asList(mapper.readValue(enumeratedClasses.toString(), ClassValueConstraint[].class)));
    classes.sort(Comparator.comparing(ClassValueConstraint::getPrefLabel, String.CASE_INSENSITIVE_ORDER));
    for (int i = 0; i < classes.size(); i++) {
      assertEnumeratedClass(classes.get(i).getUri(), classes.get(i).getPrefLabel(), results.get(i));
    }
  }

  private static List<String> labels(PagedResults<SearchResult> results) {
    return results.getCollection().stream().map(SearchResult::getPrefLabel).toList();
  }

  private static PagedResults<SearchResult> search(ObjectNode requestBody) {
    try (Response response = request(baseUrlBpIntegratedSearch).post(Entity.json(requestBody))) {
      if (response.getStatus() != Status.OK.getStatusCode()) {
        Assertions.fail("The search answered " + response.getStatus() + ": " + response.readEntity(String.class));
      }
      Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
      return response.readEntity(new GenericType<>() {});
    }
  }

  private static int statusOfPost(String url, ObjectNode requestBody) {
    try (Response response = request(url).post(Entity.json(requestBody))) {
      return response.getStatus();
    }
  }

  private static ObjectNode generateRequestBody(String inputText, ArrayNode ontologies, ArrayNode branches,
                                                ArrayNode valueSets, ArrayNode classes, Optional<Integer> page,
                                                Optional<Integer> pageSize) {
    // valueConstraints object
    ObjectNode valueConstraints = mapper.createObjectNode();
    valueConstraints.set(VALUE_CONSTRAINTS_ONTOLOGIES, ontologies);
    valueConstraints.set(VALUE_CONSTRAINTS_BRANCHES, branches);
    valueConstraints.set(VALUE_CONSTRAINTS_VALUE_SETS, valueSets);
    valueConstraints.set(VALUE_CONSTRAINTS_CLASSES, classes);
    // parameterObject object
    ObjectNode parameterObject = mapper.createObjectNode();
    parameterObject.set(BP_INTEGRATED_SEARCH_PARAM_VALUE_CONSTRAINTS, valueConstraints);
    if (inputText != null) {
      parameterObject.put(BP_INTEGRATED_SEARCH_PARAM_INPUT_TEXT, inputText);
    }
    // Request body
    ObjectNode requestBody = mapper.createObjectNode();
    requestBody.set(BP_INTEGRATED_SEARCH_PARAMS_FIELD, parameterObject);
    page.ifPresent(p -> requestBody.put(BP_PAGE_PARAM, p));
    pageSize.ifPresent(s -> requestBody.put(BP_PAGE_SIZE_PARAM, s));
    return requestBody;
  }

}
