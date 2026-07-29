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

  /** A concept's cross-version identity metadata: IRI, obsolete flag, and replacement IRI if any. */
  public record ConceptMeta(String iri, boolean obsolete, String replacedBy) {}

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
      own.add(java.util.Collections.max(freq.entrySet(), java.util.Map.Entry.comparingByValue()).getKey());
    }
    return own;
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
                 "  AND COALESCE(c.pref_label, '') LIKE ? ESCAPE '\\'\n");
    } else {
      sql.append("WHERE COALESCE(c.pref_label, '') LIKE ? ESCAPE '\\'\n");
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
      ps.setString(i, pattern);
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
