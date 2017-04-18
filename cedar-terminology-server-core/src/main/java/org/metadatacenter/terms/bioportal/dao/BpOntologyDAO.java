package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.domainObjects.*;
import org.metadatacenter.terms.util.Constants;
import org.metadatacenter.terms.util.HttpUtil;
import org.metadatacenter.terms.util.Util;

import javax.ws.rs.core.Response.Status;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.*;
import static org.metadatacenter.util.json.JsonMapper.MAPPER;

public class BpOntologyDAO {

  private final int connectTimeout;
  private final int socketTimeout;

  public BpOntologyDAO(int connectTimeout, int socketTimeout) {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public BpOntology find(String id, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + id + "?" + BP_INCLUDE_ALL;

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The ontology was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.convertValue(bpResult, BpOntology.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpOntologySubmission getLatestSubmission(String id, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + id + "/latest_submission";

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The ontology was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.convertValue(bpResult, BpOntologySubmission.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpOntology> findAll(String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + "?" + BP_INCLUDE_ALL;

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The ontologies were successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      List<BpOntology> ontologies = new ArrayList<>();
      for (JsonNode n : bpResult) {
        ontologies.add(MAPPER.convertValue(n, BpOntology.class));
      }
      return ontologies;
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpOntologyMetrics getOntologyMetrics(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + id + "/metrics";

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The ontology was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.convertValue(bpResult, BpOntologyMetrics.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpOntologyCategory> getOntologyCategories(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + id + "/categories";

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The ontology was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      List<BpOntologyCategory> categories = new ArrayList<>();
      for (JsonNode n : bpResult) {
        categories.add(MAPPER.convertValue(n, BpOntologyCategory.class));
      }
      return categories;
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpClass> getRootClasses(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + id + "/" + Constants.BP_CLASSES + "roots?include=prefLabel,hasChildren,created,synonym,definition";

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      List<BpClass> roots = new ArrayList<>();
      for (JsonNode n : bpResult) {
        roots.add(MAPPER.convertValue(n, BpClass.class));
      }
      return roots;
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpProperty> getRootProperties(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + id + "/" + Constants.BP_PROPERTIES + "roots";

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      List<BpProperty> roots = new ArrayList<>();
      for (JsonNode n : bpResult) {
        roots.add(MAPPER.convertValue(n, BpProperty.class));
      }
      return roots;
    } else {
      throw new HTTPException(statusCode);
    }
  }

}