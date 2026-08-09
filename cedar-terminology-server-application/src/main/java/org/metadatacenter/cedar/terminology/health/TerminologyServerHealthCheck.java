package org.metadatacenter.cedar.terminology.health;

import com.codahale.metrics.health.HealthCheck;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.terms.domainObjects.Ontology;

/**
 * Readiness, not just liveness. The server used to report healthy unconditionally (a {@code 2 * 2 == 5}
 * placeholder), so a boot that could only fetch a partial ontology list — a handful of entries instead
 * of the full ~1300 — still passed its health check, and the degraded state reached the picker silently.
 *
 * <p>This probes the ontology list and reports unhealthy when it cannot be loaded or comes back short
 * (the floor lives in {@link Cache#MIN_EXPECTED_ONTOLOGIES}, which {@link Cache#getOntologies()}
 * enforces by throwing). Ops, monitoring and {@code cedar-services.sh} then see a warming-or-degraded
 * server as not-ready rather than green. A local-only deployment is exempt from the floor (its
 * catalogue is authoritatively small), so it reports healthy on its true ontology count.
 *
 * <p>The list is fetched live (there is no in-memory cache), so the probe result is memoised for a short
 * window to keep health polling from re-crawling the catalogue on every call.
 *
 * <p>A full-by-count catalogue can still be degraded: if ingestion failed to record ontology titles,
 * every name collapses to its acronym, which clears the count floor but leaves the picker showing
 * "DOID (DOID)". So the probe also samples name quality and reports unhealthy when a full catalogue's
 * names are overwhelmingly bare acronyms. The name check is skipped below the count floor, where a
 * small (local-only) catalogue's names are not expected to be rich.
 */
public class TerminologyServerHealthCheck extends HealthCheck {

  private static final long PROBE_TTL_MS = 30_000;

  /**
   * Minimum share (percent) of a full catalogue's ontology names that must be human-readable
   * (contain whitespace) rather than bare acronyms. A healthy BioPortal-backed catalogue runs ~90%;
   * a title-less ingest is 0%. Set well below the healthy figure and far above any degraded one.
   */
  private static final int MIN_NAMED_PERCENT = 25;

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
        var ontologies = Cache.getOntologies();
        int total = ontologies.size();
        long named = ontologies.values().stream()
            .map(Ontology::getName)
            .filter(n -> n != null && n.chars().anyMatch(Character::isWhitespace))
            .count();
        if (total >= Cache.MIN_EXPECTED_ONTOLOGIES && named * 100L < (long) total * MIN_NAMED_PERCENT) {
          lastResult = Result.unhealthy("Ontology catalogue loaded " + total + " ontologies but only "
              + named + " have a human-readable name (the rest are bare acronyms); the ingest did not "
              + "record ontology titles, so the picker will show acronyms only");
        } else {
          lastResult = Result.healthy("Ontology catalogue loaded: " + total + " ontologies");
        }
      } catch (Exception e) {
        lastResult = Result.unhealthy("Ontology catalogue unavailable or partial: " + e.getMessage());
      }
    }
    return lastResult;
  }
}
