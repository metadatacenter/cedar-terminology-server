package org.metadatacenter.terms.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The global catalog: a small SQLite database that knows which ontology versions have been
 * ingested, where each snapshot file lives, and which version a movable tag such as {@code latest}
 * currently points to.
 *
 * Unlike {@link SnapshotStore} (one file per snapshot, no version columns), the catalog is a single
 * database spanning all ontologies and versions, so its rows carry {@code acronym} and
 * {@code version_id}. Request resolution is: look up {@code (acronym, tag)} here to get a
 * {@link SnapshotInfo}, open that snapshot file, then serve the read from it.
 *
 * A {@code version_id} is a content hash of the frozen subgraph, not the ontology's self-declared
 * version, so a pinned reference is reproducible.
 */
public class CatalogStore implements AutoCloseable {

  /** Metadata about a known ontology. */
  public record OntologyInfo(String acronym, String name, String sourceIri, String defaultFormat) {}

  /** Metadata about one ingested snapshot (an ontology at a version). */
  public record SnapshotInfo(
      String versionId,
      String acronym,
      String declaredVersion,
      String releasedAt,
      String ingestedAt,
      String format,
      String hierarchyStatus,
      Integer classCount,
      Integer edgeCount,
      String filePath,
      String fileHash,
      String licenseTier) {}

  /** The conventional tag for the current version of an ontology. */
  public static final String TAG_LATEST = "latest";

  private final Connection connection;

  private CatalogStore(Connection connection) {
    this.connection = connection;
  }

  public static CatalogStore openFile(String path) throws SQLException {
    return new CatalogStore(DriverManager.getConnection("jdbc:sqlite:" + path));
  }

  public static CatalogStore openInMemory() throws SQLException {
    return new CatalogStore(DriverManager.getConnection("jdbc:sqlite::memory:"));
  }

