package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.domainObjects.BpClass;
import org.metadatacenter.terms.bioportal.customObjects.BpSearchResults;
import org.metadatacenter.terms.util.Constants;
import org.metadatacenter.terms.util.Util;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;

public class BpClassDAO
{
  private final int connectTimeout;
  private final int socketTimeout;

  public BpClassDAO(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public BpSearchResults<BpClass> findValueSetsByValueSetCollection(String vsCollection, String apiKey)
    throws IOException
  {
    HttpResponse response = Request.Get(Constants.BP_SEARCH_BASE_URL +
      "?also_search_provisional=true&valueset_roots_only=true&ontology_types=VALUE_SET_COLLECTION&ontologies="
      + vsCollection).addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
      connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // Success
    if (statusCode == 200) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.readValue(mapper.treeAsTokens(bpResult), new TypeReference<BpSearchResults<BpClass>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpSearchResults<BpClass> findValuesByValueSet(String vsId, String vsCollection, String apiKey)
    throws IOException
  {
    String url = Constants.BP_ONTOLOGIES_BASE_URL + vsCollection + "/classes/" + vsId + "/children";
    HttpResponse response = Request.Get(url).addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
      connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // Success
    if (statusCode == 200) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.readValue(mapper.treeAsTokens(bpResult), new TypeReference<BpSearchResults<BpClass>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

}
