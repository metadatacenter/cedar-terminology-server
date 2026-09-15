package org.metadatacenter.terms.util;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.metadatacenter.config.OutboundTimeoutOverride;
import org.metadatacenter.util.http.HttpTimeouts;

import java.io.IOException;

public class HttpUtil {

  /**
   * The bounds every BioPortal call runs under.
   *
   * <p>BioPortal is a registry CEDAR does not operate, so these calls are the external class of
   * outbound call: that class's pool, its lease timeout and its retry policy. The connect and
   * response timeouts stay BioPortal's own, from {@code terminology.bioPortal} in
   * {@code cedar-main.yml}, because they were chosen for this registry and are longer than any
   * shared value should be.
   *
   * <p>Until {@link #install} runs, the external class's own timeouts apply, so a test or a tool
   * makes a bounded call without configuring anything. This replaced a client built in a static
   * block with a pool size, a lease timeout and a set of default timeouts written into the code,
   * which no configuration could reach.
   */
  private static volatile HttpTimeouts bioPortal = HttpTimeouts.EXTERNAL;

  /** Hands over BioPortal's configured connect and response timeouts, once, at startup. */
  public static void install(int connectMillis, int responseMillis) {
    bioPortal = HttpTimeouts.EXTERNAL.with(new OutboundTimeoutOverride(connectMillis, responseMillis));
  }

  /**
   * Run a BioPortal request through the shared, pooled external client.
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
    return bioPortal.execute(request);
  }
}
