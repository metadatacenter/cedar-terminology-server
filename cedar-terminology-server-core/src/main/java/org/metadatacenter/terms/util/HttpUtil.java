package org.metadatacenter.terms.util;

import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;

import java.io.IOException;

public class HttpUtil {

  public static ClassicHttpResponse makeHttpRequest(Request request) throws IOException {
    // honor any per-call .connectTimeout(...)/.responseTimeout(...) overrides,
    // but run through our shared, pooled Executor:
    Executor exec = HttpClientFactory.executor();

    ClassicHttpResponse response = (ClassicHttpResponse) exec.execute(request).returnResponse();

    // rate‐limit delay
    try {
      Thread.sleep(300);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return response;
  }
}
