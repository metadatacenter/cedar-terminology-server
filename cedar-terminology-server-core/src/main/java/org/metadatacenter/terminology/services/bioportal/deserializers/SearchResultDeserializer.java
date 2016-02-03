package org.metadatacenter.terminology.services.bioportal.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.terminology.services.bioportal.domainObjects.SearchResult;

import java.io.IOException;

public class SearchResultDeserializer extends JsonDeserializer<SearchResult>
{
  @Override
  public SearchResult deserialize(JsonParser parser, DeserializationContext ctx) throws IOException
  {
    JsonNode node = parser.getCodec().readTree(parser);
    
    JsonNode idNode = node.get("@id");
    String id = (idNode != null) ? idNode.textValue() : null;

    JsonNode labelNode = node.get("prefLabel");
    String label = (labelNode != null) ? labelNode.textValue() : null;
    
    // TODO: resultType: use specific attribute implemented on the BioPortal side
    SearchResult.ResultType resultType = SearchResult.ResultType.CLASS;

    Boolean provisional = false;
    JsonNode bpTypeNode = node.get("@type");
    if (bpTypeNode != null) {
      if (bpTypeNode.asText().compareTo("http://data.bioontology.org/metadata/ProvisionalClass") == 0){
        provisional = true;
      }
    }
    
    String source = null;
    JsonNode ontologyNode = node.get("ontology");
    if (ontologyNode != null) {
      source = ontologyNode.asText();
    }
    else {
      JsonNode linksNode = node.get("links");
      if (linksNode != null) {
        ontologyNode = linksNode.get("ontology");
        if (ontologyNode != null) {
          source = ontologyNode.asText();
        }
      }
    }

    JsonNode creatorNode = node.get("creator");
    String creator = (creatorNode != null) ? creatorNode.textValue() : null;

    JsonNode createdNode = node.get("created");
    String created = (createdNode != null) ? createdNode.textValue() : null;

    return new SearchResult(id, label, resultType, provisional, source, creator, created);
  }
}
