package org.metadatacenter.cedar.cache;

import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.terms.ITerminologyService;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyDetails;
import org.metadatacenter.terms.domainObjects.ValueSet;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
   * plausible-looking wrong data. A genuinely small local-only deployment is exempt: {@link
   * #getOntologies()} skips this floor when the backend {@link ITerminologyService#isLocalOnly() is
   * local-only}, because then a short list is the authoritative catalogue, not a degraded fetch.
   */
  public static final int MIN_EXPECTED_ONTOLOGIES = 100;

  /**
   * How long an ontology's flatness is held before it is fetched again.
   *
   * <p>Whether an ontology has a hierarchy is a property of its structure, so it changes only when
   * someone publishes a new submission of it, never within an authoring session. An hour matches
   * {@link #VALUE_SET_IDS_TTL_MS} and keeps the two things this class holds on one clock.
   */
  private static final long IS_FLAT_TTL_MS = 60 * 60 * 1000L;

  /** An ontology's flatness and when it was fetched. */
  private record Flatness(boolean isFlat, long fetchedAt) {
  }

  /**
   * Flatness by ontology acronym. Bounded by the size of the registry, around 1300 entries of two
   * fields each, and only for ontologies someone has actually browsed.
   */
  private static final ConcurrentHashMap<String, Flatness> flatness = new ConcurrentHashMap<>();

  /**
   * Whether an ontology is flat (has no hierarchy). Defaults to {@code false} (hierarchical) when
   * metadata is unavailable — e.g. an ontology served from a local snapshot, which carries no
   * BioPortal-style {@code isFlat} flag.
   *
   * <p>Held for {@link #IS_FLAT_TTL_MS} between fetches. The two callers are the hierarchy-browsing
   * endpoints, class tree and root classes, so without this every expansion in the picker spent a
   * whole round trip on one boolean before it could ask for the classes the reader wanted. For an
   * ontology the local store serves, that round trip is a local query; for every ontology still
   * served by BioPortal, it is a remote one.
   *
   * <p>Only a clean answer is held. The fallback covers a backend that could not answer at all, and
   * pinning an hour of {@code false} to a moment when BioPortal was unreachable would turn a
   * transient failure into a lasting wrong answer about the ontology's shape.
   */
  public static boolean isFlat(String ontology) throws IOException, ExecutionException {
    Flatness held = flatness.get(ontology);
    if (held != null && System.currentTimeMillis() - held.fetchedAt() < IS_FLAT_TTL_MS) {
      return held.isFlat();
    }
    try {
      Ontology o = AbstractTerminologyServerResource.terminologyService.findOntology(ontology, false, BP_PUBLIC_API_KEY);
      if (o == null) {
        return false;
      }
      flatness.put(ontology, new Flatness(o.getIsFlat(), System.currentTimeMillis()));
      return o.getIsFlat();
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
      // is better than handing the picker a plausible-looking but wrong list. This guards the remote
      // fetch only: a local-only backend has no BioPortal call to degrade, and its list is
      // authoritatively small (just the versioned ontologies), so the floor must not apply there.
      if (!AbstractTerminologyServerResource.terminologyService.isLocalOnly()
          && map.size() < MIN_EXPECTED_ONTOLOGIES) {
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

  /**
   * How long a fetched set of value-set identifiers is reused before it is fetched again.
   *
   * <p>Value-set collections change on the timescale at which someone publishes a value set, which
   * is far longer than an authoring session. An hour keeps the identifiers current enough to
   * classify a search result correctly while charging the fetch to roughly one search an hour
   * instead of every search.
   */
  private static final long VALUE_SET_IDS_TTL_MS = 60 * 60 * 1000L;

  private static volatile Set<String> valueSetIds;
  private static volatile long valueSetIdsFetchedAt;

  /**
   * The identifiers of every value set, held for {@link #VALUE_SET_IDS_TTL_MS} between fetches.
   *
   * <p>This is not the startup crawl the class documentation describes removing. Nothing warms it,
   * nothing refreshes it on a timer, and a server that never needs it never makes the call: it is
   * populated by the first search whose results have to be told apart, and only that.
   *
   * <p>Two callers racing a cold or expired entry both fetch and both publish. The answers agree,
   * so the cost is one redundant fetch rather than a wrong one, which is cheaper than holding a
   * lock across a network call.
   */
  public static Set<String> getValueSetIds() throws IOException {
    Set<String> held = valueSetIds;
    if (held != null && System.currentTimeMillis() - valueSetIdsFetchedAt < VALUE_SET_IDS_TTL_MS) {
      return held;
    }
    Set<String> fetched = new HashSet<>();
    for (ValueSet vs : AbstractTerminologyServerResource.terminologyService.findAllValueSets(BP_PUBLIC_API_KEY)) {
      fetched.add(vs.getId());
    }
    Set<String> immutable = Set.copyOf(fetched);
    valueSetIds = immutable;
    valueSetIdsFetchedAt = System.currentTimeMillis();
    return immutable;
  }
}
