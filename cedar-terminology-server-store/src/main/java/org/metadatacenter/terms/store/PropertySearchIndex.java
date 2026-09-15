package org.metadatacenter.terms.store;

import java.sql.*;
import java.util.*;

/** Derived property search tables in the existing cross-snapshot search-index.sqlite. */
public final class PropertySearchIndex {
  public record Hit(String sourceAcronym, String versionId, SnapshotProperties.Summary property) {}

  public record Page(long total, List<Hit> items) {}

  private final SnapshotProperties.ConnectionSource source;

  PropertySearchIndex(SnapshotProperties.ConnectionSource source) {
    this.source = source;
  }

  private Connection connection() throws SQLException {
    return source.get();
  }

  public void initSchema() throws SQLException {
    try (Statement s = connection().createStatement()) {
      s.execute(
          "CREATE TABLE IF NOT EXISTS property_indexed_snapshot(acronym TEXT PRIMARY KEY, version"
              + " TEXT NOT NULL)");
      s.execute(
          "CREATE TABLE IF NOT EXISTS property_index(id INTEGER PRIMARY KEY, acronym TEXT NOT"
              + " NULL,iri TEXT NOT NULL,kind TEXT NOT NULL,label TEXT NOT NULL,obsolete INTEGER"
              + " NOT NULL,has_children INTEGER NOT NULL,UNIQUE(acronym,iri,kind))");
      s.execute(
          "CREATE VIRTUAL TABLE IF NOT EXISTS property_index_fts USING"
              + " fts5(label,iri,names,tokenize='unicode61 remove_diacritics 2')");
    }
  }

  public boolean available() throws SQLException {
    try (Statement s = connection().createStatement();
        ResultSet r =
            s.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND"
                    + " name='property_indexed_snapshot'")) {
      return r.next();
    }
  }

  public Optional<String> version(String acronym) throws SQLException {
    if (!available()) return Optional.empty();
    try (PreparedStatement p =
        connection()
            .prepareStatement("SELECT version FROM property_indexed_snapshot WHERE acronym=?")) {
      p.setString(1, acronym);
      try (ResultSet r = p.executeQuery()) {
        return r.next() ? Optional.of(r.getString(1)) : Optional.empty();
      }
    }
  }

  public void replace(String acronym, String version, List<SnapshotProperties.Property> properties)
      throws SQLException {
    initSchema();
    Connection c = connection();
    boolean auto = c.getAutoCommit();
    c.setAutoCommit(false);
    try {
      try (PreparedStatement p =
          c.prepareStatement(
              "DELETE FROM property_index_fts WHERE rowid IN(SELECT id FROM property_index WHERE"
                  + " acronym=?)")) {
        p.setString(1, acronym);
        p.executeUpdate();
      }
      try (PreparedStatement p = c.prepareStatement("DELETE FROM property_index WHERE acronym=?")) {
        p.setString(1, acronym);
        p.executeUpdate();
      }
      Set<String> parents = new HashSet<>();
      for (var p : properties)
        for (String parent : p.parents()) parents.add(p.kind() + "\n" + parent);
      for (var v : properties) {
        long id;
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO property_index(acronym,iri,kind,label,obsolete,has_children)"
                    + " VALUES(?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
          SnapshotProperties.bind(
              p,
              List.of(
                  acronym,
                  v.iri(),
                  v.kind(),
                  v.label(),
                  v.obsolete(),
                  parents.contains(v.kind() + "\n" + v.iri())));
          p.executeUpdate();
          try (ResultSet r = p.getGeneratedKeys()) {
            r.next();
            id = r.getLong(1);
          }
        }
        try (PreparedStatement p =
            c.prepareStatement(
                "INSERT INTO property_index_fts(rowid,label,iri,names) VALUES(?,?,?,?)")) {
          SnapshotProperties.bind(p, List.of(id, v.label(), v.iri(), SnapshotProperties.names(v)));
          p.executeUpdate();
        }
      }
      try (PreparedStatement p =
          c.prepareStatement(
              "INSERT INTO property_indexed_snapshot VALUES(?,?) ON CONFLICT(acronym) DO UPDATE SET"
                  + " version=excluded.version")) {
        p.setString(1, acronym);
        p.setString(2, version);
        p.executeUpdate();
      }
      c.commit();
    } catch (SQLException | RuntimeException e) {
      c.rollback();
      throw e;
    } finally {
      c.setAutoCommit(auto);
    }
  }

  public Page search(String query, List<String> acronyms, List<String> kinds, int offset, int limit)
      throws SQLException {
    SnapshotProperties.validateKinds(kinds);
    String tokens = SnapshotProperties.queryTokens(query);
    if (!available())
      throw new IllegalStateException("The property search index has not been built");
    if (acronyms.isEmpty()) return new Page(0, List.of());
    List<Object> args = new ArrayList<>(List.of(tokens, query.trim()));
    String where =
        " WHERE (p.id IN(SELECT rowid FROM property_index_fts WHERE property_index_fts MATCH ?) OR"
            + " p.iri=?) AND p.acronym IN ("
            + String.join(",", Collections.nCopies(acronyms.size(), "?"))
            + ")";
    args.addAll(acronyms);
    if (!kinds.isEmpty()) {
      where += " AND p.kind IN (" + String.join(",", Collections.nCopies(kinds.size(), "?")) + ")";
      args.addAll(kinds);
    }
    Connection c = connection();
    boolean auto = c.getAutoCommit();
    c.setAutoCommit(false);
    try {
      long total;
      try (PreparedStatement p =
          c.prepareStatement("SELECT count(*) FROM property_index p" + where)) {
        SnapshotProperties.bind(p, args);
        try (ResultSet r = p.executeQuery()) {
          r.next();
          total = r.getLong(1);
        }
      }
      List<Hit> items = new ArrayList<>();
      args.add(limit);
      args.add(offset);
      try (PreparedStatement p =
          c.prepareStatement(
              "SELECT p.*,v.version FROM property_index p JOIN property_indexed_snapshot v ON"
                  + " v.acronym=p.acronym"
                  + where
                  + " ORDER BY lower(p.label),p.iri,p.acronym,p.kind LIMIT ? OFFSET ?")) {
        SnapshotProperties.bind(p, args);
        try (ResultSet r = p.executeQuery()) {
          while (r.next())
            items.add(
                new Hit(
                    r.getString("acronym"), r.getString("version"), SnapshotProperties.summary(r)));
        }
      }
      c.commit();
      return new Page(total, items);
    } catch (SQLException | RuntimeException e) {
      c.rollback();
      throw e;
    } finally {
      c.setAutoCommit(auto);
    }
  }
}
