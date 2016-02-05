package org.metadatacenter.terminology.bioportal.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.terminology.bioportal.domainObjects.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//public class ValueDeserializer extends JsonDeserializer<Value>
//{
//  @Override
//  public Value deserialize(JsonParser parser, DeserializationContext ctx) throws IOException
//  {
//    JsonNode node = parser.getCodec().readTree(parser);
//
//    JsonNode idNode = node.get("@id");
//    String id = (idNode != null) ? idNode.textValue() : null;
//
//    JsonNode labelNode = node.get("prefLabel");
//    String label = (labelNode != null) ? labelNode.textValue() : null;
//
//    JsonNode creatorNode = node.get("creator");
//    String creator = (creatorNode != null) ? creatorNode.textValue() : null;
//
//    String vsCollection = null;
//    JsonNode ontologyNode = node.get("ontology");
//    if (ontologyNode != null) {
//      vsCollection = ontologyNode.asText();
//    }
//    else {
//      JsonNode linksNode = node.get("links");
//      if (linksNode != null) {
//        ontologyNode = linksNode.get("ontology");
//        if (ontologyNode != null) {
//          vsCollection = ontologyNode.asText();
//        }
//      }
//    }
//
//    JsonNode definitionsNode = node.get("definition");
//    List<String> definitions = new ArrayList<>();
//    if (definitionsNode != null) {
//      for (JsonNode n : definitionsNode) {
//        definitions.add(n.asText());
//      }
//    }
//
//    JsonNode synonymsNode = node.get("synonym");
//    List<String> synonyms = new ArrayList<>();
//    if (synonymsNode != null) {
//      for (JsonNode n : synonymsNode) {
//        synonyms.add(n.asText());
//      }
//    }
//
//    JsonNode relationsNode = node.get("relations");
//    List<Relation> relations = new ArrayList<>();
//    if (relationsNode != null) {
//      for (JsonNode n : relationsNode)
//        relations.add(new ObjectMapper().treeToValue(n, Relation.class));
//    }
//
//    boolean provisional = false;
//    if (id != null)
//      // If the class id contains the substring 'provisional_classes' is because it is a provisional class
//      provisional = id.toLowerCase().contains("provisional_classes")? true : false;
//
//    JsonNode createdNode = node.get("created");
//    String created = (createdNode != null) ? createdNode.asText() : null;
//
//    // The value set will be set as null by default. The vs name will be assigned later
//    String vs = null;
//    return new Value(id, label, creator, vs, vsCollection, definitions, synonyms, relations, provisional,
//      created);
//
//  }
//}
