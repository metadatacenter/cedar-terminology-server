package org.metadatacenter.terms.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  /** A concept's cross-version identity metadata: IRI, obsolete flag, and replacement IRI if any. */
  public record ConceptMeta(String iri, boolean obsolete, String replacedBy) {}

  /** Every concept field that participates in label-sensitive normalized content identity. */
  public record ConceptState(String iri, String prefLabel, boolean obsolete, String replacedBy) {}

  /** One captured name literal to insert: the concept it names, the property CURIE (e.g.
   *  {@code skos:prefLabel}), the BCP-47 language tag ({@code ""} = untagged), and the value. */
  public record LabelRow(String conceptIri, String property, String lang, String value) {}

  /** One captured name literal read back, without the concept IRI (the caller keyed the query on it). */
  public record LabelEntry(String property, String lang, String value) {}

  /** A definition literal to capture, addressed by the concept's IRI. */
  public record DefinitionRow(String conceptIri, String property, String lang, String value) {}

  /** One captured definition read back, without the concept IRI. */
  public record DefinitionEntry(String property, String lang, String value) {}

  private final Connection connection;
  private Boolean labelTablePresent; // cached: a snapshot ingested before label capture has no label table

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
            id             INTEGER PRIMARY KEY,
            iri            TEXT NOT NULL UNIQUE,
            pref_label     TEXT,
            obsolete       INTEGER NOT NULL DEFAULT 0,
            replaced_by    TEXT
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
            PRIMARY KEY (ancestor_id, descendant_id)
          ) WITHOUT ROWID""");
      s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_closure_desc ON closure(descendant_id)");
      s.executeUpdate("CREATE TABLE IF NOT EXISTS root (concept_id INTEGER PRIMARY KEY)");
      // Level-1 typed relations: non-hierarchical object relations retained for compositional
      // vocabularies (e.g. RxNorm has_ingredient / has_dose_form), for filtered expansion.
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS relation (
            subject_id INTEGER NOT NULL,
            predicate  TEXT NOT NULL,
            object_id  INTEGER NOT NULL,
            PRIMARY KEY (subject_id, predicate, object_id)
          ) WITHOUT ROWID""");
      s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_relation_obj ON relation(predicate, object_id)");
      // Every language variant of a concept's names — its labels and synonyms — with the BCP-47
      // language tag ('' = untagged, BioPortal's "none"). The served pref_label keeps the single
      // best-ranked label; this table preserves the rest so a multilingual ontology is not collapsed
      // to one language. Outside content identity by construction: normalizedContentHash never reads
      // it. CREATE ... IF NOT EXISTS, so opening a pre-existing snapshot migrates it to an empty table
      // that the label backfill then fills.
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS label (
            concept_id INTEGER NOT NULL,
            property   TEXT NOT NULL,
            lang       TEXT NOT NULL DEFAULT '',
            value      TEXT NOT NULL,
            PRIMARY KEY (concept_id, property, lang, value)
          ) WITHOUT ROWID""");
      // What a source says a concept means, with the property it was asserted under and its language
      // tag. Several are allowed: a term can carry a definition and an alternative one, and which
      // property it came from is the difference between a definition and an editor's note.
      //
      // Outside content identity, exactly as the label table is and for the same reason: a snapshot
      // is identified by a hash over its concepts, labels and edges, so admitting definitions to it
      // would change the identity of every snapshot already written and break every pin with it.
      s.executeUpdate("""
          CREATE TABLE IF NOT EXISTS definition (
            concept_id INTEGER NOT NULL,
            property   TEXT NOT NULL,
            lang       TEXT NOT NULL DEFAULT '',
            value      TEXT NOT NULL,
            PRIMARY KEY (concept_id, property, lang, value)
          ) WITHOUT ROWID""");
      // Small key/value provenance for the snapshot itself (e.g. a marker that label backfill has run,
      // so a resume skips a snapshot even when it legitimately has zero real labels). Outside identity.
      s.executeUpdate("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)");
    }
  }

  /* --------------------------------------------------------------------------------------------
   * Ingestion
   * ------------------------------------------------------------------------------------------ */

  /** Adds an active concept. Idempotent on IRI; a repeated IRI is ignored. */
  public void addConcept(String iri, String prefLabel) throws SQLException {
    addConcept(iri, prefLabel, false, null);
  }

  /**
   * Adds a concept, recording whether it is obsolete (deprecated) and, if so, the IRI of the
   * concept that replaces it (from an OBO {@code term replaced by} annotation, when present).
   * Idempotent on IRI.
   */
  public void addConcept(String iri, String prefLabel, boolean obsolete, String replacedBy) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "INSERT OR IGNORE INTO concept (iri, pref_label, obsolete, replaced_by) VALUES (?, ?, ?, ?)")) {
      ps.setString(1, iri);
      ps.setString(2, prefLabel);
      ps.setInt(3, obsolete ? 1 : 0);
      ps.setString(4, replacedBy);
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
   * Adds a non-hierarchical typed relation {@code subject --predicate--> object} (e.g.
   * {@code drug has_ingredient ingredient}). Both endpoints must already be concepts; a relation to
   * a non-concept is silently ignored.
   */
  public void addRelation(String subjectIri, String predicate, String objectIri) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        INSERT OR IGNORE INTO relation (subject_id, predicate, object_id)
        SELECT s.id, ?, o.id FROM concept s, concept o WHERE s.iri = ? AND o.iri = ?""")) {
      ps.setString(1, predicate);
      ps.setString(2, subjectIri);
      ps.setString(3, objectIri);
      ps.executeUpdate();
    }
  }

  /**
   * Bulk-adds typed relations, each as {@code [subjectIri, predicate, objectIri]}, in a single
   * transaction. Relations whose endpoints are not both concepts are ignored.
   */
  public void addRelations(List<String[]> triples) throws SQLException {
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try (PreparedStatement ps = connection.prepareStatement("""
        INSERT OR IGNORE INTO relation (subject_id, predicate, object_id)
        SELECT s.id, ?, o.id FROM concept s, concept o WHERE s.iri = ? AND o.iri = ?""")) {
      for (String[] t : triples) {
        ps.setString(1, t[1]);
        ps.setString(2, t[0]);
        ps.setString(3, t[2]);
        ps.addBatch();
      }
      ps.executeBatch();
      connection.commit();
    } finally {
      connection.setAutoCommit(autoCommit);
    }
  }

  /** How long a write waits for a lock held by another connection (e.g. a live server reading this
   *  snapshot) before failing, instead of erroring immediately with SQLITE_BUSY. Lets a backfill write
   *  labels into a snapshot the server is serving. */
  public void setBusyTimeoutMillis(int millis) throws SQLException {
    try (Statement s = connection.createStatement()) {
      s.execute("PRAGMA busy_timeout = " + millis);
    }
  }

  /**
   * Bulk-adds captured name literals in a single transaction. Each row names an existing concept by
   * IRI; a row whose concept IRI is not in the store is silently ignored (matches {@link #addEdge}).
   * Idempotent — duplicate rows are dropped by the primary key. Outside content identity.
   */
  public void addLabels(List<LabelRow> rows) throws SQLException {
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try (PreparedStatement ps = connection.prepareStatement("""
        INSERT OR IGNORE INTO label (concept_id, property, lang, value)
        SELECT c.id, ?, ?, ? FROM concept c WHERE c.iri = ?""")) {
      for (LabelRow r : rows) {
        ps.setString(1, r.property());
        ps.setString(2, r.lang() == null ? "" : r.lang());
        ps.setString(3, r.value());
        ps.setString(4, r.conceptIri());
        ps.addBatch();
      }
      ps.executeBatch();
      connection.commit();
    } finally {
      connection.setAutoCommit(autoCommit);
    }
  }

  /** Every captured name literal for one concept, ordered by property then language. */
  public List<LabelEntry> labels(String conceptIri) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT l.property, l.lang, l.value FROM label l JOIN concept c ON c.id = l.concept_id "
            + "WHERE c.iri = ? ORDER BY l.property, l.lang, l.value")) {
      ps.setString(1, conceptIri);
      try (ResultSet rs = ps.executeQuery()) {
        List<LabelEntry> out = new ArrayList<>();
        while (rs.next()) {
          out.add(new LabelEntry(rs.getString("property"), rs.getString("lang"), rs.getString("value")));
        }
        return out;
      }
    }
  }

  /**
   * Captures definitions, ignoring any whose concept is not in this snapshot.
   *
   * Insert-or-ignore on the whole row, so a definition asserted twice under one property collapses
   * and a backfill can be run again without duplicating what it already wrote.
   */
  public void addDefinitions(List<DefinitionRow> rows) throws SQLException {
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try (PreparedStatement ps = connection.prepareStatement("""
        INSERT OR IGNORE INTO definition (concept_id, property, lang, value)
        SELECT c.id, ?, ?, ? FROM concept c WHERE c.iri = ?""")) {
      for (DefinitionRow r : rows) {
        ps.setString(1, r.property());
        ps.setString(2, r.lang() == null ? "" : r.lang());
        ps.setString(3, r.value());
        ps.setString(4, r.conceptIri());
        ps.addBatch();
      }
      ps.executeBatch();
      connection.commit();
    } finally {
      connection.setAutoCommit(autoCommit);
    }
  }

  /** Every definition a concept carries, or empty where the snapshot predates the capture. */
  public List<DefinitionEntry> definitions(String conceptIri) throws SQLException {
    if (!hasTable("definition")) {
      return List.of();
    }
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT d.property, d.lang, d.value FROM definition d JOIN concept c ON c.id = d.concept_id "
            + "WHERE c.iri = ? ORDER BY d.property, d.lang, d.value")) {
      ps.setString(1, conceptIri);
      try (ResultSet rs = ps.executeQuery()) {
        List<DefinitionEntry> out = new ArrayList<>();
        while (rs.next()) {
          out.add(new DefinitionEntry(rs.getString("property"), rs.getString("lang"), rs.getString("value")));
        }
        return out;
      }
    }
  }

  /** How many definitions this snapshot holds, for a backfill to report and to resume by. */
  public int definitionCount() throws SQLException {
    if (!hasTable("definition")) {
      return 0;
    }
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM definition")) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  private boolean hasTable(String name) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
      ps.setString(1, name);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  /** The synonym-scope property CURIEs — every captured name that is a synonym rather than the label
   *  proper. Kept in sync with the ingest-side capture (LabelProperties); the label table stores CURIEs. */
  private static final String SYNONYM_PROPERTY_LIST =
      "'skos:altLabel','skos:hiddenLabel','oboInOwl:hasExactSynonym','oboInOwl:hasRelatedSynonym',"
          + "'oboInOwl:hasBroadSynonym','oboInOwl:hasNarrowSynonym','oboInOwl:hasSynonym'";

  /** Whether this snapshot has a {@code label} table. A snapshot ingested before multilingual capture
   *  (and never backfilled) has none; the read paths that consult labels must tolerate its absence rather
   *  than fail with "no such table: label". Cached per connection. */
  private boolean hasLabelTable() throws SQLException {
    if (labelTablePresent == null) {
      try (Statement s = connection.createStatement();
           ResultSet rs = s.executeQuery(
               "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'label'")) {
        labelTablePresent = rs.next();
      }
    }
    return labelTablePresent;
  }

  /** The concept's label in a requested BCP-47 language, or empty if it has none in that language (the
   *  caller falls back to the served {@code pref_label}). Matches the exact tag or a regional variant
   *  ({@code fr} matches {@code fr-CA}), preferring an exact tag and {@code rdfs:label} over
   *  {@code skos:prefLabel}. A null/blank language yields empty. */
  public Optional<String> labelInLang(String conceptIri, String lang) throws SQLException {
    if (lang == null || lang.isBlank() || !hasLabelTable()) {
      return Optional.empty();
    }
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT l.value FROM label l JOIN concept c ON c.id = l.concept_id "
            + "WHERE c.iri = ? AND l.property IN ('rdfs:label','skos:prefLabel') "
            + "AND (LOWER(l.lang) = LOWER(?) OR LOWER(l.lang) LIKE LOWER(?)) "
            + "ORDER BY (CASE WHEN LOWER(l.lang) = LOWER(?) THEN 0 ELSE 1 END), "
            + "(CASE l.property WHEN 'rdfs:label' THEN 0 ELSE 1 END), l.value LIMIT 1")) {
      ps.setString(1, conceptIri);
      ps.setString(2, lang);
      ps.setString(3, lang + "-%");
      ps.setString(4, lang);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
      }
    }
  }

  /** A concept's synonyms — the captured altLabels and OBO synonym scopes, distinct values across all
   *  languages, ordered. The label proper ({@code rdfs:label}/{@code skos:prefLabel}) is excluded. */
  public List<String> synonyms(String conceptIri) throws SQLException {
    if (!hasLabelTable()) {
      return new ArrayList<>();
    }
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT DISTINCT l.value FROM label l JOIN concept c ON c.id = l.concept_id "
            + "WHERE c.iri = ? AND l.property IN (" + SYNONYM_PROPERTY_LIST + ") ORDER BY l.value")) {
      ps.setString(1, conceptIri);
      try (ResultSet rs = ps.executeQuery()) {
        List<String> out = new ArrayList<>();
        while (rs.next()) {
          out.add(rs.getString(1));
        }
        return out;
      }
    }
  }

  /** Every captured name literal in the snapshot, as insertable rows (concept IRI carried). Used to
   *  copy a freshly-extracted snapshot's labels into an existing snapshot file during backfill. */
  public List<LabelRow> allLabels() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery(
             "SELECT c.iri, l.property, l.lang, l.value FROM label l "
                 + "JOIN concept c ON c.id = l.concept_id")) {
      List<LabelRow> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new LabelRow(rs.getString("iri"), rs.getString("property"),
            rs.getString("lang"), rs.getString("value")));
      }
      return out;
    }
  }

  /** Every captured definition in the snapshot, for a bulk pass such as building the index. */
  public List<DefinitionRow> allDefinitions() throws SQLException {
    if (!hasTable("definition")) {
      return List.of();
    }
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery(
             "SELECT c.iri, d.property, d.lang, d.value FROM definition d "
                 + "JOIN concept c ON c.id = d.concept_id")) {
      List<DefinitionRow> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new DefinitionRow(rs.getString("iri"), rs.getString("property"),
            rs.getString("lang"), rs.getString("value")));
      }
      return out;
    }
  }

  /**
   * Which of a concept's definitions to serve, where it has more than one.
   *
   * A term can carry a definition under several properties and in several languages, and a row has
   * room for one. English first, because that is the language the rest of the picker serves; then
   * the property, a definition proper ahead of an alternative one — NCIT's ALT_DEFINITION is a
   * second reading for a different audience, not the primary sense.
   */
  public static String servedDefinition(List<DefinitionEntry> entries) {
    return entries.stream()
        .min(java.util.Comparator
            .comparingInt((DefinitionEntry e) -> languageRank(e.lang()))
            .thenComparingInt(e -> propertyRank(e.property()))
            .thenComparing(DefinitionEntry::value))
        .map(DefinitionEntry::value)
        .orElse(null);
  }

  private static int languageRank(String lang) {
    if (lang == null || lang.isEmpty()) {
      return 1;
    }
    return lang.toLowerCase().startsWith("en") ? 0 : 2;
  }

  private static int propertyRank(String property) {
    return switch (property) {
      case "IAO:0000115" -> 0;
      case "skos:definition" -> 1;
      case "NCIT:DEFINITION" -> 2;
      case "dcterms:description" -> 3;
      case "dc:description" -> 4;
      default -> 5;
    };
  }

  /** Total captured name literals. Zero means labels have not been captured for this snapshot yet
   *  (the backfill skip check). */
  public int labelCount() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM label")) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  /** Number of concepts in the snapshot. Queries the {@code concept} table directly (no schema
   *  creation), so it throws on a malformed or truncated store whose table is absent — which the
   *  integrity check treats as unreadable. */
  public long conceptCount() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM concept")) {
      return rs.next() ? rs.getLong(1) : 0;
    }
  }

  /** Sets a snapshot-level provenance value (see the {@code meta} table). */
  public void setMeta(String key, String value) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "INSERT INTO meta (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
      ps.setString(1, key);
      ps.setString(2, value);
      ps.executeUpdate();
    }
  }

  /** A snapshot-level provenance value, or empty if unset. */
  public java.util.Optional<String> getMeta(String key) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
      ps.setString(1, key);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? java.util.Optional.ofNullable(rs.getString(1)) : java.util.Optional.empty();
      }
    }
  }

  /**
   * Computes the transitive closure from the asserted edges and records the roots (concepts with
   * no parent). Run once after all concepts and edges are ingested. Assumes an acyclic hierarchy.
   */
  public void materialize() throws SQLException {
    try (Statement s = connection.createStatement()) {
      s.executeUpdate("DELETE FROM closure");
      // Cycle-safe: UNION over (ancestor, descendant) pairs terminates once no new pair appears,
      // even if the asserted hierarchy contains a cycle (a cyclic node ends up as its own
      // ancestor/descendant, which callers can detect). Depth is not tracked (it was never read and
      // its ever-increasing values made the recursion non-terminating on cycles).
      s.executeUpdate("""
          INSERT INTO closure (ancestor_id, descendant_id)
          WITH RECURSIVE walk(ancestor_id, descendant_id) AS (
              SELECT parent_id, child_id FROM edge
            UNION
              SELECT w.ancestor_id, e.child_id
              FROM walk w JOIN edge e ON e.parent_id = w.descendant_id
          )
          SELECT ancestor_id, descendant_id FROM walk""");
      s.executeUpdate("DELETE FROM root");
      // A root is a non-obsolete class with no named parent. This matches BioPortal's /classes/roots
      // for hierarchical ontologies: verified against the goldens, the parentless set reproduces
      // BioPortal's roots exactly for DOID (15), and the top classes are parentless in the source
      // rather than explicit owl:Thing subclasses (doid.owl asserts no owl:Thing at all). The earlier
      // rule keyed on an explicit owl:Thing declaration and so dropped every parentless top class that
      // did not assert it -- the picker's tree then had no entry point (DOID returned evidence and
      // sequence, not disease). owl:Thing is never materialized, so a class asserting subClassOf
      // owl:Thing is simply parentless here and is included. Obsolete classes are never roots.
      s.executeUpdate("""
          INSERT INTO root (concept_id)
          SELECT c.id FROM concept c
          WHERE c.obsolete = 0
            AND NOT EXISTS (SELECT 1 FROM edge e WHERE e.child_id = c.id)""");
    }
  }

  /**
   * A content hash of the normalized extracted model, independent of the source file's bytes and
   * serialization (VERSIONING-ROADMAP "The Model" §4.3). Two snapshots with the same served content hash to the
   * same value even when they came from different serializations (OBO vs OWL) or backends; a genuine
   * content change gives a different hash. This is the alternative to the raw-file hash that today's
   * {@code version_id} uses.
   *
   * The canonical form is built over IRIs (stable, source-independent), never internal row ids:
   * <ul>
   *   <li>every concept, sorted by IRI, as {@code C<TAB>iri<TAB>obsolete} — and, when
   *       {@code includeLabels}, also {@code <TAB>prefLabel<TAB>replacedBy};</li>
   *   <li>every subsumption edge, sorted, as {@code E<TAB>childIri<TAB>parentIri<TAB>sourcePred};</li>
   *   <li>every typed relation, sorted, as {@code R<TAB>subjectIri<TAB>predicate<TAB>objectIri}.</li>
   * </ul>
   * Edges and relations are structure and are always included; {@code includeLabels} is the one open
   * knob (§4.3) — structure-only identity vs identity that also moves when a display label changes.
   * Section tags (C/E/R) prevent cross-section collisions; the binary default collation makes the
   * SQL ordering deterministic.
   */
  public String normalizedContentHash(boolean includeLabels) throws SQLException {
    java.security.MessageDigest md;
    try {
      md = java.security.MessageDigest.getInstance("SHA-256");
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e); // guaranteed present on every JVM
    }
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery(
             "SELECT iri, pref_label, obsolete, replaced_by FROM concept ORDER BY iri")) {
      while (rs.next()) {
        StringBuilder line = new StringBuilder("C\t").append(rs.getString("iri"))
            .append('\t').append(rs.getInt("obsolete"));
        if (includeLabels) {
          line.append('\t').append(hashField(rs.getString("pref_label")))
              .append('\t').append(hashField(rs.getString("replaced_by")));
        }
        md.update(line.append('\n').toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
    }
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery(
             "SELECT c.iri AS child, p.iri AS parent, e.source_pred AS pred FROM edge e "
                 + "JOIN concept c ON c.id = e.child_id JOIN concept p ON p.id = e.parent_id "
                 + "ORDER BY child, parent, pred")) {
      while (rs.next()) {
        String line = "E\t" + rs.getString("child") + '\t' + rs.getString("parent")
            + '\t' + hashField(rs.getString("pred")) + '\n';
        md.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
    }
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery(
             "SELECT s.iri AS subj, r.predicate AS pred, o.iri AS obj FROM relation r "
                 + "JOIN concept s ON s.id = r.subject_id JOIN concept o ON o.id = r.object_id "
                 + "ORDER BY subj, pred, obj")) {
      while (rs.next()) {
        String line = "R\t" + rs.getString("subj") + '\t' + rs.getString("pred")
            + '\t' + rs.getString("obj") + '\n';
        md.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
    }
    byte[] digest = md.digest();
    StringBuilder hex = new StringBuilder(digest.length * 2);
    for (byte b : digest) {
      hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
    }
    return hex.toString();
  }

  /** Represents a null field with a NUL sentinel so it is distinct from an empty string in the hash
   *  stream (NUL never appears in an IRI or a well-formed label). */
  private static String hashField(String value) {
    return value == null ? "\u0000" : value;
  }

  private static final java.util.regex.Pattern OBO_ID =
      java.util.regex.Pattern.compile("^(.*/obo/)([A-Za-z][A-Za-z0-9]*)_");
  private static final java.util.regex.Pattern OBO_SPACE =
      java.util.regex.Pattern.compile(".*/obo/([A-Za-z][A-Za-z0-9]*)_$");

  /**
   * The ID-space of an IRI. OBO IDs collapse to {@code .../obo/<PREFIX>_} so that {@code PO_} and
   * {@code NCBITaxon_} stay distinct even though both sit under {@code .../obo/}; every other IRI
   * yields the namespace up to and including its {@code '#'} or its last {@code '/'}.
   */
  public static String idspace(String iri) {
    java.util.regex.Matcher m = OBO_ID.matcher(iri);
    if (m.lookingAt()) {
      return m.group(1) + m.group(2) + "_";
    }
    int hash = iri.indexOf('#');
    if (hash >= 0) {
      return iri.substring(0, hash + 1);
    }
    int slash = iri.lastIndexOf('/');
    return slash >= 0 ? iri.substring(0, slash + 1) : iri;
  }

  /**
   * The ontology's own ID-spaces, keyed to its acronym rather than to frequency — an import-heavy
   * ontology's concepts are mostly imported (CL is 40% GO_, 26% UBERON_, only 19% its own CL_), so
   * frequency cannot find "own". For OBO the own space is {@code .../obo/<ACRONYM>_}; for others it
   * is an ID-space whose namespace carries an acronym token. Falls back to the single most common
   * space only when the acronym matches nothing, so oddly-named ontologies stay servable.
   */
  private java.util.Set<String> ownIdspaces(String acronym) throws SQLException {
    java.util.Set<String> tokens = new java.util.HashSet<>();
    for (String t : acronym.split("[^A-Za-z0-9]+")) {
      if (!t.isEmpty()) {
        tokens.add(t.toUpperCase());
      }
    }
    java.util.Map<String, Integer> freq = new java.util.HashMap<>();
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT iri FROM concept")) {
      while (rs.next()) {
        freq.merge(idspace(rs.getString(1)), 1, Integer::sum);
      }
    }
    java.util.Set<String> own = new java.util.HashSet<>();
    for (String sp : freq.keySet()) {
      java.util.regex.Matcher m = OBO_SPACE.matcher(sp);
      if (m.matches()) {
        if (tokens.contains(m.group(1).toUpperCase())) {
          own.add(sp);
        }
      } else {
        String up = sp.toUpperCase();
        for (String t : tokens) {
          if (t.length() >= 3 && up.contains(t)) {
            own.add(sp);
            break;
          }
        }
      }
    }
    if (own.isEmpty() && !freq.isEmpty()) {
      // Acronym matched no namespace: fall back to the dominant namespace AMONG NON-IMPORTS, so a
      // mostly-imported ontology's own (minority) namespace is not overridden by an import it pulls
      // in wholesale (e.g. NIF-Dysfunction is 34% GO / 31% PATO / 18% UBERON, its own nifstd only 4%).
      java.util.Map<String, Integer> pool = new java.util.HashMap<>();
      for (var e : freq.entrySet()) {
        if (!isImportSpace(e.getKey())) {
          pool.put(e.getKey(), e.getValue());
        }
      }
      if (pool.isEmpty()) {
        pool = freq; // pure aggregator — nothing but imports; keep the plain dominant
      }
      own.add(java.util.Collections.max(pool.entrySet(), java.util.Map.Entry.comparingByValue()).getKey());
    }
    return own;
  }

  /**
   * The ontology's single dominant own ID-space: the most frequent among {@link #ownIdspaces}, or
   * empty when the snapshot holds no concepts. This is the raw term-ID namespace (trailing separator
   * intact — {@code .../obo/DOID_}, {@code .../ontology/MESH/}) from which the canonical ontology IRI
   * is normalized ({@link OntologyIri#canonical}). Acronym-keyed like {@link #ownIdspaces}, so an
   * import-heavy ontology resolves to its own space (OBI → {@code obo/OBI_}), not a bulk-imported one
   * (OBI's most frequent concept space is {@code obo/CHEBI_}).
   */
  public java.util.Optional<String> dominantOwnIdspace(String acronym) throws SQLException {
    java.util.Set<String> own = ownIdspaces(acronym);
    if (own.isEmpty()) {
      return java.util.Optional.empty();
    }
    java.util.Map<String, Integer> freq = new java.util.HashMap<>();
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT iri FROM concept")) {
      while (rs.next()) {
        String sp = idspace(rs.getString(1));
        if (own.contains(sp)) {
          freq.merge(sp, 1, Integer::sum);
        }
      }
    }
    if (freq.isEmpty()) {
      // Own spaces were identified from the acronym but none was counted among concepts (e.g. the
      // fallback picked a space no concept sits directly in): keep resolution deterministic.
      return java.util.Optional.of(java.util.Collections.min(own));
    }
    return java.util.Optional.of(
        java.util.Collections.max(freq.entrySet(), java.util.Map.Entry.comparingByValue()).getKey());
  }

  /** Common imported / upper-reference ontologies and meta vocabularies — consulted only in the
   *  {@link #ownIdspaces} frequency fallback, to avoid mistaking imported content for the ontology's
   *  own. Never applied when the acronym itself matches a namespace, so gating one of these
   *  ontologies directly still resolves its own space. */
  private static final java.util.Set<String> IMPORT_OBO = java.util.Set.of(
      "BFO", "RO", "IAO", "BSPO", "GO", "CHEBI", "PATO", "NCBITAXON", "PR", "UBERON", "CL", "SO",
      "ENVO", "GAZ", "OGMS", "COB", "OMIM", "CARO", "OBA", "UO", "NBO", "MOP", "CHMO");
  private static final String[] IMPORT_HOST = {
      "W3.ORG", "XMLNS.COM/FOAF", "PURL.ORG/DC", "SKOS/CORE", "/2006/TIME", "SCHEMA.ORG", "ONTOLOGY/BIBO"};

  private static boolean isImportSpace(String sp) {
    java.util.regex.Matcher m = OBO_SPACE.matcher(sp);
    if (m.matches() && IMPORT_OBO.contains(m.group(1).toUpperCase())) {
      return true;
    }
    String up = sp.toUpperCase();
    for (String h : IMPORT_HOST) {
      if (up.contains(h)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Removes dead-end import references from the root set. A parentless class is not a real browse
   * root when it is unlabeled, lives in a foreign ID-space (one the ontology merely references, not
   * its own), and has no labeled descendant. Such a class is an unresolved-{@code owl:imports}
   * dangling reference: the ontology names it in an axiom but its defining ontology was not loaded,
   * so it arrives unlabeled and parentless and only looks like a root. BioPortal resolves the import
   * and roots it elsewhere; we drop it from the tree's entry points. The labeled-descendant guard
   * means a root that leads to real content is always kept, so no reachable class is ever hidden.
   * Leaves the concept and its edges intact (still resolvable by direct lookup); only the {@code
   * root} table changes. Idempotent. Returns the number of roots pruned.
   */
  public int pruneDeadEndImportRoots(String acronym) throws SQLException {
    java.util.Set<String> own = ownIdspaces(acronym);
    List<Long> unlabeledForeign = new ArrayList<>();
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery(
             "SELECT ci.id, ci.iri FROM root r JOIN concept ci ON ci.id = r.concept_id "
                 + "WHERE ci.pref_label IS NULL OR ci.pref_label = ''")) {
      while (rs.next()) {
        if (!own.contains(idspace(rs.getString(2)))) {
          unlabeledForeign.add(rs.getLong(1));
        }
      }
    }
    List<Long> victims = new ArrayList<>();
    try (PreparedStatement leadsToContent = connection.prepareStatement(
             "SELECT 1 FROM closure cl JOIN concept d ON d.id = cl.descendant_id "
                 + "WHERE cl.ancestor_id = ? AND d.pref_label IS NOT NULL AND d.pref_label <> '' LIMIT 1")) {
      for (long id : unlabeledForeign) {
        leadsToContent.setLong(1, id);
        try (ResultSet rs = leadsToContent.executeQuery()) {
          if (!rs.next()) {
            victims.add(id); // no labeled descendant — a dead-end dangling reference
          }
        }
      }
    }
    if (victims.isEmpty()) {
      return 0;
    }
    // Never prune an ontology to zero roots. A label-less but structured ontology (no rdfs:label
    // anywhere, so every root is unlabeled with no labeled descendant) would otherwise lose its
    // entire tree entry set and become unbrowsable; an unlabeled tree still beats none.
    int totalRoots;
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM root")) {
      rs.next();
      totalRoots = rs.getInt(1);
    }
    if (victims.size() >= totalRoots) {
      return 0;
    }
    try (PreparedStatement del = connection.prepareStatement("DELETE FROM root WHERE concept_id = ?")) {
      for (long id : victims) {
        del.setLong(1, id);
        del.addBatch();
      }
      del.executeBatch();
    }
    return victims.size();
  }

  /**
   * The fallback display label for a class that carries no {@code rdfs:label}/{@code skos:prefLabel}:
   * its IRI fragment (the substring after the last {@code #} or {@code /}), verbatim. This matches
   * what BioPortal serves for such classes exactly — e.g. {@code …#Alcoholic_Hallucinosis} →
   * {@code "Alcoholic_Hallucinosis"} (underscores kept, no CamelCase split, no decoding) — so a term
   * chosen here matches one BioPortal filled, and label-less ontologies stay consistent whether served
   * locally or from BioPortal. Returns null only for an empty fragment.
   */
  public static String labelFromIri(String iri) {
    if (iri == null) {
      return null;
    }
    int cut = Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/'));
    String frag = cut >= 0 ? iri.substring(cut + 1) : iri;
    return frag.isEmpty() ? null : frag;
  }

  /**
   * Gives every unlabeled concept a fallback label from its IRI fragment ({@link #labelFromIri}), so
   * label-less ontologies (no {@code rdfs:label}/{@code skos:prefLabel} anywhere — their name lives in
   * the IRI) are searchable and browsable locally instead of returning empty, matching BioPortal.
   * Run after {@link #pruneDeadEndImportRoots}: the prune keys on the genuinely-unlabeled state, and
   * this only writes {@code pref_label}, never the {@code root} table, so it does not resurrect pruned
   * roots. Idempotent. Returns the number of concepts labeled.
   */
  public int fillMissingLabelsFromIri() throws SQLException {
    List<long[]> ids = new ArrayList<>();
    List<String> labels = new ArrayList<>();
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery(
             "SELECT id, iri FROM concept WHERE pref_label IS NULL OR pref_label = ''")) {
      while (rs.next()) {
        String label = labelFromIri(rs.getString(2));
        if (label != null) {
          ids.add(new long[]{rs.getLong(1)});
          labels.add(label);
        }
      }
    }
    if (ids.isEmpty()) {
      return 0;
    }
    try (PreparedStatement up = connection.prepareStatement("UPDATE concept SET pref_label = ? WHERE id = ?")) {
      for (int i = 0; i < ids.size(); i++) {
        up.setString(1, labels.get(i));
        up.setLong(2, ids.get(i)[0]);
        up.addBatch();
      }
      up.executeBatch();
    }
    return ids.size();
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

  /**
   * Direct children of a concept, by label, at most {@code limit} of them.
   *
   * Ordering and limiting together, rather than a caller taking the first n of {@link #children} and
   * sorting those: children come back by IRI, so taking the first n of that and then sorting them
   * for display yields an arbitrary subset presented as an alphabetical list. It also matches how
   * the search index serves the same question for the current release, so pinning a release changes
   * which release is read and nothing else.
   *
   * Case-insensitively, because the order is read by a person. SQLite's default collation compares
   * bytes, which puts every capitalised label before every lowercase one: DOID's "disease" listed
   * "Y-linked monogenic disease" above "abducens nerve palsy", and a reader looking for a term among
   * 194 children of which 50 are shown reasonably concluded it was absent. The label is still the
   * tiebreak after the case-folded comparison, so two labels differing only in case keep a stable
   * order rather than depending on which row the query reached first.
   */
  public List<LabelledConcept> childrenByLabel(String parentIri, int offset, int limit)
      throws SQLException {
    List<LabelledConcept> out = new ArrayList<>();
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT c.iri, c.pref_label FROM edge e
        JOIN concept c ON c.id = e.child_id
        JOIN concept p ON p.id = e.parent_id
        WHERE p.iri = ? ORDER BY c.pref_label COLLATE NOCASE, c.pref_label, c.iri LIMIT ? OFFSET ?""")) {
      ps.setString(1, parentIri);
      ps.setInt(2, limit);
      ps.setInt(3, offset);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new LabelledConcept(rs.getString(1), rs.getString(2)));
        }
      }
    }
    return out;
  }

  /** A concept's IRI with the label the snapshot records for it, which may be absent. */
  public record LabelledConcept(String iri, String prefLabel) {
  }

  /** How many direct children a concept has, without reading them. */
  public int childCount(String parentIri) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT COUNT(*) FROM edge e
        JOIN concept p ON p.id = e.parent_id
        WHERE p.iri = ?""")) {
      ps.setString(1, parentIri);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
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

  /**
   * How many descendants a concept has, counted in the closure rather than materialized.
   *
   * A branch constraint offers a class's subtypes, so this is the size of what constraining to it
   * would capture — the number a picker shows beside a branch. Counting in SQL matters because the
   * caller asks it once per row of a result page: {@link #descendants} would build and discard a
   * list of IRIs, which for an upper-level class is tens of thousands of strings per row.
   */
  public int descendantCount(String ancestorIri) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT COUNT(*) FROM closure cl
        JOIN concept a ON a.id = cl.ancestor_id
        WHERE a.iri = ?""")) {
      ps.setString(1, ancestorIri);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }

  /**
   * The descendant count of every concept that has one, in a single pass.
   *
   * {@link #descendantCount} answers for one concept, which is what a page of results needs. Building
   * an index needs the answer for all of them, and asking one at a time over a snapshot the size of
   * NCBITaxon is millions of statements against a table that can produce the whole answer with one
   * GROUP BY. Concepts with no descendants are absent rather than present with zero.
   */
  public Map<String, Integer> descendantCounts() throws SQLException {
    Map<String, Integer> out = new HashMap<>();
    try (Statement st = connection.createStatement();
         ResultSet rs = st.executeQuery("""
             SELECT a.iri, COUNT(*) FROM closure cl
             JOIN concept a ON a.id = cl.ancestor_id
             GROUP BY a.iri""")) {
      while (rs.next()) {
        out.put(rs.getString(1), rs.getInt(2));
      }
    }
    return out;
  }

  /**
   * The captured labels of one concept that match {@code query}, with the property and language each
   * was recorded under.
   *
   * {@link #searchByLabel} matches a concept through any captured name — a label in any language, a
   * synonym — so a hit's served {@code pref_label} often has no visible relation to what was typed.
   * This answers what did match, which is what lets a result say so rather than looking like a
   * defect. Empty when the query matched the served preferred label itself, or when the snapshot
   * carries no label table.
   */
  public List<LabelEntry> matchingLabels(String conceptIri, String query) throws SQLException {
    if (query == null || query.isEmpty() || !hasLabelTable()) {
      return List.of();
    }
    List<LabelEntry> out = new ArrayList<>();
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT l.property, l.lang, l.value FROM label l
        JOIN concept c ON c.id = l.concept_id
        WHERE c.iri = ? AND l.value LIKE ? ESCAPE '\\'
        ORDER BY LENGTH(l.value), l.value""")) {
      ps.setString(1, conceptIri);
      ps.setString(2, "%" + escapeLike(query) + "%");
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new LabelEntry(rs.getString(1), rs.getString(2), rs.getString(3)));
        }
      }
    }
    return out;
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

  /** Typed relations from a concept, as {@code [predicate, objectIri]} pairs. */
  public List<String[]> relationsFrom(String subjectIri) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT r.predicate, o.iri FROM relation r
        JOIN concept s ON s.id = r.subject_id
        JOIN concept o ON o.id = r.object_id
        WHERE s.iri = ? ORDER BY r.predicate, o.iri""")) {
      ps.setString(1, subjectIri);
      try (ResultSet rs = ps.executeQuery()) {
        List<String[]> out = new ArrayList<>();
        while (rs.next()) {
          out.add(new String[]{rs.getString(1), rs.getString(2)});
        }
        return out;
      }
    }
  }

  /** Subjects related to an object by a predicate (reverse: e.g. drugs whose has_ingredient is X). */
  public List<String> subjectsWith(String predicate, String objectIri) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement("""
        SELECT s.iri FROM relation r
        JOIN concept s ON s.id = r.subject_id
        JOIN concept o ON o.id = r.object_id
        WHERE r.predicate = ? AND o.iri = ? ORDER BY s.iri""")) {
      ps.setString(1, predicate);
      ps.setString(2, objectIri);
      try (ResultSet rs = ps.executeQuery()) {
        List<String> out = new ArrayList<>();
        while (rs.next()) {
          out.add(rs.getString(1));
        }
        return out;
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

  /** The number of roots, without materializing the list — a cheap emptiness/size check. */
  public int rootCount() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM root")) {
      rs.next();
      return rs.getInt(1);
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

  /** Cross-version identity metadata for one concept (obsolete flag + replacement IRI), if present. */
  public Optional<ConceptMeta> conceptMeta(String iri) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT iri, obsolete, replaced_by FROM concept WHERE iri = ?")) {
      ps.setString(1, iri);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next()
            ? Optional.of(new ConceptMeta(rs.getString(1), rs.getInt(2) != 0, rs.getString(3)))
            : Optional.empty();
      }
    }
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

  /** Every concept as a row, ordered by IRI — for whole-ontology enumeration. */
  public List<Concept> allConceptsDetailed() throws SQLException {
    return queryConcepts("""
        SELECT c.iri, c.pref_label, c.obsolete,
               EXISTS(SELECT 1 FROM edge e2 WHERE e2.parent_id = c.id) AS has_children
        FROM concept c ORDER BY c.iri""", null);
  }

  /* --------------------------------------------------------------------------------------------
   * Label search — the primitive behind the picker's class search and type-ahead autocomplete.
   *
   * This is a plain, case-insensitive match on the preferred label. It deliberately does NOT
   * reproduce BioPortal's Solr behaviour (tokenization, stemming, synonym expansion, edge n-grams):
   * a substring/prefix match will both miss some Solr hits and over-match others. The equivalence
   * harness measures that divergence rather than assuming it away.
   * ------------------------------------------------------------------------------------------ */

  /**
   * Concepts whose preferred label matches {@code query}. When {@code prefixOnly} is true the label
   * must start with the query (type-ahead style); otherwise the query may occur anywhere in it.
   * Matching is case-insensitive (SQLite {@code LIKE} folds ASCII case). Results lead with the
   * shortest labels (a "closest match first" heuristic), then label, then IRI, capped at
   * {@code limit} ({@code <= 0} means no cap).
   */
  public List<Concept> searchByLabel(String query, boolean prefixOnly, int limit) throws SQLException {
    return labelSearch(null, query, prefixOnly, limit);
  }

  /** As {@link #searchByLabel}, but restricted to the strict descendants of {@code rootIri}; the
   * branch root itself is excluded, matching BioPortal's branch semantics. */
  public List<Concept> searchByLabelUnderRoot(String rootIri, String query, boolean prefixOnly, int limit)
      throws SQLException {
    return labelSearch(rootIri, query, prefixOnly, limit);
  }

  private List<Concept> labelSearch(String rootIri, String query, boolean prefixOnly, int limit) throws SQLException {
    String escaped = escapeLike(query == null ? "" : query);
    String pattern = prefixOnly ? escaped + "%" : "%" + escaped + "%";
    // A non-empty query also matches any captured name — a label in any language, or a synonym — so a
    // French term or an exact synonym finds the concept, not only its served pref_label. An empty query
    // is a browse: pref_label already matches every concept, so the label join is skipped (and its per-row
    // cost avoided). The match column, factored so it is used identically in the rooted and unrooted forms.
    boolean searchNames = query != null && !query.isEmpty() && hasLabelTable();
    String match = searchNames
        ? "(COALESCE(c.pref_label, '') LIKE ? ESCAPE '\\'"
            + " OR EXISTS(SELECT 1 FROM label l WHERE l.concept_id = c.id AND l.value LIKE ? ESCAPE '\\'))"
        : "COALESCE(c.pref_label, '') LIKE ? ESCAPE '\\'";
    StringBuilder sql = new StringBuilder(
        "SELECT c.iri, c.pref_label, c.obsolete,\n" +
        "       EXISTS(SELECT 1 FROM edge e2 WHERE e2.parent_id = c.id) AS has_children\n" +
        "FROM concept c\n");
    // COALESCE the label to '' so a concept with no label is not silently dropped: NULL LIKE '%'
    // is NULL (not true), which would otherwise exclude every unlabeled concept from a browse
    // (empty query). Some ontologies carry a correct hierarchy but no rdfs:label/skos:prefLabel
    // (e.g. GALEN, whose labels live only in the IRI). An empty query then matches them ('' LIKE
    // '%'), while a real search term still cannot match a label-less concept ('' LIKE '%term%' is
    // false) — so browse enumerates the subtree while prefix search stays label-driven.
    if (rootIri != null) {
      // The branch's descendants only — the root itself is excluded, matching BioPortal's branch
      // semantics (a branch value constraint offers the subtypes of the class, not the class itself).
      // The closure is non-reflexive, but a broader/narrower cycle (which some SKOS vocabularies
      // contain, e.g. HRAVS) can make a node reach itself; the explicit c.iri <> root guards that.
      sql.append("WHERE c.id IN (\n" +
                 "        SELECT cl.descendant_id FROM closure cl\n" +
                 "        JOIN concept a ON a.id = cl.ancestor_id WHERE a.iri = ?)\n" +
                 "  AND c.iri <> ?\n" +
                 "  AND ").append(match).append('\n');
    } else {
      sql.append("WHERE ").append(match).append('\n');
    }
    sql.append("ORDER BY length(c.pref_label), c.pref_label, c.iri");
    if (limit > 0) {
      sql.append(" LIMIT ").append(limit);
    }
    try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
      int i = 1;
      if (rootIri != null) {
        ps.setString(i++, rootIri);
        ps.setString(i++, rootIri);
      }
      ps.setString(i++, pattern);
      if (searchNames) {
        ps.setString(i, pattern); // the label-table match reuses the same pattern
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

  /** Escapes the SQL {@code LIKE} metacharacters so a query is matched literally. */
  private static String escapeLike(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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

  /** Every concept's cross-version identity metadata (IRI, obsolete flag, replacement IRI). */
  public List<ConceptMeta> allConceptMeta() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT iri, obsolete, replaced_by FROM concept")) {
      List<ConceptMeta> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new ConceptMeta(rs.getString(1), rs.getInt(2) != 0, rs.getString(3)));
      }
      return out;
    }
  }

  /** Every concept's complete label-sensitive content state, for version comparison. */
  public List<ConceptState> allConceptStates() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("SELECT iri, pref_label, obsolete, replaced_by FROM concept")) {
      List<ConceptState> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new ConceptState(rs.getString(1), rs.getString(2), rs.getInt(3) != 0, rs.getString(4)));
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

  /** Every direct edge as a {@code [childIri, parentIri, sourcePredicate]} triple. */
  public List<String[]> allEdgesWithPredicates() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("""
             SELECT c.iri, p.iri, e.source_pred FROM edge e
             JOIN concept c ON c.id = e.child_id
             JOIN concept p ON p.id = e.parent_id""")) {
      List<String[]> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
      }
      return out;
    }
  }

  /** Every typed relation as a {@code [subjectIri, predicate, objectIri]} triple. */
  public List<String[]> allRelations() throws SQLException {
    try (Statement s = connection.createStatement();
         ResultSet rs = s.executeQuery("""
             SELECT subj.iri, r.predicate, obj.iri FROM relation r
             JOIN concept subj ON subj.id = r.subject_id
             JOIN concept obj ON obj.id = r.object_id""")) {
      List<String[]> out = new ArrayList<>();
      while (rs.next()) {
        out.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
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
