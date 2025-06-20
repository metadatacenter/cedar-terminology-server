package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.util.Timeout;
import org.metadatacenter.terms.bioportal.domainObjects.*;
import org.metadatacenter.terms.util.HttpUtil;
import org.metadatacenter.terms.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response.Status;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.metadatacenter.cedar.terminology.util.Constants.*;
import static org.metadatacenter.util.json.JsonMapper.MAPPER;

public class BpOntologyDAO {

  private final int connectTimeout;
  private final int socketTimeout;

  private static final Logger logger = LoggerFactory.getLogger(BpOntologyDAO.class);

  public BpOntologyDAO(int connectTimeout, int socketTimeout) {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public BpOntology find(String id, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + id + "?" + BP_INCLUDE_ALL;
    logger.info("Url: " + url);

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // The ontology was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return MAPPER.convertValue(bpResult, BpOntology.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpOntologySubmission getLatestSubmission(String id, String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + id + "/latest_submission";
    logger.info("Url: " + url);

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // The ontology was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return MAPPER.convertValue(bpResult, BpOntologySubmission.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpOntology> findAll(String apiKey) throws HTTPException, IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + "?" + BP_INCLUDE_ALL;
    logger.info("Url: " + url);

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // The ontologies were successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
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
    logger.info("Url: " + url);

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // The ontology was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return MAPPER.convertValue(bpResult, BpOntologyMetrics.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public List<BpOntologyCategory> getOntologyCategories(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_ONTOLOGIES + id + "/categories";
    logger.info("Url: " + url);

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // The ontology was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
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
    String url = BP_API_BASE + BP_ONTOLOGIES + id + "/" + BP_CLASSES + "roots?include=prefLabel,hasChildren,created,synonym,definition";
    logger.info("Url: " + url);

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
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
    String url = BP_API_BASE + BP_ONTOLOGIES + id + "/" + BP_PROPERTIES + "roots";
    logger.info("Url: " + url);

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // Success
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
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
