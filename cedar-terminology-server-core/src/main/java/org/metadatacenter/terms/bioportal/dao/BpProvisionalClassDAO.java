package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.cedar.terminology.util.Constants;
import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalClass;
import org.metadatacenter.terms.util.HttpUtil;
import org.metadatacenter.terms.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response.Status;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.List;

import static org.metadatacenter.cedar.terminology.util.Constants.*;
import static org.metadatacenter.util.json.JsonMapper.MAPPER;

public class BpProvisionalClassDAO {
  private final int connectTimeout;
  private final int socketTimeout;

  private static final Logger logger = LoggerFactory.getLogger(BpProvisionalClassDAO.class);

  public BpProvisionalClassDAO(int connectTimeout, int socketTimeout) {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public BpProvisionalClass create(BpProvisionalClass c, String apiKey) throws IOException {
    // Send request to the BioPortal API
    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.post(BP_API_BASE + BP_PROVISIONAL_CLASSES)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout))
        .bodyString(MAPPER.writeValueAsString(c), ContentType.APPLICATION_JSON));

    int statusCode = response.getCode();
    // The class was successfully created
    if (statusCode == Status.CREATED.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return MAPPER.convertValue(bpResult, BpProvisionalClass.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  // TODO: Issue: not able to retrieve provisional classes from bioportal using the full id
  public BpProvisionalClass find(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_PROVISIONAL_CLASSES + id;
    logger.info("Url: " + url);

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // The class was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return MAPPER.convertValue(bpResult, BpProvisionalClass.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpPagedResults<BpProvisionalClass> findAll(String ontology, int page, int pageSize, String apiKey) throws IOException {
    BpPagedResults<BpProvisionalClass> results = null;
    String url = null;
    if (ontology != null) {
      url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + Constants.BP_PROVISIONAL_CLASSES;
    } else {
      url = BP_API_BASE + BP_PROVISIONAL_CLASSES;
    }
    url = url + "?page=" + page + "&pagesize=" + pageSize;
    logger.info("Url: " + url);

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      if (ontology != null) {
        // If the ontology is specified, BioPortal does not return paged results, so we have to build them.
        // TODO: task for the BioPortal team: provide paged results when the ontology is specified
        List<BpProvisionalClass> provClasses = MAPPER.readValue(MAPPER.treeAsTokens(bpResult),
            new TypeReference<List<BpProvisionalClass>>() {
        });
        results = new BpPagedResults<>(1, 1, provClasses.size(), null, null, provClasses);
        return results;
      } else {
        return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<BpPagedResults<BpProvisionalClass>>() {
        });
      }
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public void update(BpProvisionalClass c, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_PROVISIONAL_CLASSES + Util.getShortIdentifier(c.getId());
    logger.info("Url: " + url);

    // Send request to the BioPortal API
    HttpResponse response = HttpUtil.makeHttpRequest(Request.patch(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout))
        .bodyString(MAPPER.writeValueAsString(c), ContentType.APPLICATION_JSON));

    int statusCode = response.getCode();
    throw new HTTPException(statusCode);
  }

  public void delete(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_PROVISIONAL_CLASSES + id;
    logger.info("Url: " + url);

    // Send request to the BioPortal API
    HttpResponse response = HttpUtil.makeHttpRequest(Request.delete(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    throw new HTTPException(statusCode);
  }

}
