package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalRelation;
import org.metadatacenter.terms.util.HttpUtil;
import org.metadatacenter.terms.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response.Status;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;

import static org.metadatacenter.terms.util.Constants.BP_API_BASE;
import static org.metadatacenter.terms.util.Constants.BP_PROVISIONAL_RELATIONS;
import static org.metadatacenter.util.json.JsonMapper.MAPPER;

public class BpProvisionalRelationDAO {
  private final int connectTimeout;
  private final int socketTimeout;

  private static final Logger logger = LoggerFactory.getLogger(BpProvisionalRelationDAO.class);

  public BpProvisionalRelationDAO(int connectTimeout, int socketTimeout) {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public BpProvisionalRelation create(BpProvisionalRelation relation, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_PROVISIONAL_RELATIONS;
    logger.info("Url: " + url);

    // Send request to the BioPortal API
    HttpResponse response = HttpUtil.makeHttpRequest(Request.Post(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout)
        .bodyString(MAPPER.writeValueAsString(relation), ContentType.APPLICATION_JSON));

    // TODO: return the message returned by BioPortal to the top layers. response.getEntity() could be used for that:
    //EntityUtils.toString(response.getEntity(), "UTF-8");
    int statusCode = response.getStatusLine().getStatusCode();
    // The relation was successfully created
    if (statusCode == Status.CREATED.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.convertValue(bpResult, BpProvisionalRelation.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpProvisionalRelation find(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_PROVISIONAL_RELATIONS + id;
    logger.info("Url: " + url);

    HttpResponse response = HttpUtil.makeHttpRequest(Request.Get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    // The relation was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.convertValue(bpResult, BpProvisionalRelation.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

//  public void update(BpProvisionalRelation relation, String apiKey) throws IOException
//  {
//    ObjectMapper mapper = new ObjectMapper();
//    // Send request to the BioPortal API
//    HttpResponse response = Request.Patch(Constants.BP_PROVISIONAL_RELATIONS_BASE_URL + Util.getShortIdentifier
// (relation.getId()))
//        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
//            connectTimeout(connectTimeout).socketTimeout(socketTimeout)
//        .bodyString(mapper.writeValueAsString(relation), ContentType.APPLICATION_JSON).execute().returnResponse();
//
//    int statusCode = response.getStatusLine().getStatusCode();
//    throw new HTTPException(statusCode);
//  }

  public void delete(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_PROVISIONAL_RELATIONS + id;
    logger.info("Url: " + url);

    // Send request to the BioPortal API
    HttpResponse response = HttpUtil.makeHttpRequest(Request.Delete(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
            connectTimeout(connectTimeout).socketTimeout(socketTimeout));

    int statusCode = response.getStatusLine().getStatusCode();
    throw new HTTPException(statusCode);
  }
}
