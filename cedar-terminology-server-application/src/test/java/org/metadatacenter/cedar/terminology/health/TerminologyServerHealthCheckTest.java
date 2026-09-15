package org.metadatacenter.cedar.terminology.health;

import com.codahale.metrics.health.HealthCheck;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.domainObjects.Ontology;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminologyServerHealthCheckTest {

  private static final long FOREVER = 60_000;
  private static final long EVERY_CALL = 0;
  private static final long SHORT_BOUND = 100;

  private static Map<String, Ontology> catalogueOf(int count, boolean named) {
    Map<String, Ontology> ontologies = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) {
      String acronym = "ONT" + i;
      ontologies.put(acronym, new Ontology(acronym, "https://example.org/ontologies/" + acronym,
          named ? "Ontology Number " + i : acronym, false, null));
    }
    return ontologies;
  }

  @Test
  void aFullCatalogueIsHealthyAndReportsItsSize() {
    HealthCheck.Result result = new TerminologyServerHealthCheck(
        () -> catalogueOf(200, true), FOREVER, FOREVER).check();

    assertTrue(result.isHealthy());
    assertTrue(result.getMessage().contains("200 ontologies"), result.getMessage());
  }

  /** A catalogue whose names all collapsed to their acronym clears the count floor and is degraded. */
  @Test
  void aCatalogueOfBareAcronymsIsUnhealthy() {
    HealthCheck.Result result = new TerminologyServerHealthCheck(
        () -> catalogueOf(200, false), FOREVER, FOREVER).check();

    assertFalse(result.isHealthy());
    assertTrue(result.getMessage().contains("bare acronyms"), result.getMessage());
  }

  @Test
  void aFetchThatFailsIsUnhealthyAndNamesTheCause() {
    HealthCheck.Result result = new TerminologyServerHealthCheck(() -> {
      throw new IllegalStateException("BioPortal refused the connection");
    }, FOREVER, FOREVER).check();

    assertFalse(result.isHealthy());
    assertTrue(result.getMessage().contains("BioPortal refused the connection"), result.getMessage());
  }

  @Test
  void aMeasurementInsideItsWindowIsServedWithoutFetchingAgain() {
    AtomicInteger fetches = new AtomicInteger();
    TerminologyServerHealthCheck check = new TerminologyServerHealthCheck(() -> {
      fetches.incrementAndGet();
      return catalogueOf(200, true);
    }, FOREVER, FOREVER);

    assertTrue(check.check().isHealthy());
    assertTrue(check.check().isHealthy());
    assertEquals(1, fetches.get());
  }

  /**
   * The reason the fetch is bounded: BioPortal's own latency set it, and running it inline made
   * {@code /healthcheck} take as long, which outran the probe bound in {@code cedar-services.sh} and
   * showed a healthy server as {@code starting}.
   */
  @Test
  void aSlowFetchDoesNotHoldTheEndpointOpen() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    TerminologyServerHealthCheck check = new TerminologyServerHealthCheck(() -> {
      release.await(1, TimeUnit.MINUTES);
      return catalogueOf(200, true);
    }, FOREVER, SHORT_BOUND);

    long startedAt = System.nanoTime();
    HealthCheck.Result result = check.check();
    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

    release.countDown();
    assertFalse(result.isHealthy(), "nothing has been measured yet, so the server is not ready");
    assertTrue(elapsedMillis < 30_000, "the check waited " + elapsedMillis + "ms on a slow fetch");
  }

  /** Once a measurement exists, a refresh that has not answered does not withdraw it. */
  @Test
  void aSlowRefreshServesTheMeasurementAlreadyTaken() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    AtomicReference<TerminologyServerHealthCheck.Catalogue> catalogue =
        new AtomicReference<>(() -> catalogueOf(200, true));
    TerminologyServerHealthCheck check =
        new TerminologyServerHealthCheck(() -> catalogue.get().fetch(), EVERY_CALL, SHORT_BOUND);

    assertTrue(check.check().isHealthy());
    catalogue.set(() -> {
      release.await(1, TimeUnit.MINUTES);
      return catalogueOf(200, true);
    });
    HealthCheck.Result result = check.check();

    release.countDown();
    assertTrue(result.isHealthy());
    assertTrue(result.getMessage().contains("200 ontologies"), result.getMessage());
    assertTrue(result.getMessage().contains("has not answered within"), result.getMessage());
  }

  /** A second call joins the refresh already running rather than starting another crawl behind it. */
  @Test
  void aSlowRefreshCostsOneFetchRatherThanOnePerPoll() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(1);
    AtomicInteger fetches = new AtomicInteger();
    TerminologyServerHealthCheck check = new TerminologyServerHealthCheck(() -> {
      fetches.incrementAndGet();
      started.countDown();
      release.await(1, TimeUnit.MINUTES);
      return catalogueOf(200, true);
    }, EVERY_CALL, SHORT_BOUND);

    check.check();
    assertTrue(started.await(5, TimeUnit.SECONDS));
    check.check();

    release.countDown();
    assertEquals(1, fetches.get());
  }
}
