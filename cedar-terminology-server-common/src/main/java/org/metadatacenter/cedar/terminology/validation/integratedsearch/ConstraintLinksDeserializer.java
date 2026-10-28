package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.metadatacenter.artifacts.model.core.fields.constraints.ConstraintLinks;
import java.io.IOException;

/** Read through the core model even when the HTTP mapper tolerates unknown fields elsewhere. */
public class ConstraintLinksDeserializer extends JsonDeserializer<ConstraintLinks> {
  @Override public ConstraintLinks deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    try { return ConstraintLinks.read(context.readTree(parser)); }
    catch (IllegalArgumentException e) { throw JsonMappingException.from(parser, e.getMessage()); }
  }
  @Override public ConstraintLinks getNullValue(DeserializationContext context) throws JsonMappingException {
    throw JsonMappingException.from(context, "Omit links when unavailable; null is not a links object");
  }
}
