package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.artifacts.model.core.fields.constraints.VersionSpec;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/** Reads both legacy string pins and the structured version pins written at template publication. */
public class ConstraintVersionDeserializer extends JsonDeserializer<VersionSpec> {

  static final Set<String> VERSION_FIELDS = Set.of("id", "effectiveDate", "declaredVersion");

  @Override
  public VersionSpec deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.currentToken() == JsonToken.VALUE_STRING) {
      String value = parser.getValueAsString();
      return new VersionSpec(value, Optional.empty(), Optional.empty());
    }
    if (parser.currentToken() == JsonToken.START_OBJECT) {
      JsonNode version = parser.getCodec().readTree(parser);
      Iterator<String> fields = version.fieldNames();
      while (fields.hasNext()) {
        String field = fields.next();
        if (!VERSION_FIELDS.contains(field)) {
          throw JsonMappingException.from(parser, "unknown version field: " + field);
        }
      }
      JsonNode id = version.get("id");
      if (id != null && id.isTextual() && !id.asText().isBlank()) {
        return new VersionSpec(id.asText(), optionalText(version, "effectiveDate", parser),
            optionalText(version, "declaredVersion", parser));
      }
      throw JsonMappingException.from(parser, "version object must contain a non-blank string id");
    }
    throw JsonMappingException.from(parser, "version must be a string or an object containing id");
  }

  private static Optional<String> optionalText(JsonNode object, String field, JsonParser parser)
      throws JsonMappingException {
    JsonNode value = object.get(field);
    if (value == null || value.isNull()) {
      return Optional.empty();
    }
    if (!value.isTextual()) {
      throw JsonMappingException.from(parser, field + " must be a string");
    }
    return Optional.of(value.asText());
  }
}
