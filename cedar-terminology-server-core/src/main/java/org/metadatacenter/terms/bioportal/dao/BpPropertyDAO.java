package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.domainObjects.BpProperty;
import org.metadatacenter.terms.util.HttpUtil;
import org.metadatacenter.terms.util.Util;
import org.metadatacenter.util.json.JsonMapper;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.*;

public class BpPropertyDAO
{
  private final int connectTimeout;
  private final int socketTimeout;

  public BpPropertyDAO(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public BpProperty find(String id, String ontology, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES + id;

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The class was successfully retrieved
    if (statusCode == 200) {
      JsonNode bpResult = JsonMapper.MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return JsonMapper.MAPPER.convertValue(bpResult, BpProperty.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpProperty> findAllPropertiesInOntology(String ontology, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES;
    System.out.println("BioPortal url: " + url);
    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The classes were successfully retrieved
    if (statusCode == 200) {
      JsonNode bpResult = JsonMapper.MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return JsonMapper.MAPPER.readValue(JsonMapper.MAPPER.treeAsTokens(bpResult), new TypeReference<List<BpProperty>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }



}
