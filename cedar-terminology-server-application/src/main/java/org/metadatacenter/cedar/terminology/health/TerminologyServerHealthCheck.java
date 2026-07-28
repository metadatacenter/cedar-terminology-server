package org.metadatacenter.cedar.terminology.health;

import com.codahale.metrics.health.HealthCheck;
import org.metadatacenter.cedar.cache.Cache;

/**
 * Readiness, not just liveness. The server used to report healthy unconditionally (a {@code 2 * 2 == 5}
 * placeholder), so a boot that could only fetch a partial ontology list — a handful of entries instead
 * of the full ~1300 — still passed its health check, and the degraded state reached the picker silently.
 *
 * <p>This probes the ontology list and reports unhealthy when it cannot be loaded or comes back short
 * (the floor lives in {@link Cache#MIN_EXPECTED_ONTOLOGIES}, which {@link Cache#getOntologies()}
 * enforces by throwing). Ops, monitoring and {@code cedar-services.sh} then see a warming-or-degraded
 * server as not-ready rather than green.
 *
 * <p>The list is fetched live (there is no in-memory cache), so the probe result is memoised for a short
 * window to keep health polling from re-crawling the catalogue on every call.
 */
public class TerminologyServerHealthCheck extends HealthCheck {

  private static final long PROBE_TTL_MS = 30_000;

  private static volatile long lastProbeMs = 0;
  private static volatile Result lastResult = Result.unhealthy("Ontology catalogue not probed yet");

  public TerminologyServerHealthCheck() {
  }

  @Override
  protected Result check() {
    long now = System.currentTimeMillis();
    if (now - lastProbeMs >= PROBE_TTL_MS) {
      // Claim the window before probing so concurrent health calls don't all crawl at once.
      lastProbeMs = now;
      try {
        int count = Cache.getOntologies().size();
        lastResult = Result.healthy("Ontology catalogue loaded: " + count + " ontologies");
      } catch (Exception e) {
        lastResult = Result.unhealthy("Ontology catalogue unavailable or partial: " + e.getMessage());
      }
    }
    return lastResult;
  }
}
