package org.metadatacenter.terminology.services.bioportal.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terminology.services.bioportal.domainObjects.OntologyClass;
import org.metadatacenter.terminology.services.bioportal.domainObjects.Relation;
import org.metadatacenter.terminology.services.util.Util;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;

import static org.metadatacenter.terminology.services.util.Constants.BP_PROVISIONAL_CLASSES_BASE_URL;
import static org.metadatacenter.terminology.services.util.Constants.BP_PROVISIONAL_RELATIONS_BASE_URL;

public class RelationDAO
{
  private final int connectTimeout;
  private final int socketTimeout;

  public RelationDAO(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public Relation create(Relation relation, String apiKey) throws IOException
  {
    ObjectMapper mapper = new ObjectMapper();
    // Send request to the BioPortal API
    HttpResponse response = Request.Post(BP_PROVISIONAL_RELATIONS_BASE_URL)
      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(connectTimeout).socketTimeout(socketTimeout)
      .bodyString(mapper.writeValueAsString(relation), ContentType.APPLICATION_JSON).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // The relation was successfully created
    if (statusCode == 201) {
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.convertValue(bpResult, Relation.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public Relation find(String id, String apiKey) throws IOException
  {
    HttpResponse response = Request.Get(BP_PROVISIONAL_RELATIONS_BASE_URL + id)
      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // The relation was successfully retrieved
    if (statusCode == 200) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.convertValue(bpResult, Relation.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }
}
