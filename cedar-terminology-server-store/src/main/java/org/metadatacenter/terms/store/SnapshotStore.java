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
 * A read-optimized, single-snapshot hierarchy store backed by one SQLite database.
 *
 * One instance corresponds to exactly one {@code (ontology, version)} snapshot, so the schema
 * carries no ontology or version columns — those are properties of the file. Concepts are keyed
 * by IRI; the transitive closure is precomputed at ingestion so subsumption and descendant
 * queries are indexed reads rather than graph traversals.
 *
 * Lifecycle: construct (opens a connection), {@link #initSchema()}, ingest concepts and edges,
 * {@link #materialize()} to compute the closure and roots, then serve reads. The store holds a
 * single JDBC connection, which matters for an in-memory database (its contents live only for the
 * life of the connection). Call {@link #close()} when done.
 *
 * Closure materialization assumes the asserted hierarchy is acyclic. Cycle handling (SCC collapse)
 * is deferred; see the hierarchy-extractor design note.
 */
public class SnapshotStore implements AutoCloseable {

  /**
   * A concept row with the fields callers commonly need together: its IRI, preferred label,
   * obsolete flag, and whether it has any direct children (so a UI can render an expand control).
   */
  public record Concept(String iri, String prefLabel, boolean obsolete, boolean hasChildren) {}

  private final Connection connection;

  private SnapshotStore(Connection connection) {
    this.connection = connection;
  }

  /** Opens a snapshot store backed by the given SQLite file (created if absent). */
  public static SnapshotStore openFile(String path) throws SQLException {
    return new SnapshotStore(DriverManager.getConnection("jdbc:sqlite:" + path));
  }

  /** Opens an in-memory snapshot store, useful for tests and transient ingestion. */
  public static SnapshotStore openInMemory() throws SQLException {
    return new SnapshotStore(DriverManager.getConnection("jdbc:sqlite::memory:"));
  }

  public void initSchema() throws SQLException {
    try (Statement s = connection.createStatement()) {
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS concept (
            id         INTEGER PRIMARY KEY,
            iri        TEXT NOT NULL UNIQUE,
            pref_label TEXT,
            obsolete   INTEGER NOT NULL DEFAULT 0
          )""");
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS edge (
            child_id    INTEGER NOT NULL,
            parent_id   INTEGER NOT NULL,
            source_pred TEXT,
            PRIMARY KEY (child_id, parent_id)
          ) WITHOUT ROWID""");
      s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_edge_parent ON edge(parent_id)");
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS closure (
            ancestor_id   INTEGER NOT NULL,
            descendant_id INTEGER NOT NULL,
            depth         INTEGER,
            PRIMARY KEY (ancestor_id, descendant_id)
          ) WITHOUT ROWID""");
      s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_closure_desc ON closure(descendant_id)");
      s.executeUpdate("CREATE TABLE IF NOT EXISTS root (concept_id INTEGER PRIMARY KEY)");
    }
  }

  /* --------------------------------------------------------------------------------------------
   * Ingestion
   * ------------------------------------------------------------------------------------------ */

  /** Adds a concept. Idempotent on IRI; a repeated IRI is ignored. */
  public void addConcept(String iri, String prefLabel) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "INSERT OR IGNORE INTO concept (iri, pref_label) VALUES (?, ?)")) {
      ps.setString(1, iri);
      ps.setString(2, prefLabel);
      ps.executeUpdate();
    }
  }

  /**
   * Adds a child-to-parent hierarchy edge. Both concepts must already exist. The {@code sourcePred}
   * records which relation produced the edge (e.g. {@code rdfs:subClassOf}, {@code skos:broader}).
   */
  public void addEdge(String childIri, String parentIri, String sourcePred) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        INSERT OR IGNORE INTO edge (child_id, parent_id, source_pred)
        SELECT c.id, p.id, ? FROM concept c, concept p WHERE c.iri = ? AND p.iri = ?""")) {
      ps.setString(1, sourcePred);
      ps.setString(2, childIri);
      ps.setString(3, parentIri);
      ps.executeUpdate();
    }
  }

  /**
   * Computes the transitive closure from the asserted edges and records the roots (concepts with
   * no parent). Run once after all concepts and edges are ingested. Assumes an acyclic hierarchy.
   */
  public void materialize() throws SQLException {
    try (Statement s = connection.createStatement()) {
      s.executeUpdate("DELETE FROM closure");
      s.executeUpdate("""
          INSERT INTO closure (ancestor_id, descendant_id, depth)
          WITH RECURSIVE walk(ancestor_id, descendant_id, depth) AS (
              SELECT parent_id, child_id, 1 FROM edge
            UNION
              SELECT w.ancestor_id, e.child_id, w.depth + 1
              FROM walk w JOIN edge e ON e.parent_id = w.descendant_id
          )
          SELECT ancestor_id, descendant_id, MIN(depth)
          FROM walk GROUP BY ancestor_id, descendant_id""");
      s.executeUpdate("DELETE FROM root");
      s.executeUpdate("""
          INSERT INTO root (concept_id)
          SELECT c.id FROM concept c
          WHERE NOT EXISTS (SELECT 1 FROM edge e WHERE e.child_id = c.id)""");
    }
  }

  /* --------------------------------------------------------------------------------------------
   * Reads — the terminology-server operations, each an indexed lookup, no traversal.
   * ------------------------------------------------------------------------------------------ */

  /** validate-code: whether the snapshot contains the concept. */
  public boolean contains(String iri) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM concept WHERE iri = ?")) {
      ps.setString(1, iri);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  /** lookup: the concept's preferred label, if present. */
  public Optional<String> prefLabel(String iri) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("SELECT pref_label FROM concept WHERE iri = ?")) {
      ps.setString(1, iri);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
      }
    }
  }

  /** Direct children (subclasses) of a concept. */
  public List<String> children(String parentIri) throws SQLException {
    return queryIris("""
        SELECT c.iri FROM edge e
        JOIN concept c ON c.id = e.child_id
        JOIN concept p ON p.id = e.parent_id
        WHERE p.iri = ? ORDER BY c.iri""", parentIri);
  }

  /** Direct parents of a concept. */
  public List<String> parents(String childIri) throws SQLException {
    return queryIris("""
        SELECT p.iri FROM edge e
        JOIN concept c ON c.id = e.child_id
        JOIN concept p ON p.id = e.parent_id
        WHERE c.iri = ? ORDER BY p.iri""", childIri);
  }

  /** All descendants of a concept (value-set expansion). */
  public List<String> descendants(String ancestorIri) throws SQLException {
    return queryIris("""
        SELECT d.iri FROM closure cl
        JOIN concept a ON a.id = cl.ancestor_id
        JOIN concept d ON d.id = cl.descendant_id
        WHERE a.iri = ? ORDER BY d.iri""", ancestorIri);
  }

  /** All ancestors of a concept. */
  public List<String> ancestors(String descendantIri) throws SQLException {
    return queryIris("""
        SELECT a.iri FROM closure cl
        JOIN concept a ON a.id = cl.ancestor_id
        JOIN concept d ON d.id = cl.descendant_id
        WHERE d.iri = ? ORDER BY a.iri""", descendantIri);
  }

  /** subsumes: whether {@code ancestorIri} is a (transitive) ancestor of {@code descendantIri}. */
  public boolean subsumes(String ancestorIri, String descendantIri) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT 1 FROM closure cl
        JOIN concept a ON a.id = cl.ancestor_id
        JOIN concept d ON d.id = cl.descendant_id
        WHERE a.iri = ? AND d.iri = ? LIMIT 1""")) {
      ps.setString(1, ancestorIri);
      ps.setString(2, descendantIri);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  /** Root concepts (top of the hierarchy). */
  public List<String> roots() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("""
             SELECT c.iri FROM root r JOIN concept c ON c.id = r.concept_id ORDER BY c.iri""")) {
      List<String> out = new ArrayList<>();
      while (rs.next()) {
        out.add(rs.getString(1));
      }
      return out;
    }
  }

  /* --------------------------------------------------------------------------------------------
   * Detail reads — return concept rows (iri + label + obsolete + hasChildren) in one query, so
   * callers can build richer objects without a per-row follow-up.
   * ------------------------------------------------------------------------------------------ */

  /** The concept, if present, with its label and hasChildren flag. */
  public Optional<Concept> get(String iri) throws SQLException {
    List<Concept> rows = queryConcepts("""
        SELECT c.iri, c.pref_label, c.obsolete,
               EXISTS(SELECT 1 FROM edge e WHERE e.parent_id = c.id) AS has_children
        FROM concept c WHERE c.iri = ?""", iri);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  /** Direct children as concept rows. */
  public List<Concept> childrenDetailed(String parentIri) throws SQLException {
    return queryConcepts("""
        SELECT c.iri, c.pref_label, c.obsolete,
               EXISTS(SELECT 1 FROM edge e2 WHERE e2.parent_id = c.id) AS has_children
        FROM edge e JOIN concept c ON c.id = e.child_id
        JOIN concept p ON p.id = e.parent_id
        WHERE p.iri = ? ORDER BY c.iri""", parentIri);
  }

  /** Direct parents as concept rows. */
  public List<Concept> parentsDetailed(String childIri) throws SQLException {
    return queryConcepts("""
        SELECT p.iri, p.pref_label, p.obsolete,
               EXISTS(SELECT 1 FROM edge e2 WHERE e2.parent_id = p.id) AS has_children
        FROM edge e JOIN concept c ON c.id = e.child_id
        JOIN concept p ON p.id = e.parent_id
        WHERE c.iri = ? ORDER BY p.iri""", childIri);
  }

  /** All descendants as concept rows. */
  public List<Concept> descendantsDetailed(String ancestorIri) throws SQLException {
    return queryConcepts("""
        SELECT d.iri, d.pref_label, d.obsolete,
               EXISTS(SELECT 1 FROM edge e2 WHERE e2.parent_id = d.id) AS has_children
        FROM closure cl JOIN concept a ON a.id = cl.ancestor_id
        JOIN concept d ON d.id = cl.descendant_id
        WHERE a.iri = ? ORDER BY d.iri""", ancestorIri);
  }

  /** Root concepts as concept rows. */
  public List<Concept> rootsDetailed() throws SQLException {
    return queryConcepts("""
        SELECT c.iri, c.pref_label, c.obsolete,
               EXISTS(SELECT 1 FROM edge e2 WHERE e2.parent_id = c.id) AS has_children
        FROM root r JOIN concept c ON c.id = r.concept_id ORDER BY c.iri""", null);
  }

  private List<Concept> queryConcepts(String sql, String param) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      if (param != null) {
        ps.setString(1, param);
      }
      try (ResultSet rs = ps.executeQuery()) {
        List<Concept> out = new ArrayList<>();
        while (rs.next()) {
          out.add(new Concept(rs.getString(1), rs.getString(2), rs.getInt(3) != 0, rs.getInt(4) != 0));
        }
        return out;
      }
    }
  }

  /* --------------------------------------------------------------------------------------------
   * Bulk reads — enumerate the whole snapshot (for validation and export).
   * ------------------------------------------------------------------------------------------ */

  /** Every concept IRI in the snapshot. */
  public List<String> allConceptIris() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT iri FROM concept")) {
      List<String> out = new ArrayList<>();
      while (rs.next()) {
        out.add(rs.getString(1));
      }
      return out;
    }
  }

  /** Every direct edge as a {@code [childIri, parentIri]} pair. */
  public List<String[]> allEdges() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("""
             SELECT c.iri, p.iri FROM edge e
             JOIN concept c ON c.id = e.child_id
             JOIN concept p ON p.id = e.parent_id""")) {
      List<String[]> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new String[]{rs.getString(1), rs.getString(2)});
      }
      return out;
    }
  }

  /** Every closure pair, encoded as {@code ancestorIri + '\t' + descendantIri}. */
  public java.util.Set<String> allClosurePairs() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("""
             SELECT a.iri, d.iri FROM closure cl
             JOIN concept a ON a.id = cl.ancestor_id
             JOIN concept d ON d.id = cl.descendant_id""")) {
      java.util.Set<String> out = new java.util.HashSet<>();
      while (rs.next()) {
        out.add(rs.getString(1) + '\t' + rs.getString(2));
      }
      return out;
    }
  }

  private List<String> queryIris(String sql, String param) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, param);
      try (ResultSet rs = ps.executeQuery()) {
        List<String> out = new ArrayList<>();
        while (rs.next()) {
          out.add(rs.getString(1));
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
