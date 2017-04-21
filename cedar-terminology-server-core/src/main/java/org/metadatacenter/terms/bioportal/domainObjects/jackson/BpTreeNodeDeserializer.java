package org.metadatacenter.terms.bioportal.domainObjects.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.terms.bioportal.domainObjects.BpLinks;
import org.metadatacenter.terms.bioportal.domainObjects.BpTreeNode;
import org.metadatacenter.terms.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BpTreeNodeDeserializer extends JsonDeserializer<BpTreeNode> {
  @Override
  public BpTreeNode deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException, JsonProcessingException {
    JsonNode node = jsonParser.getCodec().readTree(jsonParser);
    String id = node.get("@id")!=null? node.get("@id").asText() : null;
    String type = node.get("@type")!=null? node.get("@type").asText() : null;
    // The main reason of using this custom deserializer is the dynamic generation of the preferred label
    String prefLabel = Util.generatePreferredLabel(node);
    boolean hasChildren = node.has("hasChildren")? node.get("hasChildren").asBoolean() : false;
    List<BpTreeNode> children = new ArrayList<>();
    if (node.get("children")!=null) {
      children =
          jsonParser.getCodec().readValue(jsonParser.getCodec().treeAsTokens(node.get("children")),
              new TypeReference<List<BpTreeNode>>() {
              });
    }
    BpLinks links = null;
    if (node.get("links")!=null) {
      links = jsonParser.getCodec().treeToValue(node.get("links"), BpLinks.class);
    }
    boolean obsolete = node.has("obsolete")? node.get("obsolete").asBoolean() : false;

    return new BpTreeNode(id, type, prefLabel, hasChildren, children, links, obsolete);
  }
}