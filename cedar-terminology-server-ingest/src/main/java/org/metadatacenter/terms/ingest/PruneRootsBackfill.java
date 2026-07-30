package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.SnapshotStore;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * One-off backfill: apply {@link SnapshotStore#pruneDeadEndImportRoots} to every latest snapshot in
 * an existing catalog, so the dead-end import roots are dropped without re-ingesting. The root table
 * is derived data; a snapshot's identity is the hash of its source file, so this leaves version ids
 * unchanged. New ingests already prune (see {@link IngestJob}); this fixes snapshots ingested before
 * the rule existed.
 *
 * Usage: {@code PruneRootsBackfill <catalogPath>}  (snapshot file paths are resolved relative to the
 * catalog's directory, as the catalog stores them).
 */
public final class PruneRootsBackfill {

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: PruneRootsBackfill <catalogPath> [acronym...]");
      System.err.println("  With no acronyms: prune every latest snapshot (re-materialize only if emptied).");
      System.err.println("  With acronyms:    force re-materialize + re-prune only those (to correct a");
      System.err.println("                    prior run whose own-namespace detection was wrong).");
      System.exit(2);
    }
    Path catalogPath = Path.of(args[0]);
    Path baseDir = catalogPath.toAbsolutePath().getParent();
    java.util.Set<String> only = new java.util.HashSet<>(java.util.Arrays.asList(args).subList(1, args.length));

    int ontologies = 0;
    long totalPruned = 0;
    long totalLabeled = 0;
    try (Connection cat = DriverManager.getConnection("jdbc:sqlite:" + catalogPath);
         Statement s = cat.createStatement();
         ResultSet rs = s.executeQuery(
             "SELECT s.acronym, s.file_path FROM version_tag t "
                 + "JOIN snapshot s ON s.version_id = t.version_id AND s.acronym = t.acronym "
                 + "WHERE t.tag = 'latest' ORDER BY s.acronym")) {
      while (rs.next()) {
        String acronym = rs.getString(1);
        if (!only.isEmpty() && !only.contains(acronym)) {
          continue;
        }
        Path file = baseDir.resolve(rs.getString(2));
        try (SnapshotStore store = SnapshotStore.openFile(file.toString())) {
          // Re-materialize to restore the full root set before pruning when (a) a prior unguarded run
          // emptied this snapshot, or (b) this is a targeted re-prune (explicit acronym list) fixing a
          // prior run's wrong own-namespace — otherwise roots already deleted can't be re-evaluated.
          if (store.rootCount() == 0 || !only.isEmpty()) {
            store.materialize();
          }
          int pruned = store.pruneDeadEndImportRoots(acronym);
          int labeled = store.fillMissingLabelsFromIri();
          ontologies++;
          totalPruned += pruned;
          totalLabeled += labeled;
          if (pruned > 0 || labeled > 0 || !only.isEmpty()) {
            System.out.printf("%-24s roots %d (pruned %d), labeled %d from IRI%n",
                acronym, store.rootCount(), pruned, labeled);
          }
        } catch (Exception e) {
          System.out.printf("%-24s SKIPPED: %s%n", acronym, e.getMessage());
        }
      }
    }
    System.out.printf("%nbackfill done: %d ontologies, %d roots pruned, %d concepts labeled from IRI%n",
        ontologies, totalPruned, totalLabeled);
  }

  private PruneRootsBackfill() {}
}
