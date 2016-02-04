package org.metadatacenter.terminology.services.bioportal.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terminology.services.bioportal.domainObjects.Value;
import org.metadatacenter.terminology.services.bioportal.domainObjects.Values;
import org.metadatacenter.terminology.services.util.Util;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;

import static org.metadatacenter.terminology.services.util.Constants.BP_ONTOLOGIES_BASE_URL;

public class ValueDAO
{
  private final int connectTimeout;
  private final int socketTimeout;

  public ValueDAO(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  public Values findByValueSet(String vsId, String vsCollection, String apiKey) throws IOException
  {
    // TODO: currently, this call does not include provisional classes and the value set must be a regular class (and not a provisional class)
    String url = BP_ONTOLOGIES_BASE_URL + vsCollection + "/classes/" + vsId + "/children";
    HttpResponse response = Request.Get(url)
      .addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // Success
    if (statusCode == 200) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      // Set vs names
      Values values =  mapper.convertValue(bpResult, Values.class);
      for (Value v : values.getValues())
        v.setVs(vsId);
      return values;
    } else {
      throw new HTTPException(statusCode);
    }
  }

}
