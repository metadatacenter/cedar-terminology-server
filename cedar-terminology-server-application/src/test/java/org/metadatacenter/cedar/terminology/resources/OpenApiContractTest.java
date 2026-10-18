package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The integrated search and retrieve bodies document every paging property the routes read, and the
 * property routes document every answer they give.
 *
 * <p>The integrated routes read {@code limit} and {@code offset} from the body, but the document is
 * generated from a model of its own, and the generator drops a property that has no accessor. The
 * model declared both without one, so the document described page-number paging alone.
 */
class OpenApiContractTest {

  @ParameterizedTest
  @ValueSource(strings = {"IntegratedSearchRequestBody", "IntegratedRetrieveRequestBody"})
  void integratedBodiesDocumentBothKindsOfPaging(String schema) throws IOException {
    JsonNode properties = readSpec().path("components").path("schemas").path(schema).path("properties");
    Set<String> documented = new TreeSet<>();
    properties.fieldNames().forEachRemaining(documented::add);
    assertTrue(documented.containsAll(Set.of("limit", "offset", "page", "pageSize")), documented.toString());
    for (String paging : Set.of("limit", "offset")) {
      assertEquals("integer", properties.path(paging).path("type").asText(), paging);
    }
  }

  /**
   * The property routes document every answer they give, each refusal in the shared error body. They
   * documented only their success, although each refuses a request and a store failure answers 500.
   */
  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "/properties/search   | post | /schema/$ref       | Result    | 400,404,406,415,500,503",
      "/properties/versions | get  | /schema/items/$ref | Version   | 404,406,500,503",
      "/properties          | get  | /schema/$ref       | Detail    | 400,404,406,500,503",
      "/properties/hierarchy| get  | /schema/$ref       | Hierarchy | 400,404,406,500,503",
      "/properties/roots    | get  | /schema/$ref       | Roots     | 400,404,406,500,503"})
  void propertyRoutesDocumentEveryAnswer(String path, String method, String successSchema, String success,
                                         String refusals) throws IOException {
    JsonNode responses = readSpec().path("paths").path(path).path(method).path("responses");
    Set<String> documented = new TreeSet<>();
    responses.fieldNames().forEachRemaining(documented::add);
    Set<String> expected = new TreeSet<>(Set.of(refusals.split(",")));
    expected.add("200");
    assertEquals(expected, documented, path);
    assertEquals("#/components/schemas/" + success,
        responses.at("/200/content/application~1json" + successSchema).asText(), path);
    for (String status : refusals.split(",")) {
      assertEquals("#/components/schemas/CedarError",
          responses.at("/" + status + "/content/application~1json/schema/$ref").asText(), path + " " + status);
      assertFalse(responses.path(status).path("description").asText().isBlank(), path + " " + status);
    }
    assertTrue(responses.at("/500/description").asText().contains("errorId"), path);
  }

  /**
   * The property hierarchy and roots document the range of {@code limit}, which is the store's page
   * bound. Neither documented the parameter, and the route accepted up to 500 while the store refused
   * any page over 200.
   */
  @ParameterizedTest
  @ValueSource(strings = {"/properties/hierarchy", "/properties/roots"})
  void propertyPagesDocumentTheirLimit(String path) throws IOException {
    JsonNode operation = readSpec().path("paths").path(path).path("get");
    String limit = null;
    for (JsonNode parameter : operation.path("parameters")) {
      if (parameter.path("name").asText().equals("limit")) {
        limit = parameter.path("description").asText();
      }
    }
    assertNotNull(limit, path + " documents no limit");
    assertTrue(limit.contains("from 1 to 200. Defaults to 50."), limit);
    assertTrue(operation.at("/responses/400/description").asText().contains("limit is outside 1 to 200"),
        operation.at("/responses/400/description").asText());
  }

  private static JsonNode readSpec() throws IOException {
    try (InputStream input = OpenApiContractTest.class.getResourceAsStream("/assets/swagger-api/swagger.json")) {
      assertNotNull(input, "generated OpenAPI document");
      return JsonMapper.STRICT_MAPPER.readTree(input);
    }
  }
}
