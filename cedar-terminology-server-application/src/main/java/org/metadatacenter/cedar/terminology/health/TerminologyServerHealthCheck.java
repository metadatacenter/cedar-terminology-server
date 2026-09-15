package org.metadatacenter.cedar.terminology.health;

import com.codahale.metrics.health.HealthCheck;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.terms.domainObjects.Ontology;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
 * <p>A full-by-count catalogue can still be degraded: if ingestion failed to record ontology titles,
 * every name collapses to its acronym, which clears the count floor but leaves the picker showing
 * "DOID (DOID)". So the probe also samples name quality and reports unhealthy when a full catalogue's
 * names are overwhelmingly bare acronyms. The name check is skipped below the count floor, where a
 * small (local-only) catalogue's names are not expected to be rich.
 *
 * <p>The list is fetched live (there is no in-memory cache), and outside a local-only deployment that
 * fetch reaches BioPortal for the whole registry. Measured on a warm server, one call per window, it
 * took 0.4s, 3.5s and 4.1s, because BioPortal's latency sets it. Running that inline made
 * {@code /healthcheck} itself take as long, which outran the five-second bound
 * {@code cedar-services.sh} probes with. {@code cedarcli native status} then showed a healthy
 * terminology server as {@code starting}, and {@code cedarcli native health}, the gate under
 * {@code test e2e}, {@code publish train} and {@code release}, failed on it.
 *
 * <p>So a measurement is taken off the calling thread, under a bound, following
 * {@code CedarDependencyHealthCheck} in {@code cedar-server-utils-dropwizard-library}: a call inside
 * {@link #DEFAULT_PROBE_TTL_MILLIS} of the last measurement serves it outright; a call past that
 * starts a refresh, or joins the one already running, and waits at most
 * {@link #DEFAULT_PROBE_TIMEOUT_MILLIS} before serving the measurement it already has with the
 * pending refresh named in the message. The endpoint therefore answers in milliseconds, or in two
 * seconds at worst, whatever BioPortal is doing. That class is the model rather than the
 * implementation for two reasons. Its probe reports only reachability, while this one carries a
 * count and a verdict on name quality. And a slow refresh here leaves an earlier measurement worth
 * serving, where a slow probe there leaves only a verdict to report.
 *
 * <p>A stale measurement cannot go green indefinitely. Joining the refresh rather than starting a
 * second one costs one thread while BioPortal is slow, and the client's own response timeout is 30
 * seconds, so a fetch that never answers becomes a failed one — and an unhealthy result — within it
 * rather than an unbounded healthy answer.
 */
public class TerminologyServerHealthCheck extends HealthCheck {

  /** How long one measurement is served before the next call refreshes it. */
  static final long DEFAULT_PROBE_TTL_MILLIS = 30_000;

  /**
   * How long a call waits for a refresh before serving the measurement it already has.
   *
   * <p>Two seconds, the bound every {@code CedarDependencyHealthCheck} probe carries, and for the
   * same reason: the budget is shared. Dropwizard runs a registry's checks one after another, and a
   * container health check gives the whole endpoint ten seconds, which this server's four checks
   * have to fit between them.
   */
  static final long DEFAULT_PROBE_TIMEOUT_MILLIS = 2_000;

  /**
   * Minimum share (percent) of a full catalogue's ontology names that must be human-readable
   * (contain whitespace) rather than bare acronyms. A healthy BioPortal-backed catalogue runs ~90%;
   * a title-less ingest is 0%. Set well below the healthy figure and far above any degraded one.
   */
  private static final int MIN_NAMED_PERCENT = 25;

  /**
   * The threads every refresh runs on.
   *
   * <p>Static rather than owned by the check, because nothing shuts a health check down: Dropwizard
   * does not manage their lifecycle, so a pool owned by a check outlives the application that
   * registered it, and a shared test JVM re-booting the application per test class would accumulate
   * one pool per boot. Cached rather than fixed, so a refresh that hangs holds one thread and idle
   * threads are reaped between windows.
   */
  private static final ExecutorService PROBERS = Executors.newCachedThreadPool(runnable -> {
    Thread thread = new Thread(runnable, "cedar-terminology-catalogue-probe");
    thread.setDaemon(true);
    return thread;
  });

  /** The ontology list one measurement reads, named so a test can supply its own. */
  @FunctionalInterface
  interface Catalogue {
    Map<String, Ontology> fetch() throws Exception;
  }

  private final Catalogue catalogue;
  private final long ttlMillis;
  private final long timeoutMillis;

  private volatile Result measured = Result.unhealthy("Ontology catalogue not probed yet");
  private volatile long measuredAt = 0;
  private Future<Result> inFlight;

  public TerminologyServerHealthCheck() {
    this(Cache::getOntologies, DEFAULT_PROBE_TTL_MILLIS, DEFAULT_PROBE_TIMEOUT_MILLIS);
  }

  TerminologyServerHealthCheck(Catalogue catalogue, long ttlMillis, long timeoutMillis) {
    this.catalogue = catalogue;
    this.ttlMillis = ttlMillis;
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  protected Result check() {
    long now = System.currentTimeMillis();
    if (measuredAt != 0 && now - measuredAt < ttlMillis) {
      return measured;
    }
    try {
      return refresh().get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException stillRunning) {
      return held(now);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return held(now);
    } catch (ExecutionException neverThrown) {
      // A refresh reports its own failure rather than throwing, so this covers only a task that
      // failed in a way it could not report at all.
      return Result.unhealthy("Ontology catalogue probe failed: " + neverThrown.getMessage());
    }
  }

  /**
   * The refresh to wait on: the one already running, or a new one when none is.
   *
   * <p>Joining rather than queueing is what keeps a slow BioPortal from costing one thread per poll,
   * and it also settles the stampede an expired window would otherwise cause, where every concurrent
   * call crawls the catalogue at once.
   */
  private synchronized Future<Result> refresh() {
    if (inFlight == null || inFlight.isDone()) {
      inFlight = PROBERS.submit(this::probe);
    }
    return inFlight;
  }

  /** Takes one measurement and records it. Reports a failed fetch rather than throwing. */
  private Result probe() {
    try {
      Map<String, Ontology> ontologies = catalogue.fetch();
      int total = ontologies.size();
      long named = ontologies.values().stream()
          .map(Ontology::getName)
          .filter(n -> n != null && n.chars().anyMatch(Character::isWhitespace))
          .count();
      if (total >= Cache.MIN_EXPECTED_ONTOLOGIES && named * 100L < (long) total * MIN_NAMED_PERCENT) {
        return record(Result.unhealthy("Ontology catalogue loaded " + total + " ontologies but only "
            + named + " have a human-readable name (the rest are bare acronyms); the ingest did not "
            + "record ontology titles, so the picker will show acronyms only"));
      }
      return record(Result.healthy("Ontology catalogue loaded: " + total + " ontologies"));
    } catch (Exception e) {
      return record(Result.unhealthy("Ontology catalogue unavailable or partial: " + e.getMessage()));
    }
  }

  private Result record(Result result) {
    measured = result;
    measuredAt = System.currentTimeMillis();
    return result;
  }

  /**
   * The measurement already taken, with the refresh that has not answered named beside it, so the
   * report distinguishes a figure from a moment ago from one just taken. Before the first
   * measurement lands there is nothing to serve, and the server is not ready, which is what the
   * initial result says.
   */
  private Result held(long now) {
    if (measuredAt == 0) {
      return measured;
    }
    String condition = measured.getMessage() + " (measured " + ((now - measuredAt) / 1000)
        + "s ago; the refresh under way has not answered within " + timeoutMillis + "ms)";
    return measured.isHealthy() ? Result.healthy(condition) : Result.unhealthy(condition);
  }
}
