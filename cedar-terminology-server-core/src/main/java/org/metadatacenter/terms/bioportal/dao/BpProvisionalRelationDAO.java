package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalRelation;
import org.metadatacenter.terms.util.Util;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;

import static org.metadatacenter.terms.util.Constants.BP_API_BASE;
import static org.metadatacenter.terms.util.Constants.BP_PROVISIONAL_RELATIONS;

public class BpProvisionalRelationDAO
{
  private final int connectTimeout;
  private final int socketTimeout;

  public BpProvisionalRelationDAO(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public BpProvisionalRelation create(BpProvisionalRelation relation, String apiKey) throws IOException
  {
    ObjectMapper mapper = new ObjectMapper();
    // Send request to the BioPortal API
    HttpResponse response = Request.Post(BP_API_BASE + BP_PROVISIONAL_RELATIONS)
      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(connectTimeout).socketTimeout(socketTimeout)
      .bodyString(mapper.writeValueAsString(relation), ContentType.APPLICATION_JSON).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // The relation was successfully created
    if (statusCode == 201) {
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.convertValue(bpResult, BpProvisionalRelation.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpProvisionalRelation find(String id, String apiKey) throws IOException
  {
    HttpResponse response = Request.Get(BP_API_BASE + BP_PROVISIONAL_RELATIONS + id)
      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // The relation was successfully retrieved
    if (statusCode == 200) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.convertValue(bpResult, BpProvisionalRelation.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

//  public void update(BpProvisionalRelation relation, String apiKey) throws IOException
//  {
//    ObjectMapper mapper = new ObjectMapper();
//    // Send request to the BioPortal API
//    HttpResponse response = Request.Patch(Constants.BP_PROVISIONAL_RELATIONS_BASE_URL + Util.getShortIdentifier(relation.getId()))
//        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
//            connectTimeout(connectTimeout).socketTimeout(socketTimeout)
//        .bodyString(mapper.writeValueAsString(relation), ContentType.APPLICATION_JSON).execute().returnResponse();
//
//    int statusCode = response.getStatusLine().getStatusCode();
//    throw new HTTPException(statusCode);
//  }

  public void delete(String id, String apiKey) throws IOException
  {
    // Send request to the BioPortal API
    HttpResponse response = Request.Delete(BP_API_BASE + BP_PROVISIONAL_RELATIONS + id)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    throw new HTTPException(statusCode);
  }
}
