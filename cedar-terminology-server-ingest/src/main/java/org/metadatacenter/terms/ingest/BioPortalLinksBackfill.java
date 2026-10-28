package org.metadatacenter.terms.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SourcePresentation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Explicit, offline metadata migration. No ingestion, vocabulary rewrite or version registration. */
public final class BioPortalLinksBackfill {
  private static final ObjectMapper JSON = new ObjectMapper();
  record Snapshot(String acronym, String versionId, String backend, Path file) {}
  record Change(Snapshot snapshot, String key, String previousValue, String value, String status) {}
  public record Result(int eligible, int existing, int otherProviders) {}

  public static void main(String[] args) throws Exception {
    if (args.length != 1 && !(args.length == 3 && "--apply".equals(args[1]))) {
      throw new IllegalArgumentException("Usage: BioPortalLinksBackfill <catalog.sqlite> [--apply <journal.jsonl>]");
    }
    System.out.println(run(Path.of(args[0]), args.length == 3 ? Path.of(args[2]) : null));
  }

  /** A null journal means read-only planning. Applying requires a durable append-only undo journal. */
  public static Result run(Path catalogPath, Path journal) throws SQLException, IOException {
    Path catalog = catalogPath.toAbsolutePath().normalize();
    if (!Files.isRegularFile(catalog)) throw new IOException("Catalog does not exist: " + catalog);
    if (journal == null) return visit(readCatalog(catalog), null);
    if (journal.toAbsolutePath().normalize().equals(catalog))
      throw new IOException("The journal must not be the catalog");
    try (var lock = CatalogStore.openFile(catalog.toString())) {
      lock.setBusyTimeoutMillis(30_000);
      Result[] result = new Result[1];
      // Share the ingest/cleanup reservation, so the plan cannot race file publication or removal.
      lock.inSnapshotTransaction(() -> {
        var snapshots = readCatalog(catalog);
        Path journalPath = journal.toAbsolutePath().normalize();
        if (snapshots.stream().anyMatch(s -> s.file().equals(journalPath)))
          throw new IOException("The journal must not be a snapshot");
        try (var out = FileChannel.open(journalPath, StandardOpenOption.CREATE,
            StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
          result[0] = visit(snapshots, out);
        }
      });
      return result[0];
    }
  }

  private static List<Snapshot> readCatalog(Path catalog) throws SQLException, IOException {
    var result = new ArrayList<Snapshot>();
    var files = new HashSet<Path>();
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + catalog + "?open_mode=1");
         var statement = connection.createStatement();
         var rows = statement.executeQuery("SELECT acronym,version_id,backend,file_path FROM snapshot ORDER BY acronym,version_id")) {
      while (rows.next()) {
        Path file = catalog.getParent().resolve(rows.getString(4)).toRealPath();
        if (!files.add(file)) throw new IOException("Multiple catalog records share a snapshot file: " + file);
        result.add(new Snapshot(rows.getString(1), rows.getString(2), rows.getString(3), file));
      }
    }
    return result;
  }

  private static Result visit(List<Snapshot> snapshots, FileChannel journal) throws SQLException, IOException {
    int eligible = 0, existing = 0, others = 0;
    String profile = JSON.writeValueAsString(BioPortalDownloader.publicLinks());
    for (var snapshot : snapshots) {
      if (!"bioportal".equals(snapshot.backend())) { others++; continue; }
      // READWRITE without CREATE on apply; an absent file must never become an empty database.
      try (var connection = DriverManager.getConnection("jdbc:sqlite:" + snapshot.file()
          + "?open_mode=" + (journal == null ? "1" : "2"));
           var statement = connection.createStatement()) {
        statement.execute("PRAGMA busy_timeout=30000");
        if (journal != null) statement.execute("BEGIN IMMEDIATE");
        try {
          boolean present;
          try (var query = connection.prepareStatement("SELECT 1 FROM meta WHERE key=?")) {
            query.setString(1, SourcePresentation.META_KEY);
            try (var rows = query.executeQuery()) { present = rows.next(); }
          }
          if (present) { existing++; }
          else {
            eligible++;
            if (journal != null) {
              // The single INSERT must not cause a trigger to modify vocabulary or other metadata.
              try (var rows = statement.executeQuery("SELECT 1 FROM sqlite_master WHERE type='trigger' AND tbl_name='meta'")) {
                if (rows.next()) throw new SQLException("Unexpected metadata trigger: " + snapshot.file());
              }
              append(journal, new Change(snapshot, SourcePresentation.META_KEY, null, profile, "planned"));
              try (var insert = connection.prepareStatement("INSERT INTO meta(key,value) VALUES (?,?)")) {
                insert.setString(1, SourcePresentation.META_KEY);
                insert.setString(2, profile);
                insert.executeUpdate();
              }
            }
          }
          if (journal != null) {
            statement.execute("COMMIT");
            if (!present) append(journal, new Change(snapshot, SourcePresentation.META_KEY, null, profile, "committed"));
          }
        } catch (SQLException | IOException | RuntimeException e) {
          if (journal != null) {
            try { statement.execute("ROLLBACK"); } catch (SQLException rollback) { e.addSuppressed(rollback); }
          }
          throw e;
        }
      }
      if ((eligible + existing) % 250 == 0) System.out.printf("BioPortal snapshots checked: %d%n", eligible + existing);
    }
    return new Result(eligible, existing, others);
  }

  private static void append(FileChannel journal, Change change) throws IOException {
    var bytes = ByteBuffer.wrap((JSON.writeValueAsString(change) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    while (bytes.hasRemaining()) journal.write(bytes);
    journal.force(true);
  }

  private BioPortalLinksBackfill() {}
}
