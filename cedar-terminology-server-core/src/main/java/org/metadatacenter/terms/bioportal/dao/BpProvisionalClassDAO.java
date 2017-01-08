package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.domainObjects.BpClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalClass;
import org.metadatacenter.terms.util.Constants;
import org.metadatacenter.terms.util.HttpUtil;
import org.metadatacenter.terms.util.Util;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.*;

public class BpProvisionalClassDAO {
  private final int connectTimeout;
  private final int socketTimeout;

  public BpProvisionalClassDAO(int connectTimeout, int socketTimeout) {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public BpProvisionalClass create(BpProvisionalClass c, String apiKey) throws IOException {
    ObjectMapper mapper = new ObjectMapper();

    // Send request to the BioPortal API
    HttpResponse response = HttpUtil.makeHttpRequest(Request.Post(BP_API_BASE + BP_PROVISIONAL_CLASSES)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout)
        .bodyString(mapper.writeValueAsString(c), ContentType.APPLICATION_JSON));

    int statusCode = response.getStatusLine().getStatusCode();
    // The class was successfully created
    if (statusCode == 201) {
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.convertValue(bpResult, BpProvisionalClass.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  // TODO: Issue: not able to retrieve provisional classes from bioportal using the full id
  public BpProvisionalClass find(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_PROVISIONAL_CLASSES + id;

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The class was successfully retrieved
    if (statusCode == 200) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.convertValue(bpResult, BpProvisionalClass.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpPagedResults<BpProvisionalClass> findAll(String ontology, int page, int pageSize, String apiKey) throws IOException {
    String url = null;
    if (ontology != null) {
      url = BP_API_BASE + BP_ONTOLOGIES + ontology + "/" + Constants.BP_PROVISIONAL_CLASSES;
    } else {
      url = BP_API_BASE + BP_PROVISIONAL_CLASSES;
    }
    url = url + "?page=" + page + "&pagesize=" + pageSize;
    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == 200) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.readValue(mapper.treeAsTokens(bpResult), new TypeReference<BpPagedResults<BpProvisionalClass>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public void update(BpProvisionalClass c, String apiKey) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    String url = BP_API_BASE + BP_PROVISIONAL_CLASSES + Util.getShortIdentifier(c.getId());

    // Send request to the BioPortal API
    HttpResponse response = HttpUtil.makeHttpRequest(Request.Patch(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout)
        .bodyString(mapper.writeValueAsString(c), ContentType.APPLICATION_JSON));

    int statusCode = response.getStatusLine().getStatusCode();
    throw new HTTPException(statusCode);
  }

  public void delete(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_PROVISIONAL_CLASSES + id;

    // Send request to the BioPortal API
    HttpResponse response = HttpUtil.makeHttpRequest(Request.Delete(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    throw new HTTPException(statusCode);
  }

}
