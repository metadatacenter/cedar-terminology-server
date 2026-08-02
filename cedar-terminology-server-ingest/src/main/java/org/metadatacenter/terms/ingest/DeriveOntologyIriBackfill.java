package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.OntologyIri;
import org.metadatacenter.terms.store.SnapshotStore;

import java.nio.file.Path;
import java.util.Optional;

/**
 * One-off backfill: derive and store each ontology's canonical {@code iri} (its cross-source
 * identity, VERSIONING-DESIGN §6.4) from concepts already on disk — no re-ingest, no re-download.
 *
 * For every ontology in the catalog, opens its {@code latest} snapshot, takes the acronym-keyed
 * dominant own ID-space ({@link SnapshotStore#dominantOwnIdspace} — the roots-prune logic, which
 * correctly ignores bulk-imported namespaces), and normalizes it to the canonical form
 * ({@link OntologyIri#canonical}). The canonical IRI is stored as identity and the raw namespace it
 * was folded from is kept as provenance. Snapshots with no concepts (the empty LC-CARRIERS) are left
 * without an iri and reported.
 *
 * Usage: {@code DeriveOntologyIriBackfill <catalogPath> [acronym...]}. With acronyms, derives only
 * those; otherwise every ontology in the catalog.
 */
public final class DeriveOntologyIriBackfill {

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: DeriveOntologyIriBackfill <catalogPath> [acronym...]");
      System.exit(2);
    }
    Path catalogPath = Path.of(args[0]);
    java.util.Set<String> only = new java.util.HashSet<>(java.util.Arrays.asList(args).subList(1, args.length));

    int derived = 0;
    int empty = 0;
    int skipped = 0;
    try (CatalogStore catalog = CatalogStore.openFile(catalogPath.toString())) {
      catalog.initSchema(); // ensures the iri / raw_namespace columns exist on an older catalog
      for (CatalogStore.OntologyInfo o : catalog.listOntologies()) {
        String acronym = o.acronym();
        if (!only.isEmpty() && !only.contains(acronym)) {
          continue;
        }
        Optional<CatalogStore.SnapshotInfo> latest = catalog.resolveLatest(acronym);
        if (latest.isEmpty()) {
          skipped++;
          System.out.printf("%-24s SKIPPED: no latest snapshot%n", acronym);
          continue;
        }
        try (SnapshotStore store = SnapshotStore.openFile(latest.get().filePath())) {
          Optional<String> namespace = store.dominantOwnIdspace(acronym);
          if (namespace.isEmpty()) {
            empty++;
            System.out.printf("%-24s EMPTY: no concepts to derive from%n", acronym);
            continue;
          }
          String canonical = OntologyIri.canonical(namespace.get());
          catalog.setOntologyIri(acronym, canonical, namespace.get());
          derived++;
          System.out.printf("%-24s %s  (raw %s)%n", acronym, canonical, namespace.get());
        } catch (Exception e) {
          skipped++;
          System.out.printf("%-24s SKIPPED: %s%n", acronym, e.getMessage());
        }
      }
      // Enforce the identity invariant across the whole corpus: a canonical iri shared by
      // content-distinct ontologies (a placeholder/host base, or a namespace an ontology only imports)
      // is a false merge — keep it for its OBO owner, decline it for the rest.
      IriDeconfliction.Result d = IriDeconfliction.run(catalog, true);
      System.out.printf("de-confliction: %d shared iris — %d duplicates kept, %d owner-resolved, "
              + "%d ownerless; %d acronyms declined, %d orphan identities pruned%n",
          d.sharedIris(), d.duplicates(), d.conflictsWithOwner(), d.conflictsNoOwner(),
          d.acronymsDeclined(), d.orphanIdentitiesPruned());

      // Header-IRI fallback (item 6): anything the class namespace left acronym-only (a file/host base,
      // or a namespace it only imports) gets its identity from the declared owl:Ontology IRI recorded in
      // the snapshot meta at ingest — no re-download. Settle collisions with a second de-confliction pass.
      int headerRestored = 0;
      for (CatalogStore.OntologyInfo o : catalog.listOntologies()) {
        if (!only.isEmpty() && !only.contains(o.acronym())) {
          continue;
        }
        if (catalog.ontologyIri(o.acronym()).isPresent()) {
          continue;
        }
        Optional<CatalogStore.SnapshotInfo> latest = catalog.resolveLatest(o.acronym());
        if (latest.isEmpty()) {
          continue;
        }
        try (SnapshotStore store = SnapshotStore.openFile(latest.get().filePath())) {
          store.initSchema();
          Optional<String> header = store.getMeta("ontology_iri");
          if (header.isPresent()) {
            catalog.setOntologyIri(o.acronym(), OntologyIri.canonical(header.get()), header.get());
            headerRestored++;
          }
        }
      }
      if (headerRestored > 0) {
        IriDeconfliction.run(catalog, true);
      }
      System.out.printf("header-iri fallback: %d identities restored from snapshot meta%n", headerRestored);
    }
    System.out.printf("%nbackfill done: %d derived, %d empty, %d skipped%n", derived, empty, skipped);
  }

  private DeriveOntologyIriBackfill() {}
}
