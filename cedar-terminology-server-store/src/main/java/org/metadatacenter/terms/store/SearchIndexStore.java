package org.metadatacenter.terms.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One searchable index across every served snapshot, so a query need not open 1,215 SQLite files.
 *
 * A snapshot is a self-contained file, which is what makes a version reproducible and what makes a
 * search of the whole corpus impossible to serve by iteration: 13.9 million concepts and about 16
 * million captured names, spread over 8.2 GB, measured 2026-08-13. This is the one place they are
 * gathered.
 *
 * <p><b>It holds the current version of each ontology and no other.</b> That is not a shortcut. A
 * corpus-wide search cannot be pinned — there is no one version to pin it to, only a version per
 * ontology — so searching everything is searching what is current. A search that names its sources
 * can be pinned, and that path reads the snapshot itself rather than this index. The index records
 * the {@code version_id} it holds for each ontology, so a caller can say which version answered
 * and a rebuild can skip what has not moved.
 *
 * <p>Matching is FTS5 rather than the snapshot's {@code LIKE}: token prefixes instead of arbitrary
 * substrings, and diacritics folded, so {@code aquifere} finds {@code aquifère}. The two paths
 * therefore answer the same query slightly differently, which is a real inconsistency and belongs
 * with the search-ordering work rather than being papered over here.
 */
public class SearchIndexStore implements AutoCloseable {

  /**
   * A term as the index holds it, with the structural facts a result row needs.
   *
   * {@code parentLabel} is the immediate parent's name, and it is here because a label does not
   * always identify a class. IRAEO carries fifteen classes labelled "Disease", one under each body
   * system, so a corpus-wide result listing them by label alone is fifteen rows nobody can choose
   * between. The parent is what separates them. One step, not the whole chain: the chain needs a
   * walk per term and the step is a join.
   */
  public record IndexedTerm(
      String acronym,
      String iri,
      String prefLabel,
      boolean obsolete,
      String replacedBy,
      boolean hasChildren,
      int descendantCount,
      String parentIri,
      String parentLabel,
      String definition) {

    public IndexedTerm(String acronym, String iri, String prefLabel, boolean obsolete, String replacedBy,
                       boolean hasChildren, int descendantCount) {
      this(acronym, iri, prefLabel, obsolete, replacedBy, hasChildren, descendantCount, null, null, null);
    }
  }

  /** A searchable name of a term: its preferred label, a synonym, or a label in another language. */
  public record IndexedName(String property, String lang, String value) {}

  /** A hit: the term, and the names of it that matched. */
  public record IndexHit(IndexedTerm term, List<IndexedName> matched) {}

