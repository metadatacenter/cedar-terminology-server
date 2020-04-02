package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.domainObjects.BpClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpTreeNode;
import org.metadatacenter.terms.util.HttpUtil;
import org.metadatacenter.terms.util.ObjectConverter;
import org.metadatacenter.terms.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response.Status;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.*;
import static org.metadatacenter.util.json.JsonMapper.MAPPER;

public class BpClassDAO
{
  private final int connectTimeout;
  private final int socketTimeout;

  private static final Logger logger = LoggerFactory.getLogger(BpClassDAO.class);

  public BpClassDAO(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public BpClass find(String id, String ontology, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_CLASSES + id + "?include=prefLabel,hasChildren,created,synonym,definition";
    logger.info("Url: " + url);

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The class was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.convertValue(bpResult, BpClass.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpPagedResults<BpClass> findAllClassesInOntology(String ontology, int page, int pageSize, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_CLASSES + "?include=prefLabel,hasChildren,created,synonym,definition"
        + "&page=" + page + "&pagesize=" + pageSize;
    logger.info("Url: " + url);

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The classes were successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<BpPagedResults<BpClass>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpTreeNode> getTree(String id, String ontology, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_CLASSES + id + "/tree";
    logger.info("Url: " + url);

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

  public BpPagedResults<BpClass> getChildren(String id, String ontology, int page, int pageSize, String apiKey) throws IOException
  {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_CLASSES + id + "/children?"
        + "&page=" + page + "&pagesize=" + pageSize + "&include=prefLabel,hasChildren,created,synonym,definition";
    logger.info("Url: " + url);

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<BpPagedResults<BpClass>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpPagedResults<BpClass> getDescendants(String id, String ontology, int page, int pageSize, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_CLASSES + Util.encodeIfNeeded(id) + "/descendants?"
    + "&page=" + page + "&pagesize=" + pageSize + "&include=prefLabel,hasChildren,created,synonym,definition";
    logger.info("Url: " + url);

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The class was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<BpPagedResults<BpClass>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpClass> getParents(String id, String ontology, String apiKey) throws IOException
  {
    String url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + BP_CLASSES
        + id + "/parents" + "?include=prefLabel,hasChildren,created,synonym,definition";
    logger.info("Url: " + url);

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      List<BpClass> children = new ArrayList<>();
      for (JsonNode n : bpResult) {
        children.add(MAPPER.convertValue(n, BpClass.class));
      }
      return children;
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpPagedResults<BpClass> findValueSetsByValueSetCollection(String vsCollection, int page, int pageSize, String apiKey)
    throws IOException
  {
    String url = BP_API_BASE + BP_SEARCH +
        "?also_search_provisional=true&valueset_roots_only=true&ontology_types=VALUE_SET_COLLECTION&ontologies="
        + vsCollection + "&page=" + page + "&pagesize=" + pageSize;
    logger.info("Url: " + url);

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<BpPagedResults<BpClass>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpPagedResults<BpClass> findValuesByValueSet(String vsId, String vsCollection, int page, int pageSize, String apiKey)
    throws IOException
  {
    String url = BP_API_BASE + BP_ONTOLOGIES + vsCollection + "/classes/" + vsId + "/children?"
        + "page=" + page + "&pagesize=" + pageSize + "&include=prefLabel,hasChildren,created,synonym,definition";
    // In the case of the CADSR value sets collection, we also need to return the properties to be able to access to the
    // source terminology URI, which is stored using the property skos:relatedMatch, and to the VALIDVALUE, which is
    // stored using the skos:notation property.
    if (vsCollection.equals(CADSR_VALUE_SETS_ONTOLOGY)) {
      url = url + ",properties";
    }
    logger.info("Url: " + url);

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<BpPagedResults<BpClass>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }


}
