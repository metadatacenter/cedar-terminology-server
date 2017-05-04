package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.domainObjects.BpProperty;
import org.metadatacenter.terms.bioportal.domainObjects.BpTreeNode;
import org.metadatacenter.terms.util.HttpUtil;
import org.metadatacenter.terms.util.ObjectConverter;
import org.metadatacenter.terms.util.Util;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.core.Response.Status;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.*;
import static org.metadatacenter.util.json.JsonMapper.MAPPER;

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
    if (statusCode == Status.OK.getStatusCode()) {
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
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = JsonMapper.MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return JsonMapper.MAPPER.readValue(JsonMapper.MAPPER.treeAsTokens(bpResult), new TypeReference<List<BpProperty>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpTreeNode> getTree(String id, String ontology, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES + id + "/tree";
    System.out.println("BioPortal url: " + url);
    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The tree was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {

      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return ObjectConverter.toBpTreeNodeList(bpResult);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpProperty> getChildren(String id, String ontology, String apiKey) throws IOException
  {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES + id + "/children";

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<List<BpProperty>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpProperty> getDescendants(String id, String ontology, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES + id + "/descendants";

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The class was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<List<BpProperty>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpProperty> getParents(String id, String ontology, String apiKey) throws IOException
  {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES + id + "/parents";

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = JsonMapper.MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return JsonMapper.MAPPER.readValue(JsonMapper.MAPPER.treeAsTokens(bpResult), new TypeReference<List<BpProperty>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

}
