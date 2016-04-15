package org.metadatacenter.terms.util;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;

import java.io.IOException;

import static org.metadatacenter.terms.util.Constants.BP_API_WAIT_TIME;

public class HttpUtil {

  public static HttpResponse makeHttpRequest(Request request) throws IOException {
    HttpResponse response = request.execute().returnResponse();
    int statusCode = response.getStatusLine().getStatusCode();
    int count = 0;
    int maxAttempts = 20;
    // If too many requests try again...
    while (statusCode == 429 && count++ < maxAttempts) {
      System.out.println("BioPortal returned HTTP 429: too many requests. Trying it again...");
      //Delay between calls
      try {
        Thread.sleep(BP_API_WAIT_TIME * 20);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      response = request.execute().returnResponse();
      statusCode = response.getStatusLine().getStatusCode();
    }
    //Delay between calls to avoid status 429 (too many requests)
    try {
      Thread.sleep(BP_API_WAIT_TIME);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return response;
  }
}
