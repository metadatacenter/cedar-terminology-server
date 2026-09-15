package org.metadatacenter.terms.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Property tables owned by an ontology's SnapshotStore; never opens a separate database. */
public final class SnapshotProperties {
  public record Literal(String predicate, String lang, String value) {}

  public record Property(
      String iri,
      String kind,
      String label,
      boolean obsolete,
      List<Literal> literals,
      List<String> parents) {}

  public record Summary(
      String iri, String kind, String label, boolean obsolete, boolean hasChildren) {}

  public record Page(long total, List<Summary> items) {}

  @FunctionalInterface
  interface ConnectionSource {
    Connection get() throws SQLException;
  }

  private final ConnectionSource source;

  SnapshotProperties(ConnectionSource source) {
    this.source = source;
  }

  private Connection connection() throws SQLException {
    return source.get();
  }

  public void initSchema() throws SQLException {
    try (Statement s = connection().createStatement()) {
      s.execute(
          "CREATE TABLE IF NOT EXISTS ontology_property(id INTEGER PRIMARY KEY, iri TEXT NOT NULL,"
              + " kind TEXT NOT NULL, label TEXT NOT NULL, obsolete INTEGER NOT NULL,"
              + " UNIQUE(iri,kind))");
      s.execute(
          "CREATE TABLE IF NOT EXISTS property_literal(property_id INTEGER, predicate TEXT, lang"
              + " TEXT, value TEXT, PRIMARY KEY(property_id,predicate,lang,value))");
      s.execute(
          "CREATE TABLE IF NOT EXISTS property_parent(property_id INTEGER, parent TEXT, PRIMARY"
              + " KEY(property_id,parent))");
      s.execute("CREATE INDEX IF NOT EXISTS property_parent_iri ON property_parent(parent)");
      s.execute(
          "CREATE VIRTUAL TABLE IF NOT EXISTS property_fts USING"
              + " fts5(label,iri,names,tokenize='unicode61 remove_diacritics 2')");
    }
  }

