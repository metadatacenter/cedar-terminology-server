package org.metadatacenter.cedar.cache;

import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyDetails;
import org.metadatacenter.terms.domainObjects.ValueSet;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;

/**
 * Live pass-through to the terminology service for ontology and value-set metadata.
 *
 * <p>This previously warmed and periodically refreshed an in-memory cache of every BioPortal
 * ontology and value set: on startup (or on first access) the server crawled ~1300 ontologies
 * ({@code loaded (n/1317)…}), which was slow and meant it could not start offline. Caching has been
 * removed — each accessor fetches on demand. The accessors still declare {@link ExecutionException}
 * so the existing call sites, which catch it, compile unchanged.
 */
public class Cache {

  /**
   * The floor below which an ontology list is treated as a failed load rather than a real one.
   * BioPortal serves ~1300 ontologies; a cold or rate-limited fetch has been seen to come back with a
   * tiny fraction (single digits) — the value-set collections only, with names collapsed to the bare
   * acronym. Serving that silently makes the picker show "DOID (DOID)" instead of "Human Disease
   * Ontology (DOID)". The floor is well below a full load and well above any such degraded partial, so
   * a short list becomes a loud failure here and an unhealthy signal in the health check, rather than
   * plausible-looking wrong data. If a deployment ever serves a genuinely small local-only catalog,
   * this is the number to revisit.
   */
  public static final int MIN_EXPECTED_ONTOLOGIES = 100;

  /** No-op: retained so the application startup call site is unchanged. Caching has been removed. */
  public static void init(boolean testMode) {
    // Intentionally empty — no warmup, no background refresh, no on-disk cache.
  }

  /**
   * Whether an ontology is flat (has no hierarchy). Fetched live; defaults to {@code false}
   * (hierarchical) when metadata is unavailable — e.g. an ontology served from a local snapshot,
   * which carries no BioPortal-style {@code isFlat} flag.
   */
  public static boolean isFlat(String ontology) throws IOException, ExecutionException {
    try {
      Ontology o = AbstractTerminologyServerResource.terminologyService.findOntology(ontology, false, BP_PUBLIC_API_KEY);
      return o != null && o.getIsFlat();
    } catch (RuntimeException metadataUnavailable) {
      return false;
    }
  }

  /**
   * Every ontology, keyed by id, fetched live from the terminology service. Uses
   * {@code includeDetails=false}: the list only needs id/name/isFlat (for the picker), so this is a
   * single call — the remote path no longer makes a per-ontology detail call for all ~1300
   * (the {@code loaded (n/1317)} crawl); the catalog-backed local path ignores the flag. Wraps any
   * {@link IOException} in {@link ExecutionException} to preserve the exception contract the call
   * sites expect (they used to read through a Guava cache, whose {@code get} threw ExecutionException).
   */
  public static LinkedHashMap<String, Ontology> getOntologies() throws ExecutionException {
    try {
      LinkedHashMap<String, Ontology> map = new LinkedHashMap<>();
      for (Ontology o : AbstractTerminologyServerResource.terminologyService.findAllOntologies(false, BP_PUBLIC_API_KEY)) {
        // Backstop for the picker, which drops any ontology whose details are null. The BioPortal list
        // path already sets details.hasSubmissions from summaryOnly upstream; this covers the
        // locally-served (catalog) path, whose ontologies are always browsable.
        if (o.getDetails() == null) {
          OntologyDetails details = new OntologyDetails();
          details.setHasSubmissions(true);
          o.setDetails(details);
        }
        map.put(o.getId(), o);
      }
      // Refuse to serve a partial list. A fetch that comes back far short of the full catalogue is a
      // failed load (cold BioPortal, a rate-limited key), not a real answer; surfacing it as an error
      // is better than handing the picker a plausible-looking but wrong list.
      if (map.size() < MIN_EXPECTED_ONTOLOGIES) {
        throw new ExecutionException(new IllegalStateException(
            "Ontology list came back with only " + map.size() + " entries (expected at least "
                + MIN_EXPECTED_ONTOLOGIES + "); treating it as a failed load rather than serving a partial list."));
      }
      return map;
    } catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  /** A single ontology by id, fetched live. Wraps {@link IOException} in {@link ExecutionException}. */
  public static Ontology getOntology(String id) throws ExecutionException {
    try {
      return AbstractTerminologyServerResource.terminologyService.findOntology(id, true, BP_PUBLIC_API_KEY);
    } catch (IOException e) {
      throw new ExecutionException(e);
    }
  }

  /** Every value set, keyed by id, fetched live. Wraps {@link IOException} in {@link ExecutionException}. */
  public static LinkedHashMap<String, ValueSet> getValueSets() throws ExecutionException {
    try {
      LinkedHashMap<String, ValueSet> map = new LinkedHashMap<>();
      for (ValueSet vs : AbstractTerminologyServerResource.terminologyService.findAllValueSets(BP_PUBLIC_API_KEY)) {
        map.put(vs.getId(), vs);
      }
      return map;
    } catch (IOException e) {
      throw new ExecutionException(e);
    }
  }
}
