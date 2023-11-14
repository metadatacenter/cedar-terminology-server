package org.metadatacenter.terms.bioportal.domainObjects.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.terms.bioportal.domainObjects.BpLinks;
import org.metadatacenter.terms.bioportal.domainObjects.BpProperty;
import org.metadatacenter.terms.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BpPropertyDeserializer extends JsonDeserializer<BpProperty> {
  @Override
  public BpProperty deserialize(JsonParser jsonParser, DeserializationContext ctx) throws IOException, JsonProcessingException {
    JsonNode node = jsonParser.getCodec().readTree(jsonParser);
    String id = node.get("@id")!=null? node.get("@id").asText() : null;
    String type = node.get("@type")!=null? node.get("@type").asText() : null;
    String propertyType = node.get("propertyType")!=null? node.get("propertyType").asText() : null;
    String ontologyType = node.get("ontologyType")!=null? node.get("ontologyType").asText() : null;
    // The main reason of using this custom deserializer is the dynamic generation of the preferred label
    String prefLabel = Util.generatePreferredLabel(node);
    List<String> label = new ArrayList();
    if (node.get("label")!=null && node.get("label").size() > 0) {
      for (JsonNode n : node.get("label")) {
        label.add(n.asText());
      }
    }
    List<String> definition = new ArrayList();
    if (node.get("definition")!=null && node.get("definition").size() > 0) {
      for (JsonNode n : node.get("definition")) {
        definition.add(n.asText());
      }
    }
    BpLinks links = null;
    if (node.get("links")!=null) {
      links = jsonParser.getCodec().treeToValue(node.get("links"), BpLinks.class);
    }
    boolean hasChildren = node.has("hasChildren")? node.get("hasChildren").asBoolean() : false;

    return new BpProperty(id, type, propertyType, ontologyType, prefLabel, label, definition, links, hasChildren);
  }
}
