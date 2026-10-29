package org.metadatacenter.terms.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

/** The closed API contract, also used to generate the browser types. Not an artifact schema. */
public final class TerminologyLinksContract {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final JsonNode DOCUMENT = load();
  private static final JsonSchema REFERENCE = schema("ConstraintReference");

  private TerminologyLinksContract() {}

  private static JsonNode load() {
    try (var in = TerminologyLinksContract.class.getResourceAsStream("/contracts/terminology-links.schema.json")) {
      if (in == null) throw new IllegalStateException("Missing terminology links contract");
      return JSON.readTree(in);
    } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
  }

  public static JsonSchema schema(String name) {
    ObjectNode document = DOCUMENT.deepCopy();
    document.put("$ref", "#/$defs/" + name);
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(document);
  }

  public static ResourceDescription.Reference readReference(JsonNode input) {
    if (input == null || !REFERENCE.validate(input).isEmpty())
      throw new IllegalArgumentException("Expected a CEDAR constraint reference with only type, source, term and version fields");
    if (input.path("version").isObject() && input.path("version").path("id").asText().isBlank())
      throw new IllegalArgumentException("A version id must not be blank");
    ObjectNode reference = input.deepCopy();
    if (reference.path("version").isTextual()) reference.remove("version"); // the schema permits only "latest"
    try { return JSON.treeToValue(reference, ResourceDescription.Reference.class); }
    catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid constraint reference", e); }
  }
}
