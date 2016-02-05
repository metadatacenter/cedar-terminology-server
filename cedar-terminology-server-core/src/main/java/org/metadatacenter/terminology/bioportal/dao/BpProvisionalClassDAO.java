package org.metadatacenter.terminology.bioportal.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terminology.bioportal.domainObjects2.bioportal.BpProvisionalClass;
import org.metadatacenter.terminology.util.Util;
import org.metadatacenter.terminology.util.Constants;

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

//  // TODO: expand it to deal with regular classes.
//  // TODO: Issue: not able to retrieve provisional classes from bioportal using the full id
//  public OntologyClass find(String id, String apiKey) throws IOException
//  {
//    HttpResponse response = Request.Get(Constants.BP_PROVISIONAL_CLASSES_BASE_URL + id)
//      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
//        connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();
//
//    int statusCode = response.getStatusLine().getStatusCode();
//    // The class was successfully retrieved
//    if (statusCode == 200) {
//      ObjectMapper mapper = new ObjectMapper();
//      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
//      return mapper.convertValue(bpResult, OntologyClass.class);
//    } else {
//      throw new HTTPException(statusCode);
//    }
//  }
//
//  // Note: the result from BioPortal is not paged
//  public List<OntologyClass> findAllProvisionalClasses(String ontology, String apiKey) throws IOException
//  {
//    HttpResponse response = null;
//    if (ontology != null) {
//      response = Request.Get(Constants.BP_ONTOLOGIES_BASE_URL + ontology + "/" + Constants.BP_PROVISIONAL_CLASSES)
//        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
//          connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();
//    } else {
//      response = Request.Get(Constants.BP_PROVISIONAL_CLASSES_BASE_URL)
//        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
//          connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();
//    }
//
//    int statusCode = response.getStatusLine().getStatusCode();
//
//    if (statusCode == 200) {
//      ObjectMapper mapper = new ObjectMapper();
//      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
//      List<OntologyClass> classes = new ArrayList<>();
//      for (JsonNode n : bpResult) {
//        classes.add(mapper.convertValue(n, OntologyClass.class));
//      }
//      return classes;
//    } else {
//      throw new HTTPException(statusCode);
//    }
//  }

}
