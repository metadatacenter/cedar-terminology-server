package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SearchIndexStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the cross-snapshot search index: the one file a corpus-wide query reads instead of opening
 * every snapshot in the catalog.
 *
 * A batch job, not something the server does at startup. It reads the current snapshot of each
 * served ontology and writes its terms and names into the index, skipping any ontology already
 * indexed at that same version — so a rebuild after a handful of re-ingests costs a handful of
 * ontologies rather than the corpus.
 *
 * <pre>
 *   SearchIndexJob &lt;catalogPath&gt; &lt;indexPath&gt; [--acronyms A,B] [--max N]
 *                  [--skip-larger-than N] [--force]
 * </pre>
 *
 * {@code --skip-larger-than} leaves the giants out by class count. An ontology is loaded into memory
 * one at a time, and NCBITaxon alone is 2.85 million concepts, so a first index over everything else
 * can be built in a normal heap while the giants are done separately with a larger one. What was
 * skipped is printed rather than passed over quietly: an index that silently lacks an ontology
 * answers "no matches" for it, which is the failure this whole endpoint is built to avoid.
 */
public class SearchIndexJob {

  /** What one ontology contributed, or why it did not. */
  public record Outcome(String acronym, String versionId, int terms, long names, String skipped) {
    public boolean indexed() {
      return skipped == null;
    }
  }

  private final CatalogStore catalog;
  private final SearchIndexStore index;

  public SearchIndexJob(CatalogStore catalog, SearchIndexStore index) {
    this.catalog = catalog;
    this.index = index;
  }

  /**
   * Indexes one ontology's current snapshot.
   *
   * @param force index even when the version held is already the current one
   */
  public Outcome indexOntology(String acronym, boolean force, int skipLargerThan) throws SQLException {
    Optional<CatalogStore.SnapshotInfo> latest = catalog.resolveLatest(acronym);
    if (latest.isEmpty()) {
      return new Outcome(acronym, null, 0, 0, "no current snapshot");
    }
    CatalogStore.SnapshotInfo snapshot = latest.get();
    if (!force && snapshot.versionId().equals(index.indexedVersion(acronym).orElse(null))) {
      return new Outcome(acronym, snapshot.versionId(), 0, 0, "already current");
    }
    if (skipLargerThan > 0 && snapshot.classCount() != null && snapshot.classCount() > skipLargerThan) {
      return new Outcome(acronym, snapshot.versionId(), 0, 0,
          "larger than " + skipLargerThan + " classes (" + snapshot.classCount() + ")");
    }
    Path file = resolve(snapshot.filePath());
    if (!Files.exists(file)) {
      return new Outcome(acronym, snapshot.versionId(), 0, 0, "snapshot file missing: " + file);
    }

    List<SearchIndexStore.IndexedTerm> terms = new ArrayList<>();
    Map<String, List<SearchIndexStore.IndexedName>> names = new HashMap<>();
    long nameCount = 0;
    try (SnapshotStore store = SnapshotStore.openFile(file.toString())) {
      Map<String, Integer> descendants = store.descendantCounts();
      Map<String, String> replacements = new HashMap<>();
      for (SnapshotStore.ConceptMeta meta : store.allConceptMeta()) {
        if (meta.replacedBy() != null) {
          replacements.put(meta.iri(), meta.replacedBy());
        }
      }
      for (SnapshotStore.Concept concept : store.allConceptsDetailed()) {
        terms.add(new SearchIndexStore.IndexedTerm(acronym, concept.iri(), concept.prefLabel(),
            concept.obsolete(), replacements.get(concept.iri()), concept.hasChildren(),
            descendants.getOrDefault(concept.iri(), 0)));
      }
      for (SnapshotStore.LabelRow row : store.allLabels()) {
        // The preferred label is added from the term itself, so skipping it here keeps one name from
        // being indexed twice and reported as two matches.
        if (row.value() == null || row.value().equals(prefLabelOf(terms, row.conceptIri()))) {
          continue;
        }
        names.computeIfAbsent(row.conceptIri(), k -> new ArrayList<>())
            .add(new SearchIndexStore.IndexedName(row.property(), row.lang(), row.value()));
        nameCount++;
      }
    }
    index.replaceOntology(acronym, snapshot.versionId(), Instant.now().toString(), terms, names);
    return new Outcome(acronym, snapshot.versionId(), terms.size(), nameCount, null);
  }

