package org.metadatacenter.terms.util;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;

import java.io.IOException;

import static org.metadatacenter.cedar.terminology.util.Constants.*;

public class HttpUtil {

  public static HttpResponse makeHttpRequest(Request request) throws IOException {
    HttpResponse response = request.execute().returnResponse();
//    int statusCode = response.getStatusLine().getStatusCode();
//    int count = 0;
//    int maxAttempts = 20;
    // Repeat some requests for safety
//    while ((statusCode == 429 || statusCode == 500) && count++ < maxAttempts) {
//      System.out.println("BioPortal returned error " + statusCode + ". Trying it again...");
//      //Delay between calls
//      try {
//        Thread.sleep(BP_API_WAIT_TIME * 5);
//      } catch (InterruptedException e) {
//        e.printStackTrace();
//      }
//      response = request.execute().returnResponse();
//      statusCode = response.getStatusLine().getStatusCode();
//    }
    //Delay between calls to avoid status 429 (too many requests)
    try {
      Thread.sleep(BP_API_WAIT_TIME);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return response;
  }
}
