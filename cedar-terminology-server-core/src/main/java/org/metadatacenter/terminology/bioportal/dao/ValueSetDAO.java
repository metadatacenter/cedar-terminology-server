package org.metadatacenter.terminology.bioportal.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terminology.bioportal.domainObjects.ValueSet;
import org.metadatacenter.terminology.bioportal.domainObjects.ValueSets;
import org.metadatacenter.terminology.util.Util;
import org.metadatacenter.terminology.util.Constants;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;

//public class ValueSetDAO
//{
//  private final int connectTimeout;
//  private final int socketTimeout;
//
//  public ValueSetDAO(int connectTimeout, int socketTimeout)
//  {
//    this.connectTimeout = connectTimeout;
//    this.socketTimeout = socketTimeout;
//  }
//
//  public ValueSet create(ValueSet vs, String apiKey) throws IOException
//  {
//    ObjectMapper mapper = new ObjectMapper();
//    // Send request to the BioPortal API
//    HttpResponse response = Request.Post(Constants.BP_PROVISIONAL_CLASSES_BASE_URL)
//      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
//        connectTimeout(connectTimeout).socketTimeout(socketTimeout)
//      .bodyString(mapper.writeValueAsString(vs), ContentType.APPLICATION_JSON).execute().returnResponse();
//
//    int statusCode = response.getStatusLine().getStatusCode();
//    // Success
//    if (statusCode == 201) {
//      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
//      return mapper.convertValue(bpResult, ValueSet.class);
//    } else {
//      throw new HTTPException(statusCode);
//    }
//  }
//
//  // TODO: expand it to deal with regular classes.
//  // TODO: Issue: not able to retrieve provisional classes from bioportal using the full id
//  public ValueSet find(String id, String apiKey) throws IOException
//  {
//    HttpResponse response = Request.Get(Constants.BP_PROVISIONAL_CLASSES_BASE_URL + id)
//      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
//        connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();
//
//    int statusCode = response.getStatusLine().getStatusCode();
//    // Success
//    if (statusCode == 200) {
//      ObjectMapper mapper = new ObjectMapper();
//      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
//      return mapper.convertValue(bpResult, ValueSet.class);
//    } else {
//      throw new HTTPException(statusCode);
//    }
//  }
//
//  public ValueSets findByValueSetCollection(String vsCollection, String apiKey) throws IOException
//  {
//    HttpResponse response = Request.Get(Constants.BP_SEARCH_BASE_URL +
//      "?also_search_provisional=true&valueset_roots_only=true&ontology_types=VALUE_SET_COLLECTION&ontologies="
//      + vsCollection).addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
//      connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();
//
//    int statusCode = response.getStatusLine().getStatusCode();
//    // Success
//    if (statusCode == 200) {
//      ObjectMapper mapper = new ObjectMapper();
//      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
//      return mapper.convertValue(bpResult, ValueSets.class);
//    } else {
//      throw new HTTPException(statusCode);
//    }
//  }
//
//}