  private static String prefLabelOf(List<SearchIndexStore.IndexedTerm> terms, String iri) {
    // Linear only in the pathological case; terms arrive ordered by IRI and the caller looks up the
    // concept it just read. A map would double the peak memory of a giant for a duplicate check.
    for (int i = terms.size() - 1; i >= 0 && i > terms.size() - 64; i--) {
      if (terms.get(i).iri().equals(iri)) {
        return terms.get(i).prefLabel();
      }
    }
    return null;
  }

  private static Path resolve(String filePath) {
    Path path = Paths.get(filePath);
    return path.isAbsolute() ? path : Paths.get(System.getProperty("user.dir")).resolve(path);
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: SearchIndexJob <catalogPath> <indexPath> [--acronyms A,B] [--max N] "
          + "[--skip-larger-than N] [--force]");
      System.exit(2);
    }
    String catalogPath = args[0];
    String indexPath = args[1];
    Set<String> only = new LinkedHashSet<>();
    int max = 0;
    int skipLargerThan = 0;
    boolean force = false;
    for (int i = 2; i < args.length; i++) {
      switch (args[i]) {
        case "--acronyms" -> {
          for (String a : args[++i].split(",")) {
            if (!a.isBlank()) {
              only.add(a.trim());
            }
          }
        }
        case "--max" -> max = Integer.parseInt(args[++i]);
        case "--skip-larger-than" -> skipLargerThan = Integer.parseInt(args[++i]);
        case "--force" -> force = true;
        default -> {
          System.err.println("Unknown argument: " + args[i]);
          System.exit(2);
        }
      }
    }

    if (!SearchIndexStore.supportsFts5()) {
      System.err.println("This SQLite build has no FTS5; the index cannot be created.");
      System.exit(3);
    }

    long started = System.currentTimeMillis();
    try (CatalogStore catalog = CatalogStore.openFile(catalogPath);
         SearchIndexStore index = SearchIndexStore.openFile(indexPath)) {
      catalog.initSchema();
      index.initSchema();
      index.applyBulkLoadPragmas();
      SearchIndexJob job = new SearchIndexJob(catalog, index);

      List<String> acronyms = only.isEmpty()
          ? catalog.listOntologies().stream().map(CatalogStore.OntologyInfo::acronym)
              .sorted(Comparator.naturalOrder()).toList()
          : List.copyOf(only);

      int indexed = 0;
      long terms = 0;
      long names = 0;
      List<Outcome> skipped = new ArrayList<>();
      for (String acronym : acronyms) {
        if (max > 0 && indexed >= max) {
          break;
        }
        Outcome outcome;
        try {
          outcome = job.indexOntology(acronym, force, skipLargerThan);
        } catch (Exception e) {
          outcome = new Outcome(acronym, null, 0, 0, "failed: " + e);
        }
        if (outcome.indexed()) {
          indexed++;
          terms += outcome.terms();
          names += outcome.names();
          System.out.printf("indexed %-22s %,10d terms %,10d names%n", acronym, outcome.terms(), outcome.names());
        } else if (!"already current".equals(outcome.skipped())) {
          skipped.add(outcome);
        }
      }

      System.out.println("building the full-text index…");
      index.rebuildFullText();
      index.optimize();

      System.out.printf("%nindexed %d ontologies, %,d terms, %,d names in %ds%n",
          indexed, terms, names, (System.currentTimeMillis() - started) / 1000);
      System.out.printf("index now holds %d ontologies and %,d terms%n",
          index.indexedOntologyCount(), index.termCount());
      if (!skipped.isEmpty()) {
        System.out.println("\nnot indexed — a query will report no matches for these:");
        skipped.forEach(o -> System.out.printf("  %-22s %s%n", o.acronym(), o.skipped()));
      }
    }
  }
}
