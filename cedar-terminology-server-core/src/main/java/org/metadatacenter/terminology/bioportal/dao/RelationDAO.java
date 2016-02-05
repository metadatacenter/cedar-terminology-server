package org.metadatacenter.terminology.bioportal.dao;

public class RelationDAO
{
//  private final int connectTimeout;
//  private final int socketTimeout;
//
//  public RelationDAO(int connectTimeout, int socketTimeout)
//  {
//    this.connectTimeout = connectTimeout;
//    this.socketTimeout = socketTimeout;
//  }
//
//  public Relation create(Relation relation, String apiKey) throws IOException
//  {
//    ObjectMapper mapper = new ObjectMapper();
//    // Send request to the BioPortal API
//    HttpResponse response = Request.Post(Constants.BP_PROVISIONAL_RELATIONS_BASE_URL)
//      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
//        connectTimeout(connectTimeout).socketTimeout(socketTimeout)
//      .bodyString(mapper.writeValueAsString(relation), ContentType.APPLICATION_JSON).execute().returnResponse();
//
//    int statusCode = response.getStatusLine().getStatusCode();
//    // The relation was successfully created
//    if (statusCode == 201) {
//      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
//      return mapper.convertValue(bpResult, Relation.class);
//    } else {
//      throw new HTTPException(statusCode);
//    }
//  }
//
//  public Relation find(String id, String apiKey) throws IOException
//  {
//    HttpResponse response = Request.Get(Constants.BP_PROVISIONAL_RELATIONS_BASE_URL + id)
//      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
//        connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();
//
//    int statusCode = response.getStatusLine().getStatusCode();
//    // The relation was successfully retrieved
//    if (statusCode == 200) {
//      ObjectMapper mapper = new ObjectMapper();
//      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
//      return mapper.convertValue(bpResult, Relation.class);
//    } else {
//      throw new HTTPException(statusCode);
//    }
//  }
}
