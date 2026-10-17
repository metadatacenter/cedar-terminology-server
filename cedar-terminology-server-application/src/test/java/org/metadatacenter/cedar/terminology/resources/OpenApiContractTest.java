package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The integrated search and retrieve bodies document every paging property the routes read.
 *
 * <p>Both routes read {@code limit} and {@code offset} from the body, but the document is generated
 * from a model of its own, and the generator drops a property that has no accessor. The model
 * declared both without one, so the document described page-number paging alone.
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

  private static JsonNode readSpec() throws IOException {
    try (InputStream input = OpenApiContractTest.class.getResourceAsStream("/assets/swagger-api/swagger.json")) {
      assertNotNull(input, "generated OpenAPI document");
      return JsonMapper.STRICT_MAPPER.readTree(input);
    }
  }
}
