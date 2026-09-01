package org.metadatacenter.terms.util;

import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;

import java.io.IOException;

public class HttpUtil {

  /**
   * Run a BioPortal request through the shared, pooled executor.
   *
   * <p>Per-call {@code .connectTimeout(...)}/{@code .responseTimeout(...)} overrides are honored;
   * everything else comes from {@link HttpClientFactory}.
   *
   * <p>This used to sleep 300 ms after every response, on all 31 call sites. The delay arrived in
   * 2016 as one half of a rate-limit mechanism whose other half — retry on 429 with backoff — was
   * disabled weeks later and deleted in the move to HttpClient 5, and the surviving half never
   * limited anything: it runs after {@code returnResponse()} has buffered the entity and released
   * the connection, so concurrent Jetty workers still issue concurrent BioPortal requests. All it
   * bounded was a single thread, at roughly three requests a second, while holding that thread. On
   * the search path, where one CEDAR request makes several BioPortal calls in sequence, the delays
   * added up into seconds of latency per keystroke. Rate limiting, if it is wanted, belongs in a
   * shared limiter over the outbound rate paired with a bounded retry that honors {@code
   * Retry-After}, not in a fixed sleep charged to every caller. The rate that mechanism was
   * written against, recorded alongside the deleted constant, was 15 calls a second per key.
   */
  public static ClassicHttpResponse makeHttpRequest(Request request) throws IOException {
    Executor exec = HttpClientFactory.executor();

    return (ClassicHttpResponse) exec.execute(request).returnResponse();
  }
}