  /**
   * Every name a page of terms carries, keyed by acronym and IRI.
   *
   * One query for the page rather than one per row: a term's synonyms are what say whether it is
   * the concept an author meant, and they are worth nothing if fetching them costs a round trip a
   * row. An IRI can belong to more than one ontology — OBO terms are imported widely — so the key
   * is the pair that addresses a term and not the IRI alone.
   */
  public Map<String, List<IndexedName>> namesOf(Collection<String> iris) throws SQLException {
    Map<String, List<IndexedName>> out = new HashMap<>();
    if (iris.isEmpty()) {
      return out;
    }
    List<String> distinct = List.copyOf(new LinkedHashSet<>(iris));
    String sql = "SELECT t.acronym, t.iri, n.property, n.lang, n.value FROM term t"
        + " JOIN name n ON n.term_id = t.term_id WHERE t.iri IN ("
        + String.join(",", Collections.nCopies(distinct.size(), "?")) + ")";
    try (PreparedStatement ps = connection().prepareStatement(sql)) {
      for (int i = 0; i < distinct.size(); i++) {
        ps.setString(i + 1, distinct.get(i));
      }
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.computeIfAbsent(nameKey(rs.getString(1), rs.getString(2)), key -> new ArrayList<>())
              .add(new IndexedName(rs.getString(3), rs.getString(4), rs.getString(5)));
        }
      }
    }
    return out;
  }

  /**
   * The chain above a term, root first.
   *
   * Walked in SQLite rather than a query a step, since a deep vocabulary is thirty steps and each
   * would be a round trip. Bounded by depth rather than trusted to terminate: a broader/narrower
   * cycle, which some SKOS vocabularies carry, would otherwise recurse until the process died.
   */
  public List<IndexedTerm> ancestors(String acronym, String iri, int maxDepth) throws SQLException {
    String sql = """
        WITH RECURSIVE up(iri, depth) AS (
          SELECT parent_iri, 1 FROM term WHERE acronym = ? AND iri = ? AND parent_iri IS NOT NULL
          UNION ALL
          SELECT t.parent_iri, up.depth + 1 FROM term t JOIN up ON t.iri = up.iri
            WHERE t.acronym = ? AND t.parent_iri IS NOT NULL AND up.depth < ?
        )
        SELECT t.acronym, t.iri, t.pref_label, t.obsolete, t.replaced_by, t.has_children,
               t.descendant_count, t.parent_iri, t.parent_label, t.definition, up.depth
        FROM up JOIN term t ON t.iri = up.iri AND t.acronym = ?
        ORDER BY up.depth DESC""";
    List<IndexedTerm> chain = new ArrayList<>();
    try (PreparedStatement ps = connection().prepareStatement(sql)) {
      ps.setString(1, acronym);
      ps.setString(2, iri);
      ps.setString(3, acronym);
      ps.setInt(4, maxDepth);
      ps.setString(5, acronym);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          chain.add(readTerm(rs));
        }
      }
    }
    return chain;
  }

  /**
   * What sits directly under a term, and how many there are in all.
   *
   * By case-folded label, matching the snapshot's own children query, so pinning a release changes
   * which release is read and not the order it is read in. Byte order put capitals first, which for
   * a capped list reads as a term being absent.
   */
  public List<IndexedTerm> children(String acronym, String iri, int offset, int limit)
      throws SQLException {
    String sql = "SELECT acronym, iri, pref_label, obsolete, replaced_by, has_children,"
        + " descendant_count, parent_iri, parent_label, definition FROM term"
        + " WHERE acronym = ? AND parent_iri = ? ORDER BY pref_label COLLATE NOCASE, pref_label, iri LIMIT ? OFFSET ?";
    List<IndexedTerm> out = new ArrayList<>();
    try (PreparedStatement ps = connection().prepareStatement(sql)) {
      ps.setString(1, acronym);
      ps.setString(2, iri);
      ps.setInt(3, limit);
      ps.setInt(4, offset);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(readTerm(rs));
        }
      }
    }
    return out;
  }

  /** How many children a term has, without reading them. */
  public int childCount(String acronym, String iri) throws SQLException {
    try (PreparedStatement ps = connection().prepareStatement(
        "SELECT COUNT(*) FROM term WHERE acronym = ? AND parent_iri = ?")) {
      ps.setString(1, acronym);
      ps.setString(2, iri);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }

  public Optional<IndexedTerm> term(String acronym, String iri) throws SQLException {
    try (PreparedStatement ps = connection().prepareStatement(
        "SELECT acronym, iri, pref_label, obsolete, replaced_by, has_children, descendant_count,"
            + " parent_iri, parent_label, definition FROM term WHERE acronym = ? AND iri = ?")) {
      ps.setString(1, acronym);
      ps.setString(2, iri);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(readTerm(rs)) : Optional.empty();
      }
    }
  }

  private static IndexedTerm readTerm(ResultSet rs) throws SQLException {
    return new IndexedTerm(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4) != 0,
        rs.getString(5), rs.getInt(6) != 0, rs.getInt(7), rs.getString(8), rs.getString(9),
        rs.getString(10));
  }

  /** Whether a table already has a column, so a migration runs once rather than failing twice. */
  private static boolean hasColumn(Statement st, String table, String column) throws SQLException {
    try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
      while (rs.next()) {
        if (column.equals(rs.getString("name"))) {
          return true;
        }
      }
      return false;
    }
  }

  /** The pair that addresses a term, as one string, for keying a lookup. */
  public static String nameKey(String acronym, String iri) {
    return acronym + '\u0000' + iri;
  }

  private final Connection connection;

  /**
   * A connection each reading thread, or {@code null} when this store was opened to write.
   *
   * A writer keeps the one connection it commits on. Only a store opened by {@link #openForRead}
   * hands out others, because only then is every statement a read.
   */
  private final ReadConnections reads;

  /** Connections a serving store will open before threads start sharing one again. */
  private static final int READ_CONNECTION_LIMIT = 8;

  /** The SQL name of the function applying {@link #carriesResidual}. */
  static final String RESIDUAL_FN = "cedar_carries_residual";

  private SearchIndexStore(Connection connection) throws SQLException {
    this(connection, null);
  }

  private SearchIndexStore(Connection connection, String readPath) throws SQLException {
    this.connection = connection;
    registerResidualFunction(connection);
    this.reads = readPath == null ? null
        : ReadConnections.of(readPath, connection, SearchIndexStore::prepareRead,
            READ_CONNECTION_LIMIT);
  }

  /** Everything a freshly opened reading connection needs before it answers a query. */
  private static void prepareRead(Connection fresh) throws SQLException {
    registerResidualFunction(fresh);
  }

  /**
   * The connection this call should use: the caller's thread has its own when the store serves.
   */
  private Connection connection() throws SQLException {
    return reads == null ? connection : reads.get();
  }

  /**
   * Teaches SQLite the test a held-back token has to pass.
   *
   * The test belongs where the rows are, not where they land: the paged query ranks and groups its
   * matches in SQL, so a filter applied to its output would rank names it is about to discard. A
   * scalar function keeps one definition of the rule, in Java, and lets every query apply it before
   * it aggregates.
   */
  private static void registerResidualFunction(Connection connection) throws SQLException {
    org.sqlite.Function.create(connection, RESIDUAL_FN, new org.sqlite.Function() {
      @Override
      protected void xFunc() throws SQLException {
        String value = value_text(0);
        String held = value_text(1);
        if (held == null || held.isBlank()) {
          result(1);
          return;
        }
        result(carriesResidual(value, Arrays.asList(held.split(" "))) ? 1 : 0);
      }
    });
  }

  /** {@code ""} when the plan held nothing back, otherwise a predicate with one bound parameter. */
  private static String residualFilter(MatchPlan plan) {
    return plan.residual().isEmpty() ? "" : " AND " + RESIDUAL_FN + "(n.value, ?) = 1";
  }

  public static SearchIndexStore openFile(String path) throws SQLException {
    return new SearchIndexStore(DriverManager.getConnection("jdbc:sqlite:" + path));
  }

  /**
   * Opens the index to answer queries, where lookups arrive on many threads at once.
   *
   * The store this returns writes nothing. An ingest opens the same file with {@link #openFile},
   * which keeps the single connection its transactions need.
   */
  public static SearchIndexStore openForRead(String path) throws SQLException {
    return new SearchIndexStore(DriverManager.getConnection("jdbc:sqlite:" + path), path);
  }

  public static SearchIndexStore openInMemory() throws SQLException {
    return new SearchIndexStore(DriverManager.getConnection("jdbc:sqlite::memory:"));
  }

  public void initSchema() throws SQLException {
    try (Statement st = connection().createStatement()) {
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS indexed_snapshot (
            acronym     TEXT PRIMARY KEY,
            version_id  TEXT NOT NULL,
            term_count  INTEGER NOT NULL,
            indexed_at  TEXT NOT NULL
          )""");
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS term (
            term_id          INTEGER PRIMARY KEY,
            acronym          TEXT NOT NULL,
            iri              TEXT NOT NULL,
            pref_label       TEXT,
            obsolete         INTEGER NOT NULL DEFAULT 0,
            replaced_by      TEXT,
            has_children     INTEGER NOT NULL DEFAULT 0,
            descendant_count INTEGER NOT NULL DEFAULT 0,
            parent_iri       TEXT,
            parent_label     TEXT,
            definition       TEXT
          )""");
      st.executeUpdate("CREATE INDEX IF NOT EXISTS term_by_acronym ON term(acronym)");
      // Terms are also reached by IRI alone, which is how a page of corpus-wide hits fetches its
      // names: the hits come from the whole corpus, so there is no acronym to narrow by. Without
      // this the lookup scans every term in the index -- fifteen million rows for a page of
      // twenty-five, on every corpus-wide search -- and the scan, not the search, is what the
      // request costs.
      st.executeUpdate("CREATE INDEX IF NOT EXISTS term_by_iri ON term(iri)");
      // An index built before definitions were captured has no such column; adding it here means an
      // existing index keeps working and fills as ontologies are rebuilt, rather than all at once.
      if (!hasColumn(st, "term", "definition")) {
        st.executeUpdate("ALTER TABLE term ADD COLUMN definition TEXT");
      }
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS name (
            name_id  INTEGER PRIMARY KEY,
            term_id  INTEGER NOT NULL,
            property TEXT,
            lang     TEXT,
            value    TEXT NOT NULL,
            ont      TEXT
          )""");
      // An index built before the ontology was indexed alongside the text has no such column. It
      // keeps working: the column stays empty, and a scoped search then filters rows after the
      // match, which is what every scoped search used to do. Rebuilding the index fills it.
      if (!hasColumn(st, "name", "ont")) {
        st.executeUpdate("ALTER TABLE name ADD COLUMN ont TEXT");
      }
      st.executeUpdate("CREATE INDEX IF NOT EXISTS name_by_term ON name(term_id)");
      // External-content FTS: the searchable text lives once, in `name`, and the index refers to it
      // by rowid. Storing it twice would add a gigabyte to say the same thing.
      //
      // remove_diacritics 2 folds accents, so "aquifere" finds "aquifère" — which the snapshot's
      // LIKE cannot do, since SQLite folds ASCII case only.
      //
      // `ont` carries the ontology a name belongs to, so that narrowing a search to one ontology
      // narrows what the index reads rather than what it returns. Filtered after the match, a search
      // of DOID's 19,578 terms costs what a search of all 15.3 million costs, because the match has
      // already walked them: "acid" in DOID took 284 ms that way and 21 ms this way, and in RH-MESH
      // 1,526 ms became 27 ms.
      //
      // The value is the acronym hex-encoded rather than the acronym. An acronym is not one token to
      // this tokenizer — 191 of the 1,266 carry a hyphen or an underscore — so `RH-MESH` indexed as
      // text answers a search scoped to `MESH`. Stripping the punctuation instead collides 16 pairs,
      // among them COVID-19 with COVID19, which are different ontologies holding different terms.
      // Hex is one token, and one token an acronym.
      st.executeUpdate("""
          CREATE VIRTUAL TABLE IF NOT EXISTS name_fts USING fts5(
            value,
            ont,
            content='name',
            content_rowid='name_id',
            tokenize="unicode61 remove_diacritics 2"
          )""");
    }
  }

  /** Whether this SQLite build has FTS5. Called before a build rather than discovered mid-way. */
  public static boolean supportsFts5() {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
         Statement st = c.createStatement()) {
      st.executeUpdate("CREATE VIRTUAL TABLE probe USING fts5(v)");
      return true;
    } catch (SQLException noFts5) {
      return false;
    }
  }

  /** The version of an ontology the index currently holds, if any. */
  /**
   * How many terms the index holds for an ontology, or zero where it holds none.
   *
   * Recorded when the ontology was indexed rather than counted on demand, so a caller deciding
   * whether an ontology is large enough to answer from the index pays nothing to ask.
   */
  public long indexedTermCount(String acronym) throws SQLException {
    try (PreparedStatement ps = connection().prepareStatement(
        "SELECT term_count FROM indexed_snapshot WHERE acronym = ?")) {
      ps.setString(1, acronym);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getLong(1) : 0L;
      }
    }
  }

  public Optional<String> indexedVersion(String acronym) throws SQLException {
    try (PreparedStatement ps =
             connection().prepareStatement("SELECT version_id FROM indexed_snapshot WHERE acronym = ?")) {
      ps.setString(1, acronym);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
      }
    }
  }

  /** Every ontology in the index, with the version held for each. */
  public Map<String, String> indexedVersions() throws SQLException {
    Map<String, String> out = new LinkedHashMap<>();
    try (Statement st = connection().createStatement();
         ResultSet rs = st.executeQuery("SELECT acronym, version_id FROM indexed_snapshot ORDER BY acronym")) {
      while (rs.next()) {
        out.put(rs.getString(1), rs.getString(2));
      }
    }
    return out;
  }

  public int indexedOntologyCount() throws SQLException {
    try (Statement st = connection().createStatement();
         ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM indexed_snapshot")) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  public long termCount() throws SQLException {
    try (Statement st = connection().createStatement();
         ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM term")) {
      return rs.next() ? rs.getLong(1) : 0;
    }
  }

  /**
   * Replaces everything the index holds for one ontology, in a single transaction.
   *
   * Replace rather than merge: an ontology's terms are whatever its current snapshot says, and a
   * re-ingest can retire a term as easily as add one. Merging would leave the retired term
   * searchable and pointing at a version that no longer contains it.
   */
  public void replaceOntology(String acronym, String versionId, String indexedAt,
                              Collection<IndexedTerm> terms,
                              Map<String, List<IndexedName>> namesByIri) throws SQLException {
    boolean autoCommit = connection().getAutoCommit();
    connection().setAutoCommit(false);
    try {
      deleteOntologyRows(acronym);
      try (PreparedStatement insertTerm = connection().prepareStatement(
               "INSERT INTO term(acronym, iri, pref_label, obsolete, replaced_by, has_children, "
                   + "descendant_count, parent_iri, parent_label, definition) "
                   + "VALUES (?,?,?,?,?,?,?,?,?,?)",
               Statement.RETURN_GENERATED_KEYS);
           PreparedStatement insertName = connection().prepareStatement(
               "INSERT INTO name(term_id, property, lang, value, ont) VALUES (?,?,?,?,?)")) {
        String ont = ontToken(acronym);
        for (IndexedTerm t : terms) {
          insertTerm.setString(1, acronym);
          insertTerm.setString(2, t.iri());
          insertTerm.setString(3, t.prefLabel());
          insertTerm.setInt(4, t.obsolete() ? 1 : 0);
          insertTerm.setString(5, t.replacedBy());
          insertTerm.setInt(6, t.hasChildren() ? 1 : 0);
          insertTerm.setInt(7, t.descendantCount());
          insertTerm.setString(8, t.parentIri());
          insertTerm.setString(9, t.parentLabel());
          insertTerm.setString(10, t.definition());
          insertTerm.executeUpdate();
          long termId;
          try (ResultSet keys = insertTerm.getGeneratedKeys()) {
            termId = keys.next() ? keys.getLong(1) : -1;
          }
          // The preferred label is a searchable name like any other, so a plain label match needs no
          // separate query path.
          if (t.prefLabel() != null && !t.prefLabel().isBlank()) {
            addName(insertName, termId, "prefLabel", null, t.prefLabel(), ont);
          }
          for (IndexedName n : namesByIri.getOrDefault(t.iri(), List.of())) {
            addName(insertName, termId, n.property(), n.lang(), n.value(), ont);
          }
        }
        insertName.executeBatch();
      }
      try (PreparedStatement ps = connection().prepareStatement(
          "INSERT OR REPLACE INTO indexed_snapshot(acronym, version_id, term_count, indexed_at) VALUES (?,?,?,?)")) {
        ps.setString(1, acronym);
        ps.setString(2, versionId);
        ps.setInt(3, terms.size());
        ps.setString(4, indexedAt);
        ps.executeUpdate();
      }
      connection().commit();
    } catch (SQLException e) {
      connection().rollback();
      throw e;
    } finally {
      connection().setAutoCommit(autoCommit);
    }
  }

  private static void addName(PreparedStatement insertName, long termId, String property, String lang,
                              String value, String ont) throws SQLException {
    insertName.setLong(1, termId);
    insertName.setString(2, property);
    insertName.setString(3, lang);
    insertName.setString(4, value);
    insertName.setString(5, ont);
    insertName.addBatch();
  }

  private void deleteOntologyRows(String acronym) throws SQLException {
    try (PreparedStatement ps = connection().prepareStatement(
        "DELETE FROM name WHERE term_id IN (SELECT term_id FROM term WHERE acronym = ?)")) {
      ps.setString(1, acronym);
      ps.executeUpdate();
    }
    try (PreparedStatement ps = connection().prepareStatement("DELETE FROM term WHERE acronym = ?")) {
      ps.setString(1, acronym);
      ps.executeUpdate();
    }
  }

  /**
   * Builds the full-text index from the names table.
   *
   * Separate from insertion, and deliberately: keeping FTS in step row by row costs more than
   * building it once at the end, and a build that dies half way leaves an index that is merely
   * stale rather than one that disagrees with itself.
   */
  public void rebuildFullText() throws SQLException {
    try (Statement st = connection().createStatement()) {
      // An index whose full-text table predates the ontology column is widened here rather than on
      // open, because opening is what a serving process does: dropping the table there would leave
      // the server answering nothing until a rebuild it has no reason to run. A rebuild is already
      // the operation that repopulates it, and the text it draws on lives in `name` either way.
      if (!hasColumn(st, "name_fts", "ont")) {
        st.executeUpdate("DROP TABLE name_fts");
        st.executeUpdate("""
            CREATE VIRTUAL TABLE name_fts USING fts5(
              value,
              ont,
              content='name',
              content_rowid='name_id',
              tokenize="unicode61 remove_diacritics 2"
            )""");
        scopedIndex = null;
      }
      st.executeUpdate("INSERT INTO name_fts(name_fts) VALUES('rebuild')");
    }
  }

  /** Reclaims space and updates the planner's statistics. Worth running once after a full build. */
  public void optimize() throws SQLException {
    try (Statement st = connection().createStatement()) {
      st.executeUpdate("INSERT INTO name_fts(name_fts) VALUES('optimize')");
      st.executeUpdate("ANALYZE");
    }
  }

  /**
   * Terms whose names match {@code query}, across every indexed ontology or a named subset.
   *
   * Each token is matched as a prefix, which is what a picker's search means: "melano" should reach
   * melanoma while it is still being typed. A term that matched through several of its names is
   * returned once, carrying all of them, so a caller can say what matched without a second query.
   */
  public List<IndexHit> search(String query, Collection<String> acronyms, int limit) throws SQLException {
    return search(query, acronyms, false, limit);
  }

  /**
   * @param branchesOnly return only terms that have descendants. Filtered here rather than by the
   *                     caller: filtering after the limit under-fills a page, since most terms are
   *                     leaves.
   */
  public List<IndexHit> search(String query, Collection<String> acronyms, boolean branchesOnly, int limit)
      throws SQLException {
    MatchPlan plan = toMatchPlan(query);
    String match = plan.match();
    if (match.isEmpty()) {
      return List.of();
    }
    match = scopedMatch(match, acronyms);
    String needle = query.trim().toLowerCase(Locale.ROOT);
    StringBuilder sql = new StringBuilder("""
        SELECT t.acronym, t.iri, t.pref_label, t.obsolete, t.replaced_by, t.has_children,
               t.descendant_count, n.property, n.lang, n.value, t.term_id, t.parent_iri, t.parent_label,
               t.definition
        FROM name_fts f
        JOIN name n ON n.name_id = f.rowid
        JOIN term t ON t.term_id = n.term_id
        WHERE name_fts MATCH ?""");
    if (branchesOnly) {
      sql.append(" AND t.has_children = 1");
    }
    if (acronyms != null && !acronyms.isEmpty()) {
      sql.append(" AND t.acronym IN (")
          .append(String.join(",", java.util.Collections.nCopies(acronyms.size(), "?")))
          .append(')');
    }
    // Ranked here rather than by the caller: the limit truncates before a caller can reorder, so
    // ordering by label length alone fills the cap with the shortest labels in the corpus — the
    // numeric codes of coded vocabularies — and drops the terms actually named after the query.
    // Obsolete after the match rank rather than before it: a deprecated term is shown and marked,
    // and demoted below the live terms that answer as well — not below every live term that answers
    // worse. It ranks second so an exact hit on a retired label still beats a distant live one.
    sql.append(" ORDER BY ").append(MATCH_RANK)
        .append(", t.obsolete, LENGTH(n.value), n.value, t.iri LIMIT ?");

    Map<Long, IndexHit> byTerm = new LinkedHashMap<>();
    try (PreparedStatement ps = connection().prepareStatement(sql.toString())) {
      int p = 1;
      ps.setString(p++, match);
      if (acronyms != null) {
        for (String acronym : acronyms) {
          ps.setString(p++, acronym);
        }
      }
      for (int i = 0; i < MATCH_RANK_PARAMS; i++) {
        ps.setString(p++, needle);
      }
      // Names, not terms: one term can match several of its names, so ask for enough rows to fill
      // the requested number of terms.
      ps.setInt(p, Math.max(limit, 1) * 4);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          if (!carriesResidual(rs.getString(10), plan.residual())) {
            continue;
          }
          long termId = rs.getLong(11);
          IndexHit hit = byTerm.get(termId);
          if (hit == null) {
            if (byTerm.size() >= limit) {
              continue;
            }
            hit = new IndexHit(new IndexedTerm(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getInt(4) != 0, rs.getString(5), rs.getInt(6) != 0, rs.getInt(7),
                rs.getString(12), rs.getString(13), rs.getString(14)), new ArrayList<>());
            byTerm.put(termId, hit);
          }
          hit.matched().add(new IndexedName(rs.getString(8), rs.getString(9), rs.getString(10)));
        }
      }
    }
    return List.copyOf(byTerm.values());
  }

  /**
   * A page of matches, paged by distinct label rather than by hit, with every hit of those labels.
   *
   * Paging by hit makes collapsing impossible to do honestly. A query for a common term returns the
   * same string from a hundred vocabularies, so a page of twenty-five hits is one or two labels, and
   * a client that folds them can only say "in 24 vocabularies on this page" — it has no way to know
   * what the next page holds. Choosing the labels first and then returning all their hits makes the
   * fold complete: the row can say how many vocabularies offer the label, full stop.
   *
   * The labels are ranked as {@link #search} ranks hits, so the first page holds the same terms it
   * would have.
   */
  public List<IndexHit> searchByLabelPage(String query, Collection<String> acronyms, boolean branchesOnly,
                                          int page, int pageSize) throws SQLException {
    MatchPlan plan = toMatchPlan(query);
    String match = plan.match();
    if (match.isEmpty()) {
      return List.of();
    }
    match = scopedMatch(match, acronyms);
    String residualFilter = residualFilter(plan);
    String heldTokens = String.join(" ", plan.residual());
    String needle = query.trim().toLowerCase(Locale.ROOT);
    String acronymFilter = acronyms == null || acronyms.isEmpty() ? ""
        : " AND t.acronym IN (" + String.join(",", java.util.Collections.nCopies(acronyms.size(), "?")) + ")";
    String branchFilter = branchesOnly ? " AND t.has_children = 1" : "";

    List<String> labels = new ArrayList<>();
    // A label ranks by the best of its names, so a term reached through an exact synonym is not
    // pushed below one reached through a long label that merely contains the query.
    // Branches order by how much is under them, terms by how well they matched. A branch constraint
    // is "everything under this", so what it holds is the thing being chosen between: for "disease",
    // DOID's own disease with 12,220 beneath it is the branch an author means, and a "disease
    // course" with three is not, however exactly it matched. Rank still breaks ties, so a broad
    // branch that merely contains the query cannot displace an exact match of the same size.
    String labelOrder = branchesOnly
        ? "ORDER BY rank, beneath DESC, obsolete, label"
        : "ORDER BY rank, obsolete, len, label";
    String labelSql = "SELECT LOWER(t.pref_label) label, MIN(" + MATCH_RANK + ") rank,"
        + " MIN(t.obsolete) obsolete, MIN(LENGTH(n.value)) len, MAX(t.descendant_count) beneath"
        + " FROM name_fts f JOIN name n ON n.name_id = f.rowid JOIN term t ON t.term_id = n.term_id"
        + " WHERE name_fts MATCH ?" + branchFilter + acronymFilter + residualFilter
        + " GROUP BY LOWER(t.pref_label) " + labelOrder + " LIMIT ? OFFSET ?";
    try (PreparedStatement ps = connection().prepareStatement(labelSql)) {
      int p = 1;
      for (int i = 0; i < MATCH_RANK_PARAMS; i++) {
        ps.setString(p++, needle);
      }
      ps.setString(p++, match);
      if (acronyms != null) {
        for (String acronym : acronyms) {
          ps.setString(p++, acronym);
        }
      }
      if (!plan.residual().isEmpty()) {
        ps.setString(p++, heldTokens);
      }
      ps.setInt(p++, Math.max(pageSize, 1));
      ps.setInt(p, Math.max(page - 1, 0) * Math.max(pageSize, 1));
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          labels.add(rs.getString(1));
        }
      }
    }
    if (labels.isEmpty()) {
      return List.of();
    }

    String hitSql = """
        SELECT t.acronym, t.iri, t.pref_label, t.obsolete, t.replaced_by, t.has_children,
               t.descendant_count, n.property, n.lang, n.value, t.term_id, t.parent_iri, t.parent_label,
               t.definition
        FROM name_fts f
        JOIN name n ON n.name_id = f.rowid
        JOIN term t ON t.term_id = n.term_id
        WHERE name_fts MATCH ?"""
        + branchFilter + acronymFilter + residualFilter
        + " AND LOWER(t.pref_label) IN (" + String.join(",", java.util.Collections.nCopies(labels.size(), "?")) + ")";
    Map<Long, IndexHit> byTerm = new LinkedHashMap<>();
    try (PreparedStatement ps = connection().prepareStatement(hitSql)) {
      int p = 1;
      ps.setString(p++, match);
      if (acronyms != null) {
        for (String acronym : acronyms) {
          ps.setString(p++, acronym);
        }
      }
      if (!plan.residual().isEmpty()) {
        ps.setString(p++, heldTokens);
      }
      for (String label : labels) {
        ps.setString(p++, label);
      }
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          long termId = rs.getLong(11);
          IndexHit hit = byTerm.get(termId);
          if (hit == null) {
            hit = new IndexHit(new IndexedTerm(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getInt(4) != 0, rs.getString(5), rs.getInt(6) != 0, rs.getInt(7),
                rs.getString(12), rs.getString(13), rs.getString(14)), new ArrayList<>());
            byTerm.put(termId, hit);
          }
          hit.matched().add(new IndexedName(rs.getString(8), rs.getString(9), rs.getString(10)));
        }
      }
    }
    // Returned in the label order the ranking chose, so a client folds contiguous runs.
    Map<String, Integer> order = new LinkedHashMap<>();
    for (int i = 0; i < labels.size(); i++) {
      order.put(labels.get(i), i);
    }
    List<IndexHit> hits = new ArrayList<>(byTerm.values());
    hits.sort(Comparator
        .comparingInt((IndexHit h) -> order.getOrDefault(
            h.term().prefLabel() == null ? "" : h.term().prefLabel().toLowerCase(Locale.ROOT), Integer.MAX_VALUE))
        .thenComparing(h -> h.term().acronym())
        .thenComparing(h -> h.term().iri()));
    return List.copyOf(hits);
  }

  /**
   * How well one matched name answers the query, lower being better.
   *
   * The field half of BioPortal's model, which is the half that can be reproduced without a usage
   * signal: what matched matters as much as how much of it matched. An exact hit on the served
   * label beats an exact hit on a synonym, which beats a label that merely starts with the query,
   * and a hidden label — a vocabulary's record of a misspelling — comes last among the names worth
   * matching at all.
   *
   * Deliberately not the whole of it. The measurement behind the term-ordering item found that the
   * field boosts settle the shape of a list and not its head: a common word has far more exact
   * label matches than a page can hold, and BioPortal picks between them with a demand signal —
   * page visits — that CEDAR does not have. This orders equally-good matches by length and then
   * deterministically, and says nothing about which vocabulary deserves the top of the page.
   *
   * `?1` is the lower-cased query, bound twice.
   */
  private static final String MATCH_RANK = """
      CASE
        WHEN n.property = 'prefLabel' AND LOWER(n.value) = ?          THEN 0
        WHEN LOWER(n.value) = ? AND n.property LIKE '%ExactSynonym'   THEN 1
        WHEN LOWER(n.value) = ?                                       THEN 2
        WHEN n.property = 'prefLabel' AND LOWER(n.value) LIKE ? || '%' THEN 3
        WHEN LOWER(n.value) LIKE ? || '%'                             THEN 4
        WHEN n.property = 'prefLabel'                                 THEN 5
        WHEN n.property IN ('skos:hiddenLabel')                       THEN 7
        ELSE 6
      END""";

  /** How many times {@link #MATCH_RANK} needs the query bound. */
  private static final int MATCH_RANK_PARAMS = 5;

  /** How many terms of one vocabulary a query matched. */
  public record VocabularyMatch(String acronym, int matchCount) {}

  /**
   * How many matches a query has, counted rather than inferred from a page.
   *
   * Counting stops at {@code cap}, and the cap is what makes this affordable. Counting every match
   * of a broad query is a full deduplication of the corpus: "cell" takes 3.2 seconds unbounded and
   * 40 milliseconds at ten thousand, measured 2026-08-13. A caller shows the exact number below the
   * cap and says "more than" above it, which is the honest reading either way — nobody scrolls ten
   * thousand rows, so the difference between 10,000 and 300,000 is not a difference a badge can act
   * on.
   *
   * @param distinctLabels count distinct preferred labels rather than terms — the rows a client that
   *                       collapses identical labels will actually render
   */
  public int matchCount(String query, Collection<String> acronyms, boolean distinctLabels, int cap)
      throws SQLException {
    return matchCount(query, acronyms, distinctLabels, false, cap);
  }

  /**
   * @param branchesOnly count only terms that have descendants — the branch results are the class
   *                     results filtered that way, so their count has to be filtered the same way
   */
  public int matchCount(String query, Collection<String> acronyms, boolean distinctLabels,
                        boolean branchesOnly, int cap) throws SQLException {
    String match = toPrefixMatch(query);
    if (match.isEmpty()) {
      return 0;
    }
    MatchPlan plan = toMatchPlan(query);
    match = scopedMatch(match, acronyms);
    String selected = distinctLabels ? "DISTINCT LOWER(t.pref_label)" : "DISTINCT t.term_id";
    // The space matters: a text block drops the newline after its opening delimiter, so without it
    // the column list runs straight into FROM.
    StringBuilder inner = new StringBuilder("SELECT ").append(selected).append(" ").append("""
         FROM name_fts f
         JOIN name n ON n.name_id = f.rowid
         JOIN term t ON t.term_id = n.term_id
         WHERE name_fts MATCH ?""");
    if (branchesOnly) {
      inner.append(" AND t.has_children = 1");
    }
    if (acronyms != null && !acronyms.isEmpty()) {
      inner.append(" AND t.acronym IN (")
          .append(String.join(",", java.util.Collections.nCopies(acronyms.size(), "?")))
          .append(')');
    }
    inner.append(residualFilter(plan));
    inner.append(" LIMIT ?");
    try (PreparedStatement ps = connection().prepareStatement("SELECT COUNT(*) FROM (" + inner + ")")) {
      int p = 1;
      ps.setString(p++, match);
      if (acronyms != null) {
        for (String acronym : acronyms) {
          ps.setString(p++, acronym);
        }
      }
      if (!plan.residual().isEmpty()) {
        ps.setString(p++, String.join(" ", plan.residual()));
      }
      ps.setInt(p, cap);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }

  /**
   * Which vocabularies a query landed in, and how many terms each matched, most first.
   *
   * This is the answer to "which vocabulary should this field draw from", and the query already has
   * it: a group-by over the same match. Note it is not derivable from a page of results — a page is
   * truncated and its sources are whichever happened to rank highest, which undercounts. Measured
   * 2026-08-13, "melanoma" is in 113 vocabularies where the first thousand hits come from 88.
   *
   * {@code cap} bounds how many matched terms are grouped. Grouping every one of them costs time in
   * proportion to how broadly the query matches, and a short prefix matches very broadly. An author
   * reaching for "cell" types "ce" on the way. The cap is the one {@link #matchCount} already
   * applies, and it buys that time with accuracy rather than for free.
   *
   * Which matches fall inside the cap follows the order the index holds them rather than rank, and
   * that order tracks the acronym. A capped facet therefore drops vocabularies whose acronyms sort
   * late and reorders the ones it keeps. Measured 2026-08-25 against a 1,266-ontology store, "ce"
   * fell from 821 vocabularies to 48 and kept one of the uncapped top ten. "disease" fell from 463
   * to 100, also keeping one. "melanoma" matches fewer terms than the cap and came back unchanged.
   * A caller tells an exact facet from a cut one by the total: counts that sum to {@code cap} were
   * cut, and a smaller sum saw the whole match.
   */
  public List<VocabularyMatch> vocabularyFacet(String query, int limit, int cap) throws SQLException {
    String match = toPrefixMatch(query);
    if (match.isEmpty()) {
      return List.of();
    }
    MatchPlan plan = toMatchPlan(query);
    List<VocabularyMatch> out = new ArrayList<>();
    try (PreparedStatement ps = connection().prepareStatement("""
        SELECT acronym, COUNT(*) c FROM (
          SELECT DISTINCT t.acronym, t.term_id
          FROM name_fts f
          JOIN name n ON n.name_id = f.rowid
          JOIN term t ON t.term_id = n.term_id
          WHERE name_fts MATCH ?"""
        + residualFilter(plan) + """
          LIMIT ?)
        GROUP BY acronym
        ORDER BY c DESC, acronym
        LIMIT ?""")) {
      int p = 1;
      ps.setString(p++, match);
      if (!plan.residual().isEmpty()) {
        ps.setString(p++, String.join(" ", plan.residual()));
      }
      ps.setInt(p++, cap);
      ps.setInt(p, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          out.add(new VocabularyMatch(rs.getString(1), rs.getInt(2)));
        }
      }
    }
    return out;
  }

  /**
   * Turns typed text into an FTS5 MATCH expression: every token quoted and prefix-matched.
   *
   * Quoting is not cosmetic. FTS5's query language reads bare {@code -}, {@code *}, {@code "},
   * {@code (}, {@code :} and {@code OR} as syntax, and an ontology search is full of them — "type-2
   * diabetes" would otherwise parse as a NOT.
   */
  /**
   * The single token standing for an ontology in the index, which is its acronym hex-encoded.
   *
   * Encoded rather than written out because the tokenizer splits on punctuation and 191 acronyms
   * carry some, so `RH-MESH` written plainly would answer a search scoped to `MESH`. Stripping the
   * punctuation collides COVID-19 with COVID19, and those hold different terms.
   */
  static String ontToken(String acronym) {
    StringBuilder out = new StringBuilder("o");
    for (byte b : acronym.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
      out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
    }
    return out.toString();
  }

  /**
   * The match expression, narrowed to the ontologies asked for where the index can carry the scope.
   *
   * Kept beside the SQL acronym filter rather than replacing it. The filter is then reached with the
   * rows already narrowed, so it costs nothing, and an index without the column still answers the
   * scope correctly through it alone.
   */
  private String scopedMatch(String match, Collection<String> acronyms) throws SQLException {
    if (match.isEmpty()) {
      return match;
    }
    if (acronyms == null || acronyms.isEmpty() || !scopesInIndex()) {
      return "value: (" + match + ")";
    }
    StringBuilder scope = new StringBuilder();
    for (String acronym : acronyms) {
      if (!scope.isEmpty()) {
        scope.append(" OR ");
      }
      scope.append(ontToken(acronym));
    }
    return "ont: (" + scope + ") AND value: (" + match + ")";
  }

  /** Whether this index carries the ontology alongside the text, so a scope can narrow the match. */
  private boolean scopesInIndex() throws SQLException {
    if (scopedIndex == null) {
      try (Statement st = connection().createStatement()) {
        scopedIndex = hasColumn(st, "name_fts", "ont");
      }
    }
    return scopedIndex;
  }

  /** Cached: an index predating the scoped column filters after the match, as it always did. */
  private Boolean scopedIndex;

  static String toPrefixMatch(String query) {
    return toMatchPlan(query).match();
  }

  /**
   * The tokens a query contributes to the index, folded and split the way the index splits them.
   */
  static List<String> matchTokens(String query) {
    if (query == null) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String token : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
      String cleaned = token.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
      if (cleaned.isEmpty()) {
        continue;
      }
      out.addAll(Arrays.asList(cleaned.split("\\s+")));
    }
    return out;
  }

  private static String prefixExpr(List<String> tokens) {
    StringBuilder out = new StringBuilder();
    for (String token : tokens) {
      if (!out.isEmpty()) {
        out.append(' ');
      }
      out.append('"').append(token).append("\"*");
    }
    return out.toString();
  }

  /**
   * How a query is put to the index: an FTS expression, and the tokens held back from it.
   *
   * A token of one or two characters prefix-matched against the whole index is not a search, it is
   * most of the index: {@code "d"*} reaches seven million names where {@code "mannitol"*} reaches
   * 2,398. Punctuation makes those tokens without anyone typing them, since the index splits on it,
   * so "D-mannitol" asks for {@code "d"* "mannitol"*} and pays for the first. Chemical and strain
   * names are built out of exactly these fragments, which is why adding a term to such a query used
   * to make it slower rather than faster.
   *
   * Holding those tokens back and applying them to the matched names afterwards costs nothing when
   * something else can carry the match, and everything when nothing can: dropping "ca" from
   * "cell ca" leaves {@code "cell"*} to walk a million names alone. So the exchange is made only
   * when a token is long enough to be selective on its own, which leaves every query that has no
   * such token exactly as it was. Measured over 300 labels drawn from six ontologies, the plans
   * agree with the unconditional form on all 300 and take a third of the time.
   */
  record MatchPlan(String match, List<String> residual) {}

  /** Shorter than this, a prefix match is broad enough to dominate the query it appears in. */
  private static final int MIN_SELECTIVE_LENGTH = 3;

  /** Long enough that a prefix match on it can carry a query by itself. */
  private static final int DRIVING_LENGTH = 8;

  static MatchPlan toMatchPlan(String query) {
    List<String> tokens = matchTokens(query);
    if (tokens.isEmpty()) {
      return new MatchPlan("", List.of());
    }
    List<String> held = tokens.stream().filter(t -> t.length() < MIN_SELECTIVE_LENGTH).toList();
    boolean canDrive = tokens.stream().anyMatch(t -> t.length() >= DRIVING_LENGTH);
    if (held.isEmpty() || !canDrive) {
      return new MatchPlan(prefixExpr(tokens), List.of());
    }
    return new MatchPlan(
        prefixExpr(tokens.stream().filter(t -> t.length() >= MIN_SELECTIVE_LENGTH).toList()), held);
  }

  /**
   * Whether a matched name also carries the tokens the plan held back, by the rule the index would
   * have applied to them: some token of the name begins with each of them.
   */
  static boolean carriesResidual(String value, List<String> residual) {
    if (residual.isEmpty()) {
      return true;
    }
    if (value == null) {
      return false;
    }
    List<String> tokens = matchTokens(value);
    for (String held : residual) {
      boolean found = false;
      for (String token : tokens) {
        if (token.startsWith(held)) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  public void setBusyTimeoutMillis(int millis) throws SQLException {
    try (Statement st = connection().createStatement()) {
      st.executeUpdate("PRAGMA busy_timeout = " + millis);
    }
  }

  /** Bulk-load settings. Durability is traded for speed on a file that is rebuilt, never edited. */
  public void applyBulkLoadPragmas() throws SQLException {
    try (Statement st = connection().createStatement()) {
      st.executeUpdate("PRAGMA journal_mode = OFF");
      st.executeUpdate("PRAGMA synchronous = OFF");
      st.executeUpdate("PRAGMA cache_size = -200000");
    }
  }

  @Override
  public void close() throws SQLException {
    if (reads != null) {
      reads.close();
      return;
    }
    connection.close();
  }
}
