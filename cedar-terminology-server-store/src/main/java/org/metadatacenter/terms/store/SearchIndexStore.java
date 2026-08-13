package org.metadatacenter.terms.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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

  /** A term as the index holds it, with the structural facts a result row needs. */
  public record IndexedTerm(
      String acronym,
      String iri,
      String prefLabel,
      boolean obsolete,
      String replacedBy,
      boolean hasChildren,
      int descendantCount) {}

  /** A searchable name of a term: its preferred label, a synonym, or a label in another language. */
  public record IndexedName(String property, String lang, String value) {}

  /** A hit: the term, and the names of it that matched. */
  public record IndexHit(IndexedTerm term, List<IndexedName> matched) {}

  private final Connection connection;

  private SearchIndexStore(Connection connection) {
    this.connection = connection;
  }

  public static SearchIndexStore openFile(String path) throws SQLException {
    return new SearchIndexStore(DriverManager.getConnection("jdbc:sqlite:" + path));
  }

  public static SearchIndexStore openInMemory() throws SQLException {
    return new SearchIndexStore(DriverManager.getConnection("jdbc:sqlite::memory:"));
  }

  public void initSchema() throws SQLException {
    try (Statement st = connection.createStatement()) {
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
            descendant_count INTEGER NOT NULL DEFAULT 0
          )""");
      st.executeUpdate("CREATE INDEX IF NOT EXISTS term_by_acronym ON term(acronym)");
      st.executeUpdate("""
          CREATE TABLE IF NOT EXISTS name (
            name_id  INTEGER PRIMARY KEY,
            term_id  INTEGER NOT NULL,
            property TEXT,
            lang     TEXT,
            value    TEXT NOT NULL
          )""");
      st.executeUpdate("CREATE INDEX IF NOT EXISTS name_by_term ON name(term_id)");
      // External-content FTS: the searchable text lives once, in `name`, and the index refers to it
      // by rowid. Storing it twice would add a gigabyte to say the same thing.
      //
      // remove_diacritics 2 folds accents, so "aquifere" finds "aquifère" — which the snapshot's
      // LIKE cannot do, since SQLite folds ASCII case only.
      st.executeUpdate("""
          CREATE VIRTUAL TABLE IF NOT EXISTS name_fts USING fts5(
            value,
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
  public Optional<String> indexedVersion(String acronym) throws SQLException {
    try (PreparedStatement ps =
             connection.prepareStatement("SELECT version_id FROM indexed_snapshot WHERE acronym = ?")) {
      ps.setString(1, acronym);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
      }
    }
  }

  /** Every ontology in the index, with the version held for each. */
  public Map<String, String> indexedVersions() throws SQLException {
    Map<String, String> out = new LinkedHashMap<>();
    try (Statement st = connection.createStatement();
         ResultSet rs = st.executeQuery("SELECT acronym, version_id FROM indexed_snapshot ORDER BY acronym")) {
      while (rs.next()) {
        out.put(rs.getString(1), rs.getString(2));
      }
    }
    return out;
  }

  public int indexedOntologyCount() throws SQLException {
    try (Statement st = connection.createStatement();
         ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM indexed_snapshot")) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  public long termCount() throws SQLException {
    try (Statement st = connection.createStatement();
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
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      deleteOntologyRows(acronym);
      try (PreparedStatement insertTerm = connection.prepareStatement(
               "INSERT INTO term(acronym, iri, pref_label, obsolete, replaced_by, has_children, descendant_count) "
                   + "VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
           PreparedStatement insertName = connection.prepareStatement(
               "INSERT INTO name(term_id, property, lang, value) VALUES (?,?,?,?)")) {
        for (IndexedTerm t : terms) {
          insertTerm.setString(1, acronym);
          insertTerm.setString(2, t.iri());
          insertTerm.setString(3, t.prefLabel());
          insertTerm.setInt(4, t.obsolete() ? 1 : 0);
          insertTerm.setString(5, t.replacedBy());
          insertTerm.setInt(6, t.hasChildren() ? 1 : 0);
          insertTerm.setInt(7, t.descendantCount());
          insertTerm.executeUpdate();
          long termId;
          try (ResultSet keys = insertTerm.getGeneratedKeys()) {
            termId = keys.next() ? keys.getLong(1) : -1;
          }
          // The preferred label is a searchable name like any other, so a plain label match needs no
          // separate query path.
          if (t.prefLabel() != null && !t.prefLabel().isBlank()) {
            addName(insertName, termId, "prefLabel", null, t.prefLabel());
          }
          for (IndexedName n : namesByIri.getOrDefault(t.iri(), List.of())) {
            addName(insertName, termId, n.property(), n.lang(), n.value());
          }
        }
        insertName.executeBatch();
      }
      try (PreparedStatement ps = connection.prepareStatement(
          "INSERT OR REPLACE INTO indexed_snapshot(acronym, version_id, term_count, indexed_at) VALUES (?,?,?,?)")) {
        ps.setString(1, acronym);
        ps.setString(2, versionId);
        ps.setInt(3, terms.size());
        ps.setString(4, indexedAt);
        ps.executeUpdate();
      }
      connection.commit();
    } catch (SQLException e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(autoCommit);
    }
  }

  private static void addName(PreparedStatement insertName, long termId, String property, String lang,
                              String value) throws SQLException {
    insertName.setLong(1, termId);
    insertName.setString(2, property);
    insertName.setString(3, lang);
    insertName.setString(4, value);
    insertName.addBatch();
  }

  private void deleteOntologyRows(String acronym) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "DELETE FROM name WHERE term_id IN (SELECT term_id FROM term WHERE acronym = ?)")) {
      ps.setString(1, acronym);
      ps.executeUpdate();
    }
    try (PreparedStatement ps = connection.prepareStatement("DELETE FROM term WHERE acronym = ?")) {
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
    try (Statement st = connection.createStatement()) {
      st.executeUpdate("INSERT INTO name_fts(name_fts) VALUES('rebuild')");
    }
  }

  /** Reclaims space and updates the planner's statistics. Worth running once after a full build. */
  public void optimize() throws SQLException {
    try (Statement st = connection.createStatement()) {
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
    String match = toPrefixMatch(query);
    if (match.isEmpty()) {
      return List.of();
    }
    String needle = query.trim().toLowerCase(Locale.ROOT);
    StringBuilder sql = new StringBuilder("""
        SELECT t.acronym, t.iri, t.pref_label, t.obsolete, t.replaced_by, t.has_children,
               t.descendant_count, n.property, n.lang, n.value, t.term_id
        FROM name_fts f
        JOIN name n ON n.name_id = f.rowid
        JOIN term t ON t.term_id = n.term_id
        WHERE name_fts MATCH ?""");
    if (acronyms != null && !acronyms.isEmpty()) {
      sql.append(" AND t.acronym IN (")
          .append(String.join(",", java.util.Collections.nCopies(acronyms.size(), "?")))
          .append(')');
    }
    // Ranked on the name that matched, not on the term's preferred label, and ranked here rather
    // than by the caller. The limit truncates before a caller can reorder, so ordering by label
    // length alone fills the cap with the shortest labels in the corpus — the numeric codes of
    // coded vocabularies — and drops the terms actually named after the query.
    //
    // Exact name, then a name starting with the query, then the rest; shortest first within each.
    // Not the ranking the search-ordering work will bring, which weighs match type against a demand
    // signal. Enough that the cap holds candidates worth ranking.
    sql.append("""
         ORDER BY CASE
                    WHEN LOWER(n.value) = ? THEN 0
                    WHEN LOWER(n.value) LIKE ? || '%' THEN 1
                    ELSE 2
                  END,
                  LENGTH(n.value), n.value, t.iri
         LIMIT ?""");

    Map<Long, IndexHit> byTerm = new LinkedHashMap<>();
    try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
      int p = 1;
      ps.setString(p++, match);
      if (acronyms != null) {
        for (String acronym : acronyms) {
          ps.setString(p++, acronym);
        }
      }
      ps.setString(p++, needle);
      ps.setString(p++, needle);
      // Names, not terms: one term can match several of its names, so ask for enough rows to fill
      // the requested number of terms.
      ps.setInt(p, Math.max(limit, 1) * 4);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          long termId = rs.getLong(11);
          IndexHit hit = byTerm.get(termId);
          if (hit == null) {
            if (byTerm.size() >= limit) {
              continue;
            }
            hit = new IndexHit(new IndexedTerm(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getInt(4) != 0, rs.getString(5), rs.getInt(6) != 0, rs.getInt(7)), new ArrayList<>());
            byTerm.put(termId, hit);
          }
          hit.matched().add(new IndexedName(rs.getString(8), rs.getString(9), rs.getString(10)));
        }
      }
    }
    return List.copyOf(byTerm.values());
  }

  /**
   * Turns typed text into an FTS5 MATCH expression: every token quoted and prefix-matched.
   *
   * Quoting is not cosmetic. FTS5's query language reads bare {@code -}, {@code *}, {@code "},
   * {@code (}, {@code :} and {@code OR} as syntax, and an ontology search is full of them — "type-2
   * diabetes" would otherwise parse as a NOT.
   */
  static String toPrefixMatch(String query) {
    if (query == null) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    for (String token : query.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
      String cleaned = token.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
      if (cleaned.isEmpty()) {
        continue;
      }
      for (String part : cleaned.split("\\s+")) {
        if (!out.isEmpty()) {
          out.append(' ');
        }
        out.append('"').append(part).append("\"*");
      }
    }
    return out.toString();
  }

  public void setBusyTimeoutMillis(int millis) throws SQLException {
    try (Statement st = connection.createStatement()) {
      st.executeUpdate("PRAGMA busy_timeout = " + millis);
    }
  }

  /** Bulk-load settings. Durability is traded for speed on a file that is rebuilt, never edited. */
  public void applyBulkLoadPragmas() throws SQLException {
    try (Statement st = connection.createStatement()) {
      st.executeUpdate("PRAGMA journal_mode = OFF");
      st.executeUpdate("PRAGMA synchronous = OFF");
      st.executeUpdate("PRAGMA cache_size = -200000");
    }
  }

  @Override
  public void close() throws SQLException {
    connection.close();
  }
}
