package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.search.TerminologyPresentationContract;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDescriptionResourceTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final ResourceDescriptionResource resource = new ResourceDescriptionResource();

  @Test void returnsTheClosedContractForAnExistingUnpinnedReference() throws Exception {
    try (var response = resource.describe(JSON.readTree(
        "{\"type\":\"class\",\"sourceSystem\":\"bioportal\",\"sourceAcronym\":\"EX\",\"termIri\":\"urn:term:a\"}"))) {
      assertEquals(200, response.getStatus());
      assertTrue(TerminologyPresentationContract.schema("ResourceDescription")
          .validate(JSON.valueToTree(response.getEntity())).isEmpty());
    }
  }

  @Test void rejectsExtensionsAndReturnsMissingPinsAsNormalHttpErrors() throws Exception {
    var reference = JSON.createObjectNode().put("type", "class").put("sourceSystem", "bioportal")
        .put("sourceAcronym", "EX").put("termIri", "urn:term:a");
    try (var response = resource.describe(reference.deepCopy().put("epa", "custom"))) {
      assertEquals(400, response.getStatus());
    }
    reference.putObject("version").put("id", "missing-release");
    try (var response = resource.describe(reference)) {
      assertEquals(404, response.getStatus());
    }
  }
}