  public void initSchema() throws SQLException {
    try (Statement s = connection.createStatement()) {
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS ontology (
            acronym        TEXT PRIMARY KEY,
            name           TEXT NOT NULL,
            source_iri     TEXT,
            default_format TEXT
          )""");
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS snapshot (
            version_id       TEXT PRIMARY KEY,
            acronym          TEXT NOT NULL REFERENCES ontology(acronym),
            declared_version TEXT,
            released_at      TEXT,
            ingested_at      TEXT,
            format           TEXT NOT NULL,
            hierarchy_status TEXT NOT NULL,
            class_count      INTEGER,
            edge_count       INTEGER,
            file_path        TEXT NOT NULL,
            file_hash        TEXT NOT NULL,
            license_tier     TEXT NOT NULL
          )""");
      s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_snapshot_acronym ON snapshot(acronym, released_at)");
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS version_tag (
            acronym    TEXT NOT NULL REFERENCES ontology(acronym),
            tag        TEXT NOT NULL,
            version_id TEXT NOT NULL REFERENCES snapshot(version_id),
            PRIMARY KEY (acronym, tag)
          )""");
    }
  }

  /* --------------------------------------------------------------------------------------------
   * Registration
   * ------------------------------------------------------------------------------------------ */

  /** Registers or updates an ontology. */
  public void upsertOntology(OntologyInfo o) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        INSERT INTO ontology (acronym, name, source_iri, default_format) VALUES (?, ?, ?, ?)
        ON CONFLICT(acronym) DO UPDATE SET
          name = excluded.name, source_iri = excluded.source_iri, default_format = excluded.default_format""")) {
      ps.setString(1, o.acronym());
      ps.setString(2, o.name());
      ps.setString(3, o.sourceIri());
      ps.setString(4, o.defaultFormat());
      ps.executeUpdate();
    }
  }

  /**
   * Records an ingested snapshot. Idempotent on {@code version_id}: re-ingesting the same content
   * (same content-hash id) updates the existing row rather than failing, so a backfill can be
   * re-run safely.
   */
  public void addSnapshot(SnapshotInfo s) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        INSERT INTO snapshot (version_id, acronym, declared_version, released_at, ingested_at, format,
                              hierarchy_status, class_count, edge_count, file_path, file_hash, license_tier)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(version_id) DO UPDATE SET
          acronym = excluded.acronym, declared_version = excluded.declared_version,
          released_at = excluded.released_at, ingested_at = excluded.ingested_at,
          format = excluded.format, hierarchy_status = excluded.hierarchy_status,
          class_count = excluded.class_count, edge_count = excluded.edge_count,
          file_path = excluded.file_path, file_hash = excluded.file_hash,
          license_tier = excluded.license_tier""")) {
      ps.setString(1, s.versionId());
      ps.setString(2, s.acronym());
      ps.setString(3, s.declaredVersion());
      ps.setString(4, s.releasedAt());
      ps.setString(5, s.ingestedAt());
      ps.setString(6, s.format());
      ps.setString(7, s.hierarchyStatus());
      setNullableInt(ps, 8, s.classCount());
      setNullableInt(ps, 9, s.edgeCount());
      ps.setString(10, s.filePath());
      ps.setString(11, s.fileHash());
      ps.setString(12, s.licenseTier());
      ps.executeUpdate();
    }
  }

  /**
   * Points a tag (e.g. {@link #TAG_LATEST}) at a version. Idempotent: re-pointing a tag replaces
   * the previous target in a single statement, so a reader never sees the tag between two versions.
   */
  public void setTag(String acronym, String tag, String versionId) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        INSERT INTO version_tag (acronym, tag, version_id) VALUES (?, ?, ?)
        ON CONFLICT(acronym, tag) DO UPDATE SET version_id = excluded.version_id""")) {
      ps.setString(1, acronym);
      ps.setString(2, tag);
      ps.setString(3, versionId);
      ps.executeUpdate();
    }
  }

  /* --------------------------------------------------------------------------------------------
   * Resolution
   * ------------------------------------------------------------------------------------------ */

  /** Resolves the snapshot a tag points to for an ontology. */
  public Optional<SnapshotInfo> resolve(String acronym, String tag) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT s.* FROM version_tag t JOIN snapshot s ON s.version_id = t.version_id
        WHERE t.acronym = ? AND t.tag = ?""")) {
      ps.setString(1, acronym);
      ps.setString(2, tag);
      return firstSnapshot(ps);
    }
  }

  /** Resolves the current ("latest") snapshot for an ontology. */
  public Optional<SnapshotInfo> resolveLatest(String acronym) throws SQLException {
    return resolve(acronym, TAG_LATEST);
  }

  /** Looks up a snapshot by its version id. */
  public Optional<SnapshotInfo> getSnapshot(String versionId) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM snapshot WHERE version_id = ?")) {
      ps.setString(1, versionId);
      return firstSnapshot(ps);
    }
  }

  public List<OntologyInfo> listOntologies() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT acronym, name, source_iri, default_format FROM ontology ORDER BY acronym")) {
      List<OntologyInfo> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new OntologyInfo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
      }
      return out;
    }
  }

  public List<SnapshotInfo> listSnapshots(String acronym) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT * FROM snapshot WHERE acronym = ? ORDER BY released_at")) {
      ps.setString(1, acronym);
      try (ResultSet rs = ps.executeQuery()) {
        List<SnapshotInfo> out = new ArrayList<>();
        while (rs.next()) {
          out.add(readSnapshot(rs));
        }
        return out;
      }
    }
  }

  /* --------------------------------------------------------------------------------------------
   * Helpers
   * ------------------------------------------------------------------------------------------ */

  private static Optional<SnapshotInfo> firstSnapshot(PreparedStatement ps) throws SQLException {
    try (ResultSet rs = ps.executeQuery()) {
      return rs.next() ? Optional.of(readSnapshot(rs)) : Optional.empty();
    }
  }

  private static SnapshotInfo readSnapshot(ResultSet rs) throws SQLException {
    return new SnapshotInfo(
        rs.getString("version_id"),
        rs.getString("acronym"),
        rs.getString("declared_version"),
        rs.getString("released_at"),
        rs.getString("ingested_at"),
        rs.getString("format"),
        rs.getString("hierarchy_status"),
        getNullableInt(rs, "class_count"),
        getNullableInt(rs, "edge_count"),
        rs.getString("file_path"),
        rs.getString("file_hash"),
        rs.getString("license_tier"));
  }

  private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
    if (value == null) {
      ps.setNull(index, java.sql.Types.INTEGER);
    } else {
      ps.setInt(index, value);
    }
  }

  private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
    int v = rs.getInt(column);
    return rs.wasNull() ? null : v;
  }

  @Override
  public void close() throws SQLException {
    connection.close();
  }
}