  /** An old snapshot with no extraction is not a release with zero properties. */
  public boolean available() throws SQLException {
    try (Statement s = connection().createStatement();
        ResultSet r =
            s.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND"
                    + " name='property_extraction'")) {
      return r.next();
    }
  }

  public void write(List<Property> properties) throws SQLException {
    if (available())
      throw new IllegalStateException(
          "Properties are already extracted; create a new snapshot to replace them");
    initSchema();
    Connection c = connection();
    boolean auto = c.getAutoCommit();
    c.setAutoCommit(false);
    try {
      for (Property p : properties) insert(c, p);
      try (Statement s = c.createStatement()) {
        s.execute("CREATE TABLE property_extraction(schema_version INTEGER NOT NULL)");
        s.execute("INSERT INTO property_extraction VALUES(1)");
      }
      c.commit();
    } catch (SQLException | RuntimeException e) {
      c.rollback();
      throw e;
    } finally {
      c.setAutoCommit(auto);
    }
  }

  private void insert(Connection c, Property v) throws SQLException {
    long id;
    try (PreparedStatement p =
        c.prepareStatement(
            "INSERT INTO ontology_property(iri,kind,label,obsolete) VALUES(?,?,?,?)",
            Statement.RETURN_GENERATED_KEYS)) {
      p.setString(1, v.iri());
      p.setString(2, v.kind());
      p.setString(3, v.label());
      p.setBoolean(4, v.obsolete());
      p.executeUpdate();
      try (ResultSet r = p.getGeneratedKeys()) {
        r.next();
        id = r.getLong(1);
      }
    }
    try (PreparedStatement p =
        c.prepareStatement("INSERT OR IGNORE INTO property_literal VALUES(?,?,?,?)")) {
      for (Literal l : v.literals()) {
        p.setLong(1, id);
        p.setString(2, l.predicate());
        p.setString(3, l.lang());
        p.setString(4, l.value());
        p.addBatch();
      }
      p.executeBatch();
    }
    try (PreparedStatement p =
        c.prepareStatement("INSERT OR IGNORE INTO property_parent VALUES(?,?)")) {
      for (String parent : v.parents()) {
        p.setLong(1, id);
        p.setString(2, parent);
        p.addBatch();
      }
      p.executeBatch();
    }
    try (PreparedStatement p =
        c.prepareStatement("INSERT INTO property_fts(rowid,label,iri,names) VALUES(?,?,?,?)")) {
      p.setLong(1, id);
      p.setString(2, v.label());
      p.setString(3, v.iri());
      p.setString(4, names(v));
      p.executeUpdate();
    }
  }

  public static String names(Property p) {
    return String.join(
        " ", p.literals().stream().filter(l -> isName(l.predicate())).map(Literal::value).toList());
  }

  public static boolean isName(String iri) {
    return iri.equals("http://www.w3.org/2000/01/rdf-schema#label")
        || iri.equals("http://www.w3.org/2004/02/skos/core#prefLabel")
        || iri.equals("http://www.w3.org/2004/02/skos/core#altLabel")
        || iri.equals("http://www.w3.org/2004/02/skos/core#hiddenLabel")
        || iri.endsWith("hasExactSynonym")
        || iri.endsWith("hasRelatedSynonym");
  }

  private static void hash(MessageDigest md, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    md.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
    md.update(bytes);
  }

  /** Adds properties to the ontology content hash, including zero-property extraction coverage. */
  public void updateHash(MessageDigest md) throws SQLException {
    if (!available()) return; // Preserve identifiers of pre-property snapshots byte-for-byte.
    hash(md, "CEDAR-ontology-properties-v1");
    for (Property p : all()) {
      hash(md, "property");
      hash(md, p.iri());
      hash(md, p.kind());
      hash(md, p.label());
      hash(md, Boolean.toString(p.obsolete()));
      for (Literal l : p.literals()) {
        hash(md, "literal");
        hash(md, l.predicate());
        hash(md, l.lang());
        hash(md, l.value());
      }
      for (String parent : p.parents()) {
        hash(md, "parent");
        hash(md, parent);
      }
    }
  }

  public long count() throws SQLException {
    if (!available()) return 0;
    try (Statement s = connection().createStatement();
        ResultSet r = s.executeQuery("SELECT count(*) FROM ontology_property")) {
      r.next();
      return r.getLong(1);
    }
  }

  public List<Property> all() throws SQLException {
    if (!available()) return List.of();
    List<Property> out = new ArrayList<>();
    try (Statement s = connection().createStatement();
        ResultSet r = s.executeQuery("SELECT * FROM ontology_property ORDER BY iri,kind")) {
      while (r.next()) out.add(readProperty(r));
    }
    return out;
  }

  private Property readProperty(ResultSet r) throws SQLException {
    long id = r.getLong("id");
    List<Literal> literals = new ArrayList<>();
    List<String> parents = new ArrayList<>();
    try (PreparedStatement p =
        connection()
            .prepareStatement(
                "SELECT * FROM property_literal WHERE property_id=? ORDER BY"
                    + " predicate,lang,value")) {
      p.setLong(1, id);
      try (ResultSet rows = p.executeQuery()) {
        while (rows.next())
          literals.add(
              new Literal(
                  rows.getString("predicate"), rows.getString("lang"), rows.getString("value")));
      }
    }
    try (PreparedStatement p =
        connection()
            .prepareStatement(
                "SELECT parent FROM property_parent WHERE property_id=? ORDER BY parent")) {
      p.setLong(1, id);
      try (ResultSet rows = p.executeQuery()) {
        while (rows.next()) parents.add(rows.getString(1));
      }
    }
    return new Property(
        r.getString("iri"),
        r.getString("kind"),
        r.getString("label"),
        r.getBoolean("obsolete"),
        literals,
        parents);
  }

  public Optional<Property> property(String iri, String kind) throws SQLException {
    if (!available())
      throw new IllegalStateException("Properties were not extracted for this ontology version");
    try (PreparedStatement p =
        connection().prepareStatement("SELECT * FROM ontology_property WHERE iri=? AND kind=?")) {
      p.setString(1, iri);
      p.setString(2, kind);
      try (ResultSet r = p.executeQuery()) {
        return r.next() ? Optional.of(readProperty(r)) : Optional.empty();
      }
    }
  }

  static Summary summary(ResultSet r) throws SQLException {
    return new Summary(
        r.getString("iri"),
        r.getString("kind"),
        r.getString("label"),
        r.getBoolean("obsolete"),
        r.getBoolean("has_children"));
  }

  private static final String SELECT =
      "SELECT p.*,EXISTS(SELECT 1 FROM property_parent e JOIN ontology_property child ON"
          + " child.id=e.property_id WHERE e.parent=p.iri AND child.kind=p.kind) AS has_children"
          + " FROM ontology_property p";

  public static String queryTokens(String query) {
    if (query == null || query.isBlank() || query.length() > 500)
      throw new IllegalArgumentException("A property search needs a query of 1–500 characters");
    String tokens =
        Arrays.stream(query.trim().split("[^\\p{L}\\p{N}]+"))
            .filter(t -> !t.isEmpty())
            .map(t -> "\"" + t + "\"*")
            .collect(java.util.stream.Collectors.joining(" AND "));
    if (tokens.isEmpty()) throw new IllegalArgumentException("The query needs a letter or number");
    return tokens;
  }

  public static void validateKinds(List<String> kinds) {
    for (String kind : kinds)
      if (kind == null || !Set.of("object", "datatype", "annotation").contains(kind))
        throw new IllegalArgumentException("Unknown property kind: " + kind);
  }

  public Page search(String query, List<String> kinds, int offset, int limit) throws SQLException {
    if (!available())
      throw new IllegalStateException("Properties were not extracted for this ontology version");
    if (offset < 0 || limit < 1 || limit > 200)
      throw new IllegalArgumentException("Invalid search page");
    validateKinds(kinds);
    List<Object> args = new ArrayList<>(List.of(queryTokens(query), query.trim()));
    String where =
        " WHERE (p.id IN (SELECT rowid FROM property_fts WHERE property_fts MATCH ?) OR p.iri=?)";
    if (!kinds.isEmpty()) {
      where += " AND p.kind IN (" + String.join(",", Collections.nCopies(kinds.size(), "?")) + ")";
      args.addAll(kinds);
    }
    long total;
    try (PreparedStatement p =
        connection().prepareStatement("SELECT count(*) FROM ontology_property p" + where)) {
      bind(p, args);
      try (ResultSet r = p.executeQuery()) {
        r.next();
        total = r.getLong(1);
      }
    }
    args.add(limit);
    args.add(offset);
    return new Page(
        total,
        read(SELECT + where + " ORDER BY lower(p.label),p.iri,p.kind LIMIT ? OFFSET ?", args));
  }

  static void bind(PreparedStatement p, List<?> args) throws SQLException {
    for (int i = 0; i < args.size(); i++) p.setObject(i + 1, args.get(i));
  }

  private List<Summary> read(String sql, List<?> args) throws SQLException {
    List<Summary> out = new ArrayList<>();
    try (PreparedStatement p = connection().prepareStatement(sql)) {
      bind(p, args);
      try (ResultSet r = p.executeQuery()) {
        while (r.next()) out.add(summary(r));
      }
    }
    return out;
  }

  public List<Summary> children(String iri, String kind, int offset, int limit)
      throws SQLException {
    if (offset < 0 || limit < 1 || limit > 200)
      throw new IllegalArgumentException("Invalid child page");
    return read(
        SELECT
            + " WHERE p.kind=? AND EXISTS(SELECT 1 FROM property_parent e WHERE e.property_id=p.id"
            + " AND e.parent=?) ORDER BY lower(p.label),p.iri LIMIT ? OFFSET ?",
        List.of(kind, iri, limit, offset));
  }

  /** Full ancestor graph, retaining multiple parents. UNION terminates cycles in source data. */
  public List<Summary> ancestors(String iri, String kind) throws SQLException {
    return read(
        "WITH RECURSIVE ancestors(iri) AS (SELECT ? UNION SELECT e.parent FROM ancestors a JOIN"
            + " ontology_property p ON p.iri=a.iri JOIN property_parent e ON e.property_id=p.id"
            + " WHERE p.kind=?) "
            + SELECT
            + " JOIN ancestors a ON a.iri=p.iri WHERE p.kind=? AND p.iri<>? ORDER BY"
            + " lower(p.label),p.iri",
        List.of(iri, kind, kind, iri));
  }

  public List<Summary> roots(String kind, int offset, int limit) throws SQLException {
    if (offset < 0 || limit < 1 || limit > 200)
      throw new IllegalArgumentException("Invalid root page");
    return read(
        SELECT
            + " WHERE p.kind=? AND NOT EXISTS(SELECT 1 FROM property_parent e JOIN"
            + " ontology_property parent ON parent.iri=e.parent AND parent.kind=p.kind WHERE"
            + " e.property_id=p.id) ORDER BY lower(p.label),p.iri LIMIT ? OFFSET ?",
        List.of(kind, limit, offset));
  }
}
