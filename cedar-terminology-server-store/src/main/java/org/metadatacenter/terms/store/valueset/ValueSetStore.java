package org.metadatacenter.terms.store.valueset;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persists value sets and their versions. A value set has a stable id and, per version, a
 * {@link ValueSetDefinition}. Storing versioned definitions (rather than materialized member lists)
 * keeps a value set reproducible: expanding a version always resolves the same pinned snapshots.
 *
 * Lives at the catalog tier because a value set can span ontologies and is versioned independently
 * of any one of them.
 */
public class ValueSetStore implements AutoCloseable {

  /** Value-set identity. */
  public record ValueSetInfo(String id, String name, String description) {}

  private final Connection connection;

  private ValueSetStore(Connection connection) {
    this.connection = connection;
  }

  public static ValueSetStore openFile(String path) throws SQLException {
    return new ValueSetStore(DriverManager.getConnection("jdbc:sqlite:" + path));
  }

  public static ValueSetStore openInMemory() throws SQLException {
    return new ValueSetStore(DriverManager.getConnection("jdbc:sqlite::memory:"));
  }

  public void initSchema() throws SQLException {
    try (Statement s = connection.createStatement()) {
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS value_set (
            id          TEXT PRIMARY KEY,
            name        TEXT NOT NULL,
            description TEXT
          )""");
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS value_set_version (
            value_set_id        TEXT NOT NULL REFERENCES value_set(id),
            version             TEXT NOT NULL,
            kind                TEXT NOT NULL,
            snapshot_version_id TEXT,
            root_iri            TEXT,
            include_root        INTEGER,
            predicate           TEXT,
            object_iri          TEXT,
            created_at          TEXT,
            PRIMARY KEY (value_set_id, version)
          )""");
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS value_set_member (
            value_set_id      TEXT NOT NULL,
            version           TEXT NOT NULL,
            member_version_id TEXT NOT NULL,
            concept_iri       TEXT NOT NULL,
            PRIMARY KEY (value_set_id, version, member_version_id, concept_iri)
          )""");
    }
  }

  /** Registers or updates a value set's identity. */
  public void upsertValueSet(String id, String name, String description) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        INSERT INTO value_set (id, name, description) VALUES (?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET name = excluded.name, description = excluded.description""")) {
      ps.setString(1, id);
      ps.setString(2, name);
      ps.setString(3, description);
      ps.executeUpdate();
    }
  }

  /** Stores (or replaces) a version's definition. */
  public void putVersion(String valueSetId, String version, ValueSetDefinition def) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        INSERT INTO value_set_version (value_set_id, version, kind, snapshot_version_id, root_iri,
                                       include_root, predicate, object_iri, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(value_set_id, version) DO UPDATE SET
          kind = excluded.kind, snapshot_version_id = excluded.snapshot_version_id,
          root_iri = excluded.root_iri, include_root = excluded.include_root,
          predicate = excluded.predicate, object_iri = excluded.object_iri""")) {
      ps.setString(1, valueSetId);
      ps.setString(2, version);
      ps.setString(3, def.kind().name());
      ps.setString(4, def.snapshotVersionId());
      ps.setString(5, def.rootIri());
      ps.setObject(6, def.kind() == ValueSetDefinition.Kind.DESCENDANTS ? (def.includeRoot() ? 1 : 0) : null);
      ps.setString(7, def.predicate());
      ps.setString(8, def.objectIri());
      ps.setString(9, Instant.now().toString());
      ps.executeUpdate();
    }
    try (PreparedStatement del = connection.prepareStatement(
        "DELETE FROM value_set_member WHERE value_set_id = ? AND version = ?")) {
      del.setString(1, valueSetId);
      del.setString(2, version);
      del.executeUpdate();
    }
    if (def.kind() == ValueSetDefinition.Kind.EXTENSIONAL) {
      try (PreparedStatement ins = connection.prepareStatement("""
          INSERT OR IGNORE INTO value_set_member (value_set_id, version, member_version_id, concept_iri)
          VALUES (?, ?, ?, ?)""")) {
        for (PinnedConcept m : def.members()) {
          ins.setString(1, valueSetId);
          ins.setString(2, version);
          ins.setString(3, m.versionId());
          ins.setString(4, m.conceptIri());
          ins.executeUpdate();
        }
      }
    }
  }

  /** Reconstructs a version's definition. */
  public Optional<ValueSetDefinition> getDefinition(String valueSetId, String version) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT kind, snapshot_version_id, root_iri, include_root, predicate, object_iri
        FROM value_set_version WHERE value_set_id = ? AND version = ?""")) {
      ps.setString(1, valueSetId);
      ps.setString(2, version);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return Optional.empty();
        }
        ValueSetDefinition.Kind kind = ValueSetDefinition.Kind.valueOf(rs.getString("kind"));
        return Optional.of(switch (kind) {
          case DESCENDANTS -> ValueSetDefinition.descendants(
              rs.getString("snapshot_version_id"), rs.getString("root_iri"), rs.getInt("include_root") != 0);
          case RELATION -> ValueSetDefinition.relation(
              rs.getString("snapshot_version_id"), rs.getString("predicate"), rs.getString("object_iri"));
          case EXTENSIONAL -> ValueSetDefinition.extensional(loadMembers(valueSetId, version));
        });
      }
    }
  }

  public List<ValueSetInfo> listValueSets() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT id, name, description FROM value_set ORDER BY id")) {
      List<ValueSetInfo> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new ValueSetInfo(rs.getString(1), rs.getString(2), rs.getString(3)));
      }
      return out;
    }
  }

  public List<String> listVersions(String valueSetId) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT version FROM value_set_version WHERE value_set_id = ? ORDER BY version")) {
      ps.setString(1, valueSetId);
      try (ResultSet rs = ps.executeQuery()) {
        List<String> out = new ArrayList<>();
        while (rs.next()) {
          out.add(rs.getString(1));
        }
        return out;
      }
    }
  }

  private List<PinnedConcept> loadMembers(String valueSetId, String version) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT member_version_id, concept_iri FROM value_set_member
        WHERE value_set_id = ? AND version = ? ORDER BY concept_iri""")) {
      ps.setString(1, valueSetId);
      ps.setString(2, version);
      try (ResultSet rs = ps.executeQuery()) {
        List<PinnedConcept> out = new ArrayList<>();
        while (rs.next()) {
          out.add(new PinnedConcept(rs.getString(1), rs.getString(2)));
        }
        return out;
      }
    }
  }

  @Override
  public void close() throws SQLException {
    connection.close();
  }
}
