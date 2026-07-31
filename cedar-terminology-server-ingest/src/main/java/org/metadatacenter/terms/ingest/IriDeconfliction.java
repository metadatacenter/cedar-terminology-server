package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.OntologyIri;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Enforces the identity invariant on the re-keyed catalog: <b>a canonical iri identifies at most one
 * content-distinct ontology</b>. The re-key derives each ontology's canonical iri from its own term
 * namespace, but that namespace is sometimes not the ontology's own — a placeholder/host base every
 * webprotege ontology shares, a Protégé {@code ont.owl} default, or an OBO namespace an ontology
 * merely <i>imports</i> (GRO-CPGA's terms are mostly {@code obo/PO_}). Those collapse unrelated
 * ontologies onto one iri: a false merge.
 *
 * De-confliction resolves each shared iri:
 * <ul>
 *   <li><b>True duplicate</b> — all sharers have identical content (same latest {@code version_id},
 *       e.g. INCENTIVE / INCENTIVE-VARS on one SKOS file). Genuinely one ontology under two acronyms;
 *       the shared identity is correct and kept.</li>
 *   <li><b>Conflict</b> — content-distinct sharers. Kept only for the iri's <b>OBO owner</b> (the
 *       acronym that <i>is</i> the {@code obo/<id>}, via {@link OntologyIri#isOboOwner}); every other
 *       sharer is declined to acronym-only. A non-OBO base has no derivable owner, so a placeholder
 *       base shared by unrelated ontologies is declined for all — none falsely owns it.</li>
 * </ul>
 *
 * Conservative by design: it never merges, and when ownership is not unambiguous it declines rather
 * than guess. A major non-OBO ontology whose real IRI is import-leaked (NCIT on {@code Thesaurus.owl})
 * becomes acronym-only here; restoring its identity cleanly is the {@code owl:Ontology}-header-IRI
 * follow-up, not a heuristic size contest.
 */
public final class IriDeconfliction {

  /** One shared iri and how it was resolved. */
  public record Group(String iri, List<String> acronyms, boolean duplicate,
                      Optional<String> owner, List<String> declined) {}

  /** Outcome of a full pass. */
  public record Result(int sharedIris, int duplicates, int conflictsWithOwner, int conflictsNoOwner,
                       int acronymsDeclined, int orphanIdentitiesPruned, List<Group> groups) {}

  /**
   * The acronyms to decline for one shared iri, given each sharer's latest content id (null when it
   * has no current snapshot). Empty when the group is a true duplicate — all keep. Pure and
   * order-stable, so the policy is unit-testable without a catalog.
   */
  static List<String> toDecline(String iri, Map<String, String> latestContentByAcronym) {
    Set<String> contents = latestContentByAcronym.entrySet().stream()
        // a missing content counts as distinct-per-acronym, so it never masquerades as a duplicate
        .map(e -> e.getValue() != null ? "v:" + e.getValue() : "none:" + e.getKey())
        .collect(Collectors.toSet());
    if (contents.size() <= 1) {
      return List.of(); // true duplicate — one ontology under several acronyms
    }
    List<String> owners = latestContentByAcronym.keySet().stream()
        .filter(a -> OntologyIri.isOboOwner(a, iri))
        .sorted().collect(Collectors.toList());
    String keep = owners.size() == 1 ? owners.get(0) : null; // ambiguous/absent owner ⇒ decline all
    return latestContentByAcronym.keySet().stream()
        .filter(a -> !a.equals(keep))
        .sorted().collect(Collectors.toList());
  }

  /** The OBO owner among the sharers of an iri, when exactly one — else empty. */
  private static Optional<String> owner(String iri, Set<String> acronyms) {
    List<String> owners = acronyms.stream().filter(a -> OntologyIri.isOboOwner(a, iri)).toList();
    return owners.size() == 1 ? Optional.of(owners.get(0)) : Optional.empty();
  }

  /**
   * Re-evaluates one shared iri and clears it from every sharer that does not own it. Returns the
   * acronyms declined (empty for a true duplicate or a sole holder). Idempotent. Called both by the
   * full pass and, at ingest, for the one iri a fresh ingest just claimed.
   */
  public static List<String> reconcile(CatalogStore catalog, String iri) throws SQLException {
    List<String> acronyms = catalog.acronymsForIri(iri);
    if (acronyms.size() <= 1) {
      return List.of();
    }
    Map<String, String> latest = new LinkedHashMap<>();
    for (String a : acronyms) {
      latest.put(a, catalog.resolveLatest(a).map(CatalogStore.SnapshotInfo::versionId).orElse(null));
    }
    List<String> decline = toDecline(iri, latest);
    for (String a : decline) {
      catalog.clearOntologyIri(a);
    }
    return decline;
  }

  /** Resolves every shared iri in the catalog. With {@code apply} false it classifies without
   *  mutating (a report); with it true it clears non-owners and prunes orphaned identity rows. */
  public static Result run(CatalogStore catalog, boolean apply) throws SQLException {
    List<Group> groups = new ArrayList<>();
    int duplicates = 0, withOwner = 0, noOwner = 0, declinedTotal = 0;
    for (String iri : catalog.sharedIris()) {
      List<String> acronyms = catalog.acronymsForIri(iri);
      Map<String, String> latest = new LinkedHashMap<>();
      for (String a : acronyms) {
        latest.put(a, catalog.resolveLatest(a).map(CatalogStore.SnapshotInfo::versionId).orElse(null));
      }
      List<String> decline = toDecline(iri, latest);
      Optional<String> owner = decline.isEmpty() ? Optional.empty() : owner(iri, new TreeSet<>(acronyms));
      boolean duplicate = decline.isEmpty();
      if (duplicate) {
        duplicates++;
      } else if (owner.isPresent()) {
        withOwner++;
      } else {
        noOwner++;
      }
      declinedTotal += decline.size();
      groups.add(new Group(iri, acronyms, duplicate, owner, decline));
      if (apply) {
        for (String a : decline) {
          catalog.clearOntologyIri(a);
        }
      }
    }
    int pruned = apply ? catalog.pruneOrphanIdentities() : 0;
    return new Result(groups.size(), duplicates, withOwner, noOwner, declinedTotal, pruned, groups);
  }

  /**
   * Usage: {@code IriDeconfliction <catalogPath> [--apply]}. Default is a dry-run report; {@code
   * --apply} clears non-owner iris and prunes orphaned identity rows.
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: IriDeconfliction <catalogPath> [--apply]");
      System.exit(2);
    }
    boolean apply = args.length > 1 && "--apply".equals(args[1]);
    try (CatalogStore catalog = CatalogStore.openFile(Path.of(args[0]).toString())) {
      catalog.initSchema();
      Result r = run(catalog, apply);
      for (Group g : r.groups()) {
        if (g.duplicate()) {
          System.out.printf("DUPLICATE  %-60s  %s%n", g.iri(), g.acronyms());
        } else {
          System.out.printf("%-9s %-60s owner=%s  decline=%s%n",
              g.owner().isPresent() ? "CONFLICT+" : "CONFLICT-", g.iri(),
              g.owner().orElse("(none)"), g.declined());
        }
      }
      System.out.printf("%n%s: %d shared iris — %d duplicates, %d conflicts with an owner, "
              + "%d conflicts with none; %d acronyms declined, %d orphan identities pruned%n",
          apply ? "APPLIED" : "DRY-RUN", r.sharedIris(), r.duplicates(), r.conflictsWithOwner(),
          r.conflictsNoOwner(), r.acronymsDeclined(), r.orphanIdentitiesPruned());
    }
  }

  private IriDeconfliction() {}
}
