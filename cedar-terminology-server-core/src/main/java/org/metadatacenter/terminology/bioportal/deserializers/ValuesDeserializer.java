package org.metadatacenter.terminology.bioportal.deserializers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.terminology.bioportal.domainObjects.Values;
import org.metadatacenter.terminology.bioportal.domainObjects.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ValuesDeserializer extends JsonDeserializer<Values>
{
  @Override
  public Values deserialize(JsonParser parser, DeserializationContext ctx) throws IOException
  {
    JsonNode node = parser.getCodec().readTree(parser);

    JsonNode pageNode = node.get("page");
    int page = (pageNode != null) ? pageNode.intValue() : -1;

    JsonNode pageCountNode = node.get("pageCount");
    int pageCount = (pageCountNode != null) ? pageCountNode.intValue() : -1;

    JsonNode prevPageNode = node.get("prevPage");
    int prevPage = (prevPageNode != null) ? prevPageNode.intValue() : -1;

    JsonNode nextPageNode = node.get("nextPage");
    int nextPage = (nextPageNode != null) ? nextPageNode.intValue() : -1;

    List<Value> collection = new ArrayList<>();
    int pageSize = -1;
    JsonNode resultsNode = node.get("collection");
    if (resultsNode != null) {
      pageSize = resultsNode.size();
      for (JsonNode n : resultsNode) {
        collection.add(new ObjectMapper().treeToValue(n, Value.class));
      }
    }
    return new Values(page, pageCount, pageSize, prevPage, nextPage, collection);
  }
}
