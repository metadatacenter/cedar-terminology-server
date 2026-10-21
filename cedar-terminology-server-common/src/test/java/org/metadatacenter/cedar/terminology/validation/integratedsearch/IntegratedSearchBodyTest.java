package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dropwizard.jackson.Jackson;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An integrated search or retrieve posts a controlled-term field's whole value constraints: the ontologies, branches,
 * value sets and classes its terms come from, the arrangement of those terms, and the field's other settings. The
 * server reads every part with the mapper it serves requests with, and validation names each required part a body
 * lacks.
 */
class IntegratedSearchBodyTest {

  private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

  private static final String CONSTRAINTS = """
      {
        "ontologies": [{"uri": "https://data.bioontology.org/ontologies/DOID", "acronym": "DOID",
                        "name": "Human Disease Ontology", "numTerms": 11000, "iri": "http://purl.obolibrary.org/obo/doid.owl",
                        "version": "2026-07", "sourceSystem": "BioPortal"}],
        "branches": [{"uri": "http://purl.obolibrary.org/obo/DOID_4", "acronym": "DOID", "source": "DOID",
                      "name": "disease", "maxDepth": 3, "iri": "http://purl.obolibrary.org/obo/DOID_4",
                      "version": {"id": "sha256:abc", "effectiveDate": "2026-07-01", "declaredVersion": "2026-07"},
                      "sourceSystem": "BioPortal"}],
        "valueSets": [{"uri": "https://cadsr.example.org/VD100", "vsCollection": "CADSR-VS", "name": "Colors",
                       "numTerms": 2, "iri": "https://cadsr.example.org/VD100", "version": "latest"}],
        "classes": [{"uri": "http://purl.obolibrary.org/obo/DOID_162", "prefLabel": "cancer", "type": "OntologyClass",
                     "label": "Cancer", "source": "DOID", "iri": "http://purl.obolibrary.org/obo/DOID_162"}],
        "actions": [{"to": 0, "action": "move", "termUri": "http://purl.obolibrary.org/obo/DOID_162",
                     "type": "OntologyClass", "source": "DOID", "sourceUri": "https://data.bioontology.org/ontologies/DOID"}],
        "defaultValue": {"termUri": "http://purl.obolibrary.org/obo/DOID_162", "rdfs:label": "cancer"},
        "requiredValue": true,
        "recommendedValue": false,
        "multipleChoice": true
      }
      """;

  @Test
  void aSearchBodyIsReadWhole() throws Exception {
    IntegratedSearchBody body = MAPPER.readValue("""
        {"parameterObject": {"valueConstraints": %s, "inputText": "canc"}, "page": 2, "pageSize": 50,
         "limit": 10, "offset": 20}
        """.formatted(CONSTRAINTS), IntegratedSearchBody.class);

    assertEquals(Set.of(), violations(body));
    assertEquals("canc", body.getParameterObject().getInputText());
    assertEquals(2, body.getPage());
    assertEquals(50, body.getPageSize());
    assertEquals(10, body.getLimit());
    assertEquals(20, body.getOffset());
    assertConstraintsRead(body.getParameterObject().getValueConstraints());
  }

  @Test
  void aRetrieveBodyIsReadWhole() throws Exception {
    IntegratedRetrieveBody body = MAPPER.readValue("""
        {"valueConstraints": %s, "page": 1, "pageSize": 500}
        """.formatted(CONSTRAINTS), IntegratedRetrieveBody.class);

    assertEquals(Set.of(), violations(body));
    assertEquals(1, body.getPage());
    assertEquals(500, body.getPageSize());
    assertNull(body.getLimit(), "a body that pages by number sends no limit");
    assertNull(body.getOffset());
    assertConstraintsRead(body.getValueConstraints());
  }

  @Test
  void validationNamesEachRequiredPartABodyLacks() throws Exception {
    assertEquals(Set.of("parameterObject"), violations(MAPPER.readValue("{}", IntegratedSearchBody.class)));
    assertEquals(Set.of("valueConstraints"), violations(MAPPER.readValue("{}", IntegratedRetrieveBody.class)));
    assertEquals(Set.of("valueConstraints.ontologies", "valueConstraints.branches", "valueConstraints.valueSets",
            "valueConstraints.classes"),
        violations(MAPPER.readValue("{\"valueConstraints\": {}}", IntegratedRetrieveBody.class)));

    ObjectNode constraints = (ObjectNode) MAPPER.readTree(CONSTRAINTS);
    ((ObjectNode) constraints.get("ontologies").get(0)).remove("acronym");
    ((ObjectNode) constraints.get("branches").get(0)).remove("uri");
    ((ObjectNode) constraints.get("valueSets").get(0)).put("vsCollection", "");
    ((ObjectNode) constraints.get("classes").get(0)).remove(List.of("prefLabel", "type", "source"));
    ((ObjectNode) constraints.get("actions").get(0)).put("action", "rename");
    ObjectNode body = MAPPER.createObjectNode().set("valueConstraints", constraints);

    assertEquals(Set.of("valueConstraints.ontologies[0].acronym", "valueConstraints.branches[0].uri",
            "valueConstraints.valueSets[0].vsCollection", "valueConstraints.classes[0].prefLabel",
            "valueConstraints.classes[0].type", "valueConstraints.classes[0].source",
            "valueConstraints.actions[0].action"),
        violations(MAPPER.treeToValue(body, IntegratedRetrieveBody.class)));
  }

