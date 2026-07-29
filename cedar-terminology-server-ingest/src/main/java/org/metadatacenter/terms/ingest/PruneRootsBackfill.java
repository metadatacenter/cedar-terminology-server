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
      System.err.println("Usage: PruneRootsBackfill <catalogPath>");
      System.exit(2);
    }
    Path catalogPath = Path.of(args[0]);
    Path baseDir = catalogPath.toAbsolutePath().getParent();

    int ontologies = 0;
    long totalPruned = 0;
    try (Connection cat = DriverManager.getConnection("jdbc:sqlite:" + catalogPath);
         Statement s = cat.createStatement();
         ResultSet rs = s.executeQuery(
             "SELECT s.acronym, s.file_path FROM version_tag t "
                 + "JOIN snapshot s ON s.version_id = t.version_id AND s.acronym = t.acronym "
                 + "WHERE t.tag = 'latest' ORDER BY s.acronym")) {
      while (rs.next()) {
        String acronym = rs.getString(1);
        Path file = baseDir.resolve(rs.getString(2));
        try (SnapshotStore store = SnapshotStore.openFile(file.toString())) {
          // Restore roots first if a prior (unguarded) run emptied this snapshot, so the guarded
          // prune can re-evaluate it. Re-materialize is idempotent; it only recomputes derived data.
          if (store.rootCount() == 0) {
            store.materialize();
          }
          int pruned = store.pruneDeadEndImportRoots(acronym);
          ontologies++;
          totalPruned += pruned;
          if (pruned > 0) {
            System.out.printf("%-24s pruned %d dead-end import roots%n", acronym, pruned);
          }
        } catch (Exception e) {
          System.out.printf("%-24s SKIPPED: %s%n", acronym, e.getMessage());
        }
      }
    }
    System.out.printf("%nbackfill done: %d ontologies, %d roots pruned%n", ontologies, totalPruned);
  }

  private PruneRootsBackfill() {}
}
