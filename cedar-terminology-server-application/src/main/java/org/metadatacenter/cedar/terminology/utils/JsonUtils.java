package org.metadatacenter.cedar.terminology.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.checkerframework.checker.nullness.qual.NonNull;

public class JsonUtils
{

  // Remove a particular field from a JsonNode object
  public @NonNull JsonNode removeField(@NonNull JsonNode node, @NonNull String fieldName) {
    ObjectNode object = (ObjectNode) node;
    object.remove(fieldName);
    return object;
  }

}