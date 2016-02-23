package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalClass;
import org.metadatacenter.terms.util.Util;
import org.metadatacenter.terms.util.Constants;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BpProvisionalClassDAO
{
  private final int connectTimeout;
  private final int socketTimeout;

  public BpProvisionalClassDAO(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public BpProvisionalClass create(BpProvisionalClass c, String apiKey) throws IOException
  {
    ObjectMapper mapper = new ObjectMapper();
    // Send request to the BioPortal API
    HttpResponse response = Request.Post(Constants.BP_PROVISIONAL_CLASSES_BASE_URL)
      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(connectTimeout).socketTimeout(socketTimeout)
      .bodyString(mapper.writeValueAsString(c), ContentType.APPLICATION_JSON).execute().returnResponse();

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
  public BpProvisionalClass find(String id, String apiKey) throws IOException
  {
    HttpResponse response = Request.Get(Constants.BP_PROVISIONAL_CLASSES_BASE_URL + id)
      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

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

  // TODO: the result from BioPortal is not paged. Page it in the above layer?
  public List<BpProvisionalClass> findAll(String ontology, String apiKey) throws IOException
  {
    String url = null;
    if (ontology != null) {
      // TODO: This URL  is returning 500 Internal Server Error
      url = Constants.BP_ONTOLOGIES_BASE_URL + ontology + "/" + Constants.BP_PROVISIONAL_CLASSES;
    } else {
      url = Constants.BP_PROVISIONAL_CLASSES_BASE_URL;
    }
    HttpResponse response = Request.Get(url).addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
      connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode == 200) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      List<BpProvisionalClass> provClasses = new ArrayList<>();
      for (JsonNode n : bpResult) {
        provClasses.add(mapper.convertValue(n, BpProvisionalClass.class));
      }
      return provClasses;
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public void update(BpProvisionalClass c, String apiKey) throws IOException
  {
    ObjectMapper mapper = new ObjectMapper();
    // Send request to the BioPortal API
    HttpResponse response = Request.Patch(Constants.BP_PROVISIONAL_CLASSES_BASE_URL + Util.getShortIdentifier(c.getId()))
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout)
        .bodyString(mapper.writeValueAsString(c), ContentType.APPLICATION_JSON).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    throw new HTTPException(statusCode);
  }

  public void delete(String id, String apiKey) throws IOException
  {
    // Send request to the BioPortal API
    HttpResponse response = Request.Delete(Constants.BP_PROVISIONAL_CLASSES_BASE_URL + id)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    throw new HTTPException(statusCode);
  }

}
