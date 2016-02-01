package org.metadatacenter.terminology.services.bioportal.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terminology.services.bioportal.domainObjects.OntologyClass;
import org.metadatacenter.terminology.services.util.Util;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URLEncoder;

import static org.metadatacenter.terminology.services.util.Constants.BP_PROVISIONAL_CLASSES_BASE_URL;

public class OntologyClassDAO
{
  private final int connectTimeout;
  private final int socketTimeout;

  public OntologyClassDAO(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public OntologyClass createOntologyClass(OntologyClass c, String apiKey) throws IOException
  {
    ObjectMapper mapper = new ObjectMapper();
    // Send request to the BioPortal API
    HttpResponse response = Request.Post(BP_PROVISIONAL_CLASSES_BASE_URL)
      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(connectTimeout).socketTimeout(socketTimeout)
      .bodyString(mapper.writeValueAsString(c), ContentType.APPLICATION_JSON).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // The class was successfully created
    if (statusCode == 201) {
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.convertValue(bpResult, OntologyClass.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  // TODO: expand to deal with regular classes.
  // TODO: Issue: not able to retrieve provisional classes from bioportal using the full id
  public OntologyClass findOntologyClass(String id, String apiKey) throws IOException
  {
    HttpResponse response = Request.Get(BP_PROVISIONAL_CLASSES_BASE_URL + id)
      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // The class was successfully retrieved
    if (statusCode == 200) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.convertValue(bpResult, OntologyClass.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  //  public OntologyClass findProvisionalClassById(String id, String apiKey) throws Exception;
  //  public List<OntologyClass> findAllProvisionalClasses() throws Exception;
  //  public OntologyClass updateProvisionalClass(OntologyClass provisionalClass, String apiKey) throws IOException;
  //  public void deleteProvisionalClass(OntologyClass provisionalClass, String apiKey) throws IOException;

}