  @Test
  void aTermCanBeMovedOrDeletedAndNothingElse() throws Exception {
    for (String action : List.of("move", "delete")) {
      assertTrue(violations(action(action)).isEmpty(), action);
    }
    assertEquals(Set.of("action"), violations(action("rename")));
    assertEquals(Set.of("action"), violations(action("moved")), "the whole action must match, not a prefix of it");
  }

  @Test
  void aMalformedVersionPinIsRefused() {
    for (String version : List.of("{\"id\": \"v1\", \"tag\": \"latest\"}", "{\"effectiveDate\": \"2026-07-01\"}",
        "{\"id\": \" \"}", "{\"id\": 7}", "{\"id\": \"v1\", \"declaredVersion\": 2026}", "42", "[\"v1\"]")) {
      assertThrows(JsonMappingException.class, () -> MAPPER.readValue(
          "{\"acronym\": \"DOID\", \"version\": " + version + "}", OntologyValueConstraint.class), version);
    }
  }

  @Test
  void aStructuredPinWithoutItsOptionalPartsNamesOnlyTheRelease() throws Exception {
    ValueSetValueConstraint valueSet = MAPPER.readValue(
        "{\"uri\": \"u\", \"vsCollection\": \"CEDARVS\", \"version\": {\"id\": \"v1\", \"effectiveDate\": null}}",
        ValueSetValueConstraint.class);

    assertEquals("v1", valueSet.getVersion());
    assertFalse(valueSet.versionSpec().effectiveDate().isPresent());
    assertFalse(valueSet.versionSpec().declaredVersion().isPresent());
  }

  private static void assertConstraintsRead(ValueConstraints constraints) {
    OntologyValueConstraint ontology = constraints.getOntologies().get(0);
    assertEquals("https://data.bioontology.org/ontologies/DOID", ontology.getUri());
    assertEquals("DOID", ontology.getAcronym());
    assertEquals("Human Disease Ontology", ontology.getName());
    assertEquals(11000, ontology.getNumTerms());
    assertEquals("http://purl.obolibrary.org/obo/doid.owl", ontology.getIri());
    assertEquals("2026-07", ontology.getVersion());
    assertEquals("BioPortal", ontology.getSourceSystem());

    BranchValueConstraint branch = constraints.getBranches().get(0);
    assertEquals("http://purl.obolibrary.org/obo/DOID_4", branch.getUri());
    assertEquals("DOID", branch.getAcronym());
    assertEquals("DOID", branch.getSource());
    assertEquals("disease", branch.getName());
    assertEquals(3, branch.getMaxDepth());
    assertEquals("http://purl.obolibrary.org/obo/DOID_4", branch.getIri());
    assertEquals("sha256:abc", branch.getVersion());
    assertEquals("2026-07-01", branch.versionSpec().effectiveDate().orElseThrow());
    assertEquals("BioPortal", branch.getSourceSystem());

    ValueSetValueConstraint valueSet = constraints.getValueSets().get(0);
    assertEquals("https://cadsr.example.org/VD100", valueSet.getUri());
    assertEquals("CADSR-VS", valueSet.getVsCollection());
    assertEquals("Colors", valueSet.getName());
    assertEquals(2, valueSet.getNumTerms());
    assertEquals("https://cadsr.example.org/VD100", valueSet.getIri());
    assertEquals("latest", valueSet.getVersion());
    assertNull(valueSet.getSourceSystem(), "an absent source system means BioPortal");

    ClassValueConstraint term = constraints.getClasses().get(0);
    assertEquals("http://purl.obolibrary.org/obo/DOID_162", term.getUri());
    assertEquals("cancer", term.getPrefLabel());
    assertEquals("OntologyClass", term.getType());
    assertEquals("Cancer", term.getLabel());
    assertEquals("DOID", term.getSource());
    assertEquals("http://purl.obolibrary.org/obo/DOID_162", term.getIri());
    assertNull(term.getSourceSystem());
    assertNull(term.getVersion(), "a class with no pin is unpinned");
    assertNull(term.versionSpec());

    Action action = constraints.getActions().get(0);
    assertEquals(0, action.getTo());
    assertEquals("move", action.getAction());
    assertEquals("http://purl.obolibrary.org/obo/DOID_162", action.getTermUri());
    assertEquals("OntologyClass", action.getType());
    assertEquals("DOID", action.getSource());
    assertEquals("https://data.bioontology.org/ontologies/DOID", action.getSourceUri());

    assertEquals("cancer", constraints.getDefaultValue().get("rdfs:label").asText(),
        "the default value is kept as posted");
    assertTrue(constraints.isRequiredValue());
    assertFalse(constraints.isRecommendedValue());
    assertTrue(constraints.isMultipleChoice());
  }

  private static Action action(String name) throws Exception {
    return MAPPER.readValue("""
        {"action": "%s", "termUri": "http://purl.obolibrary.org/obo/DOID_162", "type": "OntologyClass", "source": "DOID"}
        """.formatted(name), Action.class);
  }

  /** The property path of each constraint the object violates. */
  private static Set<String> violations(Object body) {
    return VALIDATOR.validate(body).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }
}
