package org.metadatacenter.cedar.terminology.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
public class JsonUtils
{

  // Remove a particular field from a JsonNode object
  public JsonNode removeField(JsonNode node, String fieldName) {
    ObjectNode object = (ObjectNode) node;
    object.remove(fieldName);
    return object;
  }

}