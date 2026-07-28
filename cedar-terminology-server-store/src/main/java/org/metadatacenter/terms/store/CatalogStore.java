package org.metadatacenter.terms.store;

import java.nio.file.Path;
import java.nio.file.Paths;
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
  /**
   * Directory the catalog file lives in, used to resolve snapshot {@code file_path}s stored relative
   * to it. Null for an in-memory catalog, in which case relative paths are left as-is (resolved
   * against the process working directory by the opener).
   */
  private final Path baseDir;

  private CatalogStore(Connection connection, Path baseDir) {
    this.connection = connection;
    this.baseDir = baseDir;
  }

  public static CatalogStore openFile(String path) throws SQLException {
    Path parent = Paths.get(path).toAbsolutePath().getParent();
    return new CatalogStore(DriverManager.getConnection("jdbc:sqlite:" + path), parent);
  }

  public static CatalogStore openInMemory() throws SQLException {
    return new CatalogStore(DriverManager.getConnection("jdbc:sqlite::memory:"), null);
  }

  /**
   * The directory the catalog file lives in, or empty for an in-memory catalog. Snapshot
   * {@code file_path}s are stored relative to this so the whole store (catalog + snapshots) can be
   * copied to any location and served without rewriting paths. Ingestion uses it to relativize the
   * paths it records.
   */
  public Optional<Path> baseDir() {
    return Optional.ofNullable(baseDir);
  }

  /**
   * Resolves a stored snapshot path to an absolute filesystem path. An absolute stored path is
   * returned unchanged (backward compatibility with catalogs written before relative paths); a
   * relative one is resolved against {@link #baseDir}, or left as-is when there is no base dir.
   */
  private String resolvePath(String stored) {
    if (stored == null) {
      return null;
    }
    Path p = Paths.get(stored);
    if (p.isAbsolute() || baseDir == null) {
      return stored;
    }
    return baseDir.resolve(p).normalize().toString();
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
      // The key is (version_id, acronym), not version_id alone: version_id is a pure content hash,
      // and two different ontologies can legitimately publish byte-identical downloads (INCENTIVE and
      // INCENTIVE-VARS both resolve to the same SKOS file on BioPortal), which then share a hash.
      // Keying on the pair lets that content live once per ontology; keying on the hash alone would
      // let one ontology's snapshot silently overwrite the other's.
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS snapshot (
            version_id       TEXT NOT NULL,
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
            license_tier     TEXT NOT NULL,
            PRIMARY KEY (version_id, acronym)
          )""");
      s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_snapshot_acronym ON snapshot(acronym, released_at)");
      s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_snapshot_version ON snapshot(version_id)");
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS version_tag (
            acronym    TEXT NOT NULL REFERENCES ontology(acronym),
            tag        TEXT NOT NULL,
            version_id TEXT NOT NULL,
            PRIMARY KEY (acronym, tag),
            FOREIGN KEY (version_id, acronym) REFERENCES snapshot(version_id, acronym)
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
   * Records an ingested snapshot. Idempotent on {@code (version_id, acronym)}: re-ingesting the same
   * content for the same ontology (same content-hash id) updates the existing row rather than
   * failing, so a backfill can be re-run safely. A different ontology that happens to share the
   * content hash gets its own row rather than overwriting this one.
   */
  public void addSnapshot(SnapshotInfo s) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        INSERT INTO snapshot (version_id, acronym, declared_version, released_at, ingested_at, format,
                              hierarchy_status, class_count, edge_count, file_path, file_hash, license_tier)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(version_id, acronym) DO UPDATE SET
          declared_version = excluded.declared_version,
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
        SELECT s.* FROM version_tag t
        JOIN snapshot s ON s.version_id = t.version_id AND s.acronym = t.acronym
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

  /**
   * Looks up a snapshot by its version id. Since a content hash can be shared by more than one
   * ontology, this returns any one matching row; callers that only need the frozen content (which is
   * identical across the sharers, by definition of the hash) — such as value-set expansion — do not
   * care which. Callers that need a specific ontology's row should use {@link #resolve} instead.
   */
  public Optional<SnapshotInfo> getSnapshot(String versionId) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM snapshot WHERE version_id = ? LIMIT 1")) {
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

  private Optional<SnapshotInfo> firstSnapshot(PreparedStatement ps) throws SQLException {
    try (ResultSet rs = ps.executeQuery()) {
      return rs.next() ? Optional.of(readSnapshot(rs)) : Optional.empty();
    }
  }

  private SnapshotInfo readSnapshot(ResultSet rs) throws SQLException {
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
        resolvePath(rs.getString("file_path")),
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
