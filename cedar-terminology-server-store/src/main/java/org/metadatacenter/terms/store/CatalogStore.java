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

  /**
   * Display/audit-only provenance for a snapshot: the {@code backend} it came from (currently always
   * {@code bioportal}); the source's {@code submissionId} (BioPortal's monotonic, reliable per-upload
   * key — captured at ingest, not reconstructable offline); and the {@code sourceDate}, the date the
   * source claims for itself, distinct from the upload date in {@code released_at}. None of these
   * participate in identity or resolution.
   */
  public record SnapshotProvenance(String backend, Integer submissionId, String sourceDate) {

    private static final java.util.regex.Pattern ISO_DATE =
        java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /**
     * The self-claimed date embedded in a declared-version string, or null when it carries none. The
     * BioPortal version string is often a date or contains one ({@code "2026-06-08"},
     * {@code "releases/2021-10-26"}); this extracts the first valid ISO calendar date. Free-text or
     * non-date labels ({@code "Light"}, {@code "English 051319"}, {@code "10-2024"}) yield null. This
     * is the version-string's self-date (VERSIONING-DESIGN §2), not the BioPortal upload date.
     */
    public static String sourceDateFromDeclaredVersion(String declaredVersion) {
      if (declaredVersion == null) {
        return null;
      }
      java.util.regex.Matcher m = ISO_DATE.matcher(declaredVersion);
      while (m.find()) {
        try {
          return java.time.LocalDate.parse(m.group()).toString(); // validate; reject 2026-13-40
        } catch (java.time.format.DateTimeParseException notADate) {
          // keep scanning: a later match may be a real date
        }
      }
      return null;
    }
  }

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
            default_format TEXT,
            iri            TEXT,
            raw_namespace  TEXT
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
            backend          TEXT DEFAULT 'bioportal',
            submission_id    INTEGER,
            source_date      TEXT,
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
    // Migrate catalogs created before the canonical-iri columns existed. Additive and idempotent, so
    // initSchema stays safe to call on any existing catalog (the iri backfill calls it).
    ensureColumn("ontology", "iri", "TEXT");
    ensureColumn("ontology", "raw_namespace", "TEXT");
    // Provenance columns (display/audit only). backend carries a constant DEFAULT so every existing
    // row reads 'bioportal' with no separate backfill; submission_id and source_date are populated at
    // ingest and by the provenance backfill.
    ensureColumn("snapshot", "backend", "TEXT DEFAULT 'bioportal'");
    ensureColumn("snapshot", "submission_id", "INTEGER");
    ensureColumn("snapshot", "source_date", "TEXT");
  }

  /** Adds {@code column} to {@code table} when absent; a no-op when it is already there. Lets
   *  {@link #initSchema} evolve an on-disk catalog without a full migration. */
  private void ensureColumn(String table, String column, String type) throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
      while (rs.next()) {
        if (column.equalsIgnoreCase(rs.getString("name"))) {
          return;
        }
      }
    }
    try (Statement s = connection.createStatement()) {
      s.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
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
   * Records an ontology's canonical {@code iri} (its cross-source identity) and the raw term-ID
   * {@code rawNamespace} it was derived from (kept as provenance). Set by the derivation backfill from
   * concepts already on disk; a no-op when the acronym is unknown. Idempotent — re-deriving overwrites.
   */
  public void setOntologyIri(String acronym, String iri, String rawNamespace) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "UPDATE ontology SET iri = ?, raw_namespace = ? WHERE acronym = ?")) {
      ps.setString(1, iri);
      ps.setString(2, rawNamespace);
      ps.setString(3, acronym);
      ps.executeUpdate();
    }
  }

  /**
   * The acronym of the ontology that owns a term ID-space (raw namespace) — the reverse of the A6
   * {@code iri} derivation. Resolves <b>only when exactly one</b> ontology claims the namespace: a
   * namespace shared by several (generic webprotege/host bases) is ambiguous and yields empty, so a
   * caller mapping a class IRI to its ontology never guesses. Used to freeze a class-valued
   * constraint: {@code classIri → idspace → acronym → resolve-current}.
   */
  public Optional<String> acronymForNamespace(String rawNamespace) throws SQLException {
    if (rawNamespace == null) {
      return Optional.empty();
    }
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT acronym FROM ontology WHERE raw_namespace = ?")) {
      ps.setString(1, rawNamespace);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        String only = rs.getString(1);
        return rs.next() ? Optional.empty() : Optional.of(only); // >1 match ⇒ ambiguous ⇒ empty
      }
    }
  }

  /** The ontology's canonical {@code iri}, or empty when the acronym is unknown or its iri is not yet
   *  derived. */
  public Optional<String> ontologyIri(String acronym) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("SELECT iri FROM ontology WHERE acronym = ?")) {
      ps.setString(1, acronym);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
      }
    }
  }

  /**
   * Records display/audit-only provenance for a snapshot: its source {@code submissionId} and
   * {@code sourceDate}. A no-op when the snapshot is unknown. Leaves {@code backend} at its default
   * ({@code bioportal}) — every current snapshot is from BioPortal; a future non-BioPortal backend
   * would set it at ingest. Idempotent.
   */
  public void setSnapshotProvenance(String versionId, String acronym, Integer submissionId, String sourceDate)
      throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "UPDATE snapshot SET submission_id = ?, source_date = ? WHERE version_id = ? AND acronym = ?")) {
      setNullableInt(ps, 1, submissionId);
      ps.setString(2, sourceDate);
      ps.setString(3, versionId);
      ps.setString(4, acronym);
      ps.executeUpdate();
    }
  }

  /** A snapshot's provenance, or empty when the {@code (versionId, acronym)} is unknown. */
  public Optional<SnapshotProvenance> snapshotProvenance(String versionId, String acronym) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT backend, submission_id, source_date FROM snapshot WHERE version_id = ? AND acronym = ?")) {
      ps.setString(1, versionId);
      ps.setString(2, acronym);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        return Optional.of(new SnapshotProvenance(rs.getString("backend"),
            getNullableInt(rs, "submission_id"), rs.getString("source_date")));
      }
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

  /**
   * One snapshot's place in a content-hash cutover: its current {@code (acronym, oldVersionId)} and
   * the {@code newVersionId} (normalized content hash) it should take. {@code keep} is false for a
   * merged-away duplicate — a snapshot whose content is byte-different but identical to another of
   * the same ontology; its row is dropped and its tag repointed to the survivor that shares the
   * content hash.
   */
  public record VersionRemap(String acronym, String oldVersionId, String newVersionId, boolean keep) {}

  /**
   * Rewrites {@code version_id}s from the raw-file hash to the normalized content hash in a single
   * transaction (VERSIONING-DESIGN §4.3 cutover). Tags are repointed to the surviving content hash,
   * merged-away duplicate rows are deleted, and surviving rows take their new id. {@code file_path}
   * and {@code file_hash} are left untouched: {@code file_hash} already holds the raw hash (now
   * provenance), and existing snapshot files keep their names ({@code file_path} stays authoritative).
   * Foreign-key enforcement is disabled first so the intermediate state (tags repointed before rows)
   * is allowed.
   */
  public void cutoverToContentHash(List<VersionRemap> remaps) throws SQLException {
    try (Statement fk = connection.createStatement()) {
      fk.execute("PRAGMA foreign_keys=OFF"); // a no-op inside a transaction, so set it before one
    }
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try (PreparedStatement tag = connection.prepareStatement(
             "UPDATE version_tag SET version_id = ? WHERE acronym = ? AND version_id = ?");
         PreparedStatement del = connection.prepareStatement(
             "DELETE FROM snapshot WHERE acronym = ? AND version_id = ?");
         PreparedStatement upd = connection.prepareStatement(
             "UPDATE snapshot SET version_id = ? WHERE acronym = ? AND version_id = ?")) {
      // 1. Repoint every tag from its old id to the surviving content hash (covers a tag that sat on
      //    a merged-away duplicate — it maps to the survivor).
      for (VersionRemap r : remaps) {
        if (!r.newVersionId().equals(r.oldVersionId())) {
          tag.setString(1, r.newVersionId());
          tag.setString(2, r.acronym());
          tag.setString(3, r.oldVersionId());
          tag.executeUpdate();
        }
      }
      // 2. Drop merged-away duplicate rows.
      for (VersionRemap r : remaps) {
        if (!r.keep()) {
          del.setString(1, r.acronym());
          del.setString(2, r.oldVersionId());
          del.executeUpdate();
        }
      }
      // 3. Give each surviving row its content-hash id.
      for (VersionRemap r : remaps) {
        if (r.keep() && !r.newVersionId().equals(r.oldVersionId())) {
          upd.setString(1, r.newVersionId());
          upd.setString(2, r.acronym());
          upd.setString(3, r.oldVersionId());
          upd.executeUpdate();
        }
      }
      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(autoCommit);
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
   * Resolves a specific version of an ontology by its {@code version_id}, scoped to the acronym so a
   * content hash shared by two ontologies (INCENTIVE / INCENTIVE-VARS) resolves to this ontology's
   * own snapshot. This is how a pinned reference is served reproducibly, independent of where
   * {@code latest} currently points.
   */
  public Optional<SnapshotInfo> resolveVersion(String acronym, String versionId) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT * FROM snapshot WHERE acronym = ? AND version_id = ?")) {
      ps.setString(1, acronym);
      ps.setString(2, versionId);
      return firstSnapshot(ps);
    }
  }

  /**
   * Resolves the snapshot that was current as of a calendar date: the newest snapshot of the
   * ontology whose release date is on or before {@code asOfDate} (an ISO {@code YYYY-MM-DD}).
   *
   * Comparison is day-granular. BioPortal release timestamps carry varying UTC offsets
   * ({@code 2022-06-26T18:07:50.000-07:00}), so a lexicographic compare of the full timestamps
   * against a bare date would be both offset-sensitive and off by the time-of-day suffix. Comparing
   * on the date component ({@code substr(released_at,1,10)}) is offset-independent and matches the
   * data's true resolution — publications are dated, not timed. Snapshots with no recorded release
   * date are excluded: they cannot be placed on a timeline. Within a day, ties are broken by the
   * full released timestamp, then ingest order, then version id, so the choice is deterministic.
   */
  public Optional<SnapshotInfo> resolveAsOfDate(String acronym, String asOfDate) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT * FROM snapshot
        WHERE acronym = ? AND released_at IS NOT NULL AND substr(released_at, 1, 10) <= ?
        ORDER BY released_at DESC, ingested_at DESC, version_id DESC
        LIMIT 1""")) {
      ps.setString(1, acronym);
      ps.setString(2, asOfDate);
      return firstSnapshot(ps);
    }
  }

  /**
   * The ontology's snapshots whose self-declared version equals {@code declaredVersion}, newest
   * first. Declared versions are author-supplied and not unique — one ontology can publish several
   * submissions under a single label (INCENTIVE has three {@code 0.1.1}s) — so this returns every
   * match rather than a single row. A caller wanting one snapshot takes the first (newest) and
   * should note the ambiguity when the list is longer than one. The match is exact: declared
   * versions are free-form ({@code "Light"}, {@code "English 051319"}, {@code "Version 1.0.0"}) with
   * no reliable internal ordering, so only the release timestamp orders them.
   */
  public List<SnapshotInfo> resolveByDeclaredVersion(String acronym, String declaredVersion) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT * FROM snapshot
        WHERE acronym = ? AND declared_version = ?
        ORDER BY released_at DESC, ingested_at DESC, version_id DESC""")) {
      ps.setString(1, acronym);
      ps.setString(2, declaredVersion);
      try (ResultSet rs = ps.executeQuery()) {
        List<SnapshotInfo> out = new ArrayList<>();
        while (rs.next()) {
          out.add(readSnapshot(rs));
        }
        return out;
      }
    }
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
