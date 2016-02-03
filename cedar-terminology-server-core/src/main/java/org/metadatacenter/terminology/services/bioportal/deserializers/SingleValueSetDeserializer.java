package org.metadatacenter.terminology.services.bioportal.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.terminology.services.bioportal.domainObjects.Relation;
import org.metadatacenter.terminology.services.bioportal.domainObjects.SingleValueSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SingleValueSetDeserializer extends JsonDeserializer<SingleValueSet>
{
  @Override
  public SingleValueSet deserialize(JsonParser parser, DeserializationContext ctx) throws IOException
  {
    JsonNode node = parser.getCodec().readTree(parser);

    JsonNode idNode = node.get("@id");
    String id = (idNode != null) ? idNode.textValue() : null;

    JsonNode labelNode = node.get("label");
    String label = (labelNode != null) ? labelNode.textValue() : null;

    JsonNode creatorNode = node.get("creator");
    String creator = (creatorNode != null) ? creatorNode.textValue() : null;

    JsonNode ontologyNode = node.get("ontology");
    String ontology = (ontologyNode != null) ? ontologyNode.textValue() : null;

    JsonNode definitionsNode = node.get("definitions");
    List<String> definitions = new ArrayList<>();
    if (definitionsNode != null) {
      for (JsonNode n : definitionsNode) {
        definitions.add(n.asText());
      }
    }

    JsonNode synonymsNode = node.get("synonyms");
    List<String> synonyms = new ArrayList<>();
    if (synonymsNode != null) {
      for (JsonNode n : synonymsNode) {
        synonyms.add(n.asText());
      }
    }

    JsonNode relationsNode = node.get("relations");
    List<Relation> relations = new ArrayList<>();
    if (relationsNode != null) {
      for (JsonNode n : relationsNode)
        relations.add(new ObjectMapper().treeToValue(n, Relation.class));
    }

    boolean provisional = false;
    if (id != null)
      // If the class id contains the substring 'provisional_classes' is because it is a provisional class
      provisional = id.toLowerCase().contains("provisional_classes")? true : false;

    JsonNode createdNode = node.get("created");
    String created = (createdNode != null) ? createdNode.asText() : null;

    return new SingleValueSet(id, label, creator, ontology, definitions, synonyms, relations, provisional,
      created);

  }
}
