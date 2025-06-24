package org.metadatacenter.terms.bioportal.dao;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.Timeout;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalRelation;
import org.metadatacenter.terms.util.HttpUtil;
import org.metadatacenter.terms.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response.Status;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;

import static org.metadatacenter.cedar.terminology.util.Constants.BP_API_BASE;
import static org.metadatacenter.cedar.terminology.util.Constants.BP_PROVISIONAL_RELATIONS;
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
    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.post(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout))
        .bodyString(MAPPER.writeValueAsString(relation), ContentType.APPLICATION_JSON));

    // TODO: return the message returned by BioPortal to the top layers. response.getEntity() could be used for that:
    //EntityUtils.toString(response.getEntity(), "UTF-8");
    int statusCode = response.getCode();
    // The relation was successfully created
    if (statusCode == Status.CREATED.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
      return MAPPER.convertValue(bpResult, BpProvisionalRelation.class);
    } else {
      throw new HTTPException(statusCode);
    }
  }

  public BpProvisionalRelation find(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_PROVISIONAL_RELATIONS + id;
    logger.info("Url: " + url);

    ClassicHttpResponse response = HttpUtil.makeHttpRequest(Request.get(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    // The relation was successfully retrieved
    if (statusCode == Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(response.getEntity().getContent());
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
//            connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds
//            (socketTimeout))
//        .bodyString(mapper.writeValueAsString(relation), ContentType.APPLICATION_JSON).execute().returnResponse();
//
//    int statusCode = response.getCode();
//    throw new HTTPException(statusCode);
//  }

  public void delete(String id, String apiKey) throws IOException {
    String url = BP_API_BASE + BP_PROVISIONAL_RELATIONS + id;
    logger.info("Url: " + url);

    // Send request to the BioPortal API
    HttpResponse response = HttpUtil.makeHttpRequest(Request.delete(url)
        .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(Timeout.ofMilliseconds(connectTimeout)).responseTimeout(Timeout.ofMilliseconds(socketTimeout)));

    int statusCode = response.getCode();
    throw new HTTPException(statusCode);
  }
}
