package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.domainObjects.BpProperty;
import org.metadatacenter.terms.bioportal.domainObjects.BpTreeNode;
import org.metadatacenter.terms.util.HttpUtil;
import org.metadatacenter.terms.util.ObjectConverter;
import org.metadatacenter.terms.util.Util;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response.Status;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.List;

import static org.metadatacenter.cedar.terminology.util.Constants.*;
import static org.metadatacenter.util.json.JsonMapper.MAPPER;

public class BpPropertyDAO
{
  private final int connectTimeout;
  private final int socketTimeout;

  private static final Logger logger = LoggerFactory.getLogger(BpPropertyDAO.class);

  public BpPropertyDAO(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public BpProperty find(String id, String ontology, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES + id;

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // The class was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return JsonMapper.MAPPER.convertValue(bpResult, BpProperty.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpProperty> findAllPropertiesInOntology(String ontology, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES;
    System.out.println("BioPortal url: " + url);
    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // The classes were successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return JsonMapper.MAPPER.readValue(JsonMapper.MAPPER.treeAsTokens(bpResult), new TypeReference<List<BpProperty>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpTreeNode> getTree(String id, String ontology, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES + id + "/tree";
    System.out.println("BioPortal url: " + url);
    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // The tree was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {

      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return ObjectConverter.toBpTreeNodeList(bpResult);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpProperty> getChildren(String id, String ontology, String apiKey) throws IOException
  {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES + id + "/children";

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<List<BpProperty>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpProperty> getDescendants(String id, String ontology, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES + id + "/descendants";

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // The class was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<List<BpProperty>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpProperty> getParents(String id, String ontology, String apiKey) throws IOException
  {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_PROPERTIES + id + "/parents";

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return JsonMapper.MAPPER.readValue(JsonMapper.MAPPER.treeAsTokens(bpResult), new TypeReference<List<BpProperty>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

}
