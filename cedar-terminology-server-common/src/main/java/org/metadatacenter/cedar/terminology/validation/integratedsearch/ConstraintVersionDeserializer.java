package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/** Reads both legacy string pins and the structured version pins written at template publication. */
public class ConstraintVersionDeserializer extends JsonDeserializer<String> {

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    if (parser.currentToken() == JsonToken.VALUE_STRING) {
      String value = parser.getValueAsString();
      return "latest".equalsIgnoreCase(value) ? "latest" : value;
    }
    if (parser.currentToken() == JsonToken.START_OBJECT) {
      JsonNode version = parser.getCodec().readTree(parser);
      JsonNode id = version.get("id");
      if (id != null && id.isTextual() && !id.asText().isBlank()) {
        return id.asText();
      }
      throw JsonMappingException.from(parser, "version object must contain a non-blank string id");
    }
    throw JsonMappingException.from(parser, "version must be a string or an object containing id");
  }
}
