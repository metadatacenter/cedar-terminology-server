package org.metadatacenter.terms;

import org.metadatacenter.cedar.terminology.validation.integratedsearch.BranchValueConstraint;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ClassValueConstraint;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.OntologyValueConstraint;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueSetValueConstraint;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotDiff;
import org.metadatacenter.terms.store.SnapshotStore;
import org.metadatacenter.terms.util.ObjectConverter;
import org.metadatacenter.terms.util.Util;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * An {@link ITerminologyService} served from local, version-pinned {@link SnapshotStore} snapshots.
 *
 * This is a partial implementation: it serves the ontology-scoped class hierarchy and lookup
 * operations ("Bucket A") that the snapshot store can answer, and throws
 * {@link UnsupportedOperationException} for everything else. {@link RoutingTerminologyService} is
 * expected to catch that and fall back to the remote backend, so coverage can grow one operation
 * at a time without breaking any request.
 *
 * A {@link SnapshotProvider} maps an ontology acronym to the {@link SnapshotStore} that currently
 * serves it (typically its {@code latest} snapshot, resolved via the catalog). An ontology with no
 * provider entry is not served locally.
 *
 * Fidelity note: the snapshot store currently holds hierarchy plus preferred labels, so the
 * {@link OntologyClass} objects produced here carry id, IRI, prefLabel, ontology, and hasChildren;
 * definitions, synonyms, and relations are left null until the extractor captures them.
 */
public class SqliteTerminologyService implements ITerminologyService {

  /** Resolves the snapshot store that currently serves an ontology, if any. */
  @FunctionalInterface
  public interface SnapshotProvider {
    /** The snapshot serving an ontology at its current ("latest") version, if any. */
    Optional<SnapshotStore> forOntology(String ontology);

    /**
     * The snapshot serving an ontology at a specific version (a {@code version_id} or a tag such as
     * {@code latest}). A null/blank/{@code latest} version means the current one. Default: ignore the
     * version and serve latest — a bare provider is not version-aware.
     */
    default Optional<SnapshotStore> forOntology(String ontology, String version) {
      return forOntology(ontology);
    }

    /**
     * The versions of an ontology the provider knows. Empty by default. Answers a "what versions
     * exist" query.
     */
    default List<OntologyVersion> versions(String ontology) {
      return List.of();
    }

    /**
     * The version triple of an ontology's current ("latest") snapshot, or empty when the provider
     * does not serve it. Empty by default. This is the "resolve current" capability the publish
     * pipeline calls to freeze a value constraint.
     */
    default Optional<VersionTriple> currentVersion(String ontology) {
      return Optional.empty();
    }

    /**
     * The acronym of the served ontology that owns a concept/class IRI (by its namespace), or empty
     * when it cannot be determined unambiguously or is not served. Empty by default. Lets a
     * class-valued constraint be frozen without naming its ontology.
     */
    default Optional<String> ontologyForConceptIri(String conceptIri) {
      return Optional.empty();
    }

    /**
     * The version triple of a value-set collection's current ("latest") snapshot, or empty when the
     * provider does not serve it as a value-set collection. Empty by default. The value-set analogue
     * of {@link #currentVersion}: it lets a value-set-valued constraint be frozen on publish.
     */
    default Optional<VersionTriple> currentVersionForValueSetCollection(String vsCollection) {
      return Optional.empty();
    }

    /**
     * Metadata for the ontologies this provider serves locally — the catalog's own registry, used to
     * answer the ontology-list endpoint without crawling BioPortal. Empty by default (a bare provider
     * that only resolves snapshot stores).
     */
    default List<Ontology> ontologies() {
      return List.of();
    }
  }

  private final SnapshotProvider provider;

  public SqliteTerminologyService(SnapshotProvider provider) {
    this.provider = provider;
  }

  /** Whether this backend currently serves the given ontology. */
  public boolean isAvailable(String ontology) {
    return ontology != null && provider.forOntology(ontology).isPresent();
  }

  private SnapshotStore store(String ontology) {
    return provider.forOntology(ontology)
        .orElseThrow(() -> new UnsupportedOperationException("Ontology not served locally: " + ontology));
  }

  /**
   * The snapshot for an ontology at a pinned version (null/blank/"latest" = current). The failure mode
   * depends on whether a version was explicitly pinned:
   * <ul>
   *   <li>An <em>unpinned</em> (latest) miss throws {@link UnsupportedOperationException} — the ontology
   *       is simply not served locally, so the router routes the request to BioPortal (latest), which is
   *       the right answer for an unpinned request.</li>
   *   <li>An <em>explicit pin</em> miss throws {@link PinnedVersionUnavailableException} — the router
   *       must NOT downgrade this to BioPortal, because BioPortal serves latest and would silently break
   *       the frozen read. A pin that cannot be honored fails loud.</li>
   * </ul>
   */
  private SnapshotStore store(String ontology, String version) {
    Optional<SnapshotStore> snapshot = provider.forOntology(ontology, version);
    if (snapshot.isPresent()) {
      return snapshot.get();
    }
    if (isExplicitPin(version)) {
      throw new PinnedVersionUnavailableException("Pinned version not served locally: " + ontology + "@" + version);
    }
    throw new UnsupportedOperationException("Ontology not served locally: " + ontology);
  }

  /** A version request is an explicit pin unless it is null, blank, or the "latest" sentinel. */
  private static boolean isExplicitPin(String version) {
    return version != null && !version.isBlank() && !CatalogStore.TAG_LATEST.equalsIgnoreCase(version);
  }

  private OntologyClass toClass(SnapshotStore.Concept c, String ontology) {
    return new OntologyClass(Util.getShortIdentifier(c.iri()), c.iri(), c.prefLabel(), null, ontology,
        null, null, null, null, false, null, c.hasChildren());
  }

  /** As {@link #toClass(SnapshotStore.Concept, String)} but also fills the captured synonyms (altLabels
   *  and OBO synonym scopes) and, when {@code lang} is given, the label in that language (falling back to
   *  the served pref_label) — used on the class-detail path. */
  private OntologyClass toClass(SnapshotStore st, SnapshotStore.Concept c, String ontology, String lang)
      throws SQLException {
    List<String> synonyms = st.synonyms(c.iri());
    String label = st.labelInLang(c.iri(), lang).orElse(c.prefLabel());
    return new OntologyClass(Util.getShortIdentifier(c.iri()), c.iri(), label, null, ontology,
        null, synonyms.isEmpty() ? null : synonyms, null, null, false, null, c.hasChildren());
  }

  private PagedResults<OntologyClass> paginate(List<SnapshotStore.Concept> rows, String ontology,
                                               int page, int pageSize) {
    int total = rows.size();
    int pageCount = pageSize <= 0 ? 1 : Math.max(1, (int) Math.ceil((double) total / pageSize));
    int from = pageSize <= 0 ? 0 : Math.max(0, (page - 1) * pageSize);
    int to = pageSize <= 0 ? total : Math.min(total, from + pageSize);
    List<OntologyClass> slice = new ArrayList<>();
    for (int i = from; i < to; i++) {
      slice.add(toClass(rows.get(i), ontology));
    }
    Integer prev = page > 1 ? page - 1 : null;
    Integer next = page < pageCount ? page + 1 : null;
    return new PagedResults<>(page, pageCount, pageSize, total, prev, next, slice);
  }

  /**
   * Paginates a flat list of matched concepts into a {@link SearchResult} page, reproducing
   * BioPortal's single-source contract: {@code totalCount} is the full match count,
   * {@code pageCount} is derived from the requested page size, and the {@code pageSize} field
   * carries the number of items actually on this page (not the requested size). An empty match set
   * yields {@code pageCount = 0} and {@code pageSize = 0}, as BioPortal does.
   */
  private PagedResults<SearchResult> pagedSearchResults(List<SnapshotStore.Concept> rows, String ontology,
                                                        int page, int pageSize) {
    try {
      return pagedSearchResults(rows, ontology, page, pageSize, null, null);
    } catch (SQLException impossible) {
      // The store is read only when lang is non-null; with lang null it is never touched.
      throw new RuntimeException(impossible);
    }
  }

  /**
   * As above, but when {@code lang} is given re-labels each returned row in that language (falling back
   * to its served pref_label), reading from {@code st}. Only the paginated slice is re-labelled, so the
   * per-row label lookup runs at most {@code pageSize} times, not over the whole result set.
   */
  private PagedResults<SearchResult> pagedSearchResults(List<SnapshotStore.Concept> rows, String ontology,
                                                        int page, int pageSize, SnapshotStore st, String lang)
      throws SQLException {
    int total = rows.size();
    if (total == 0) {
      return new PagedResults<>(page, 0, 0, 0, null, null, new ArrayList<>());
    }
    int reqSize = pageSize <= 0 ? total : pageSize;
    int pageCount = (int) Math.ceil((double) total / reqSize);
    int from = Math.max(0, (page - 1) * reqSize);
    int to = Math.min(total, from + reqSize);
    boolean relabel = st != null && lang != null && !lang.isBlank();
    List<SearchResult> slice = new ArrayList<>();
    for (int i = from; i < to; i++) {
      SnapshotStore.Concept c = rows.get(i);
      if (relabel) {
        String label = st.labelInLang(c.iri(), lang).orElse(c.prefLabel());
        c = new SnapshotStore.Concept(c.iri(), label, c.obsolete(), c.hasChildren());
      }
      slice.add(ObjectConverter.toSearchResult(toClass(c, ontology)));
    }
    Integer prev = page > 1 ? page - 1 : null;
    Integer next = page < pageCount ? page + 1 : null;
    return new PagedResults<>(page, pageCount, slice.size(), total, prev, next, slice);
  }

  private static UnsupportedOperationException unsupported(String op) {
    return new UnsupportedOperationException("Not served by the local snapshot backend: " + op);
  }

  /**
   * Class ids arrive URL-encoded from the resource layer (the endpoints declare {@code @Encoded});
   * the local store is keyed by the decoded IRI, so decode before looking up. Decoding is
   * idempotent for already-decoded IRIs (which contain no percent-escapes).
   */
  private static String decodeIri(String id) {
    return id == null ? null : URLDecoder.decode(id, StandardCharsets.UTF_8);
  }

  /**
   * The snapshot acronym for a value-set collection. A vsCollection is usually the bare acronym
   * ({@code NLMVS}, {@code CADSR-VS}) but is sometimes the full registry URL
   * ({@code http://data.bioontology.org/ontologies/CEDARVS}); take the last path segment in that case.
   */
  private static String vsCollectionAcronym(String vsCollection) {
    if (vsCollection == null) {
      return null;
    }
    int slash = vsCollection.lastIndexOf('/');
    return slash >= 0 ? vsCollection.substring(slash + 1) : vsCollection;
  }

  /* --------------------------------------------------------------------------------------------
   * Bucket A — implemented from the snapshot store.
   * ------------------------------------------------------------------------------------------ */

  @Override
  public List<OntologyVersion> getVersions(String ontology) {
    return provider.versions(ontology);
  }

  @Override
  public VersionTriple resolveCurrentVersion(String ontology) {
    return provider.currentVersion(ontology).orElse(null);
  }

  @Override
  public VersionTriple resolveCurrentVersionForClass(String classIri) {
    // classIri -> owning ontology (by namespace) -> that ontology's current triple.
    return provider.ontologyForConceptIri(classIri).flatMap(provider::currentVersion).orElse(null);
  }

  @Override
  public VersionTriple resolveCurrentVersionForValueSetCollection(String vsCollection) {
    return provider.currentVersionForValueSetCollection(vsCollection).orElse(null);
  }

  @Override
  public VersionDiff diffVersions(String ontology, String fromVersion, String toVersion) throws IOException {
    Optional<SnapshotStore> from = provider.forOntology(ontology, fromVersion);
    Optional<SnapshotStore> to = provider.forOntology(ontology, toVersion);
    if (from.isEmpty() || to.isEmpty()) {
      return null; // unknown ontology or version — the resource turns this into a 404
    }
    try {
      SnapshotDiff.Diff d = new SnapshotDiff().diff(from.get(), to.get());
      int cap = 25;
      return new VersionDiff(fromVersion, toVersion,
          d.fromConcepts(), d.toConcepts(), d.addedConcepts().size(), d.removedConcepts().size(),
          d.changedConcepts().size(),
          d.fromEdges(), d.toEdges(), d.addedEdges().size(), d.removedEdges().size(),
          d.fromRelations(), d.toRelations(), d.addedRelations().size(), d.removedRelations().size(),
          d.newlyObsoleted().size(),
          d.addedConcepts().stream().limit(cap).toList(),
          d.removedConcepts().stream().limit(cap).toList(),
          d.changedConcepts().stream().limit(cap).toList(),
          d.summary());
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  @Override
  public OntologyClass findClass(String id, String ontology, String apiKey, String lang) throws IOException {
    try {
      SnapshotStore st = store(ontology);
      Optional<SnapshotStore.Concept> c = st.get(decodeIri(id));
      return c.isPresent() ? toClass(st, c.get(), ontology, lang) : null;
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  @Override
  public OntologyClass findRegularClass(String id, String ontology, String apiKey) throws IOException {
    // All classes in a snapshot are "regular" (non-provisional).
    return findClass(id, ontology, apiKey);
  }

  @Override
  public List<OntologyClass> getRootClasses(String ontologyId, boolean isFlat, String apiKey) throws IOException {
    try {
      List<OntologyClass> out = new ArrayList<>();
      for (SnapshotStore.Concept c : store(ontologyId).rootsDetailed()) {
        out.add(toClass(c, ontologyId));
      }
      return out;
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  @Override
  public PagedResults<OntologyClass> getClassChildren(String id, String ontology, int page, int pageSize,
                                                      String apiKey) throws IOException {
    try {
      return paginate(store(ontology).childrenDetailed(decodeIri(id)), ontology, page, pageSize);
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  @Override
  public PagedResults<OntologyClass> getClassDescendants(String id, String ontology, int page, int pageSize,
                                                         String apiKey) throws IOException {
    try {
      return paginate(store(ontology).descendantsDetailed(decodeIri(id)), ontology, page, pageSize);
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  @Override
  public List<OntologyClass> getClassParents(String id, String ontology, String apiKey) throws IOException {
    try {
      List<OntologyClass> out = new ArrayList<>();
      for (SnapshotStore.Concept c : store(ontology).parentsDetailed(decodeIri(id))) {
        out.add(toClass(c, ontology));
      }
      return out;
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  /* --------------------------------------------------------------------------------------------
   * Not yet served locally — throw so the router falls back to the remote backend.
   * ------------------------------------------------------------------------------------------ */

  @Override
  public List<TreeNode> getClassTree(String id, String ontology, boolean isFlat, String apiKey) {
    throw unsupported("getClassTree");
  }

  @Override
  public Ontology findOntology(String id, boolean includeDetails, String apiKey) {
    // The ontology's own metadata, from the catalog. A non-served ontology throws so the router can
    // fall back to the remote backend (e.g. for its BioPortal isFlat flag).
    return provider.ontologies().stream()
        .filter(o -> id != null && id.equals(o.getId()))
        .findFirst()
        .orElseThrow(() -> unsupported("findOntology: " + id));
  }

  @Override
  public List<Ontology> findAllOntologies(boolean includeDetails, String apiKey) {
    // The ontologies this server versions — reported from the catalog, not by crawling BioPortal.
    return provider.ontologies();
  }

  @Override
  public PagedResults<OntologyClass> findAllClassesInOntology(String ontology, int page, int pageSize, String apiKey)
      throws IOException {
    // Whole-ontology enumeration — backs the picker's "select the whole ontology" dropdown, which
    // opens with an empty query. Ordered by IRI for a deterministic page sequence.
    try {
      return paginate(store(ontology).allConceptsDetailed(), ontology, page, pageSize);
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  @Override
  public List<OntologyProperty> getRootProperties(String ontologyId, String apiKey) {
    throw unsupported("getRootProperties");
  }

  @Override
  public OntologyProperty findProperty(String id, String ontology, String apiKey) {
    throw unsupported("findProperty");
  }

  @Override
  public List<OntologyProperty> findAllPropertiesInOntology(String ontology, String apiKey) {
    throw unsupported("findAllPropertiesInOntology");
  }

  @Override
  public List<TreeNode> getPropertyTree(String id, String ontology, String apiKey) {
    throw unsupported("getPropertyTree");
  }

  @Override
  public List<OntologyProperty> getPropertyChildren(String id, String ontology, String apiKey) {
    throw unsupported("getPropertyChildren");
  }

  @Override
  public List<OntologyProperty> getPropertyDescendants(String id, String ontology, String apiKey) {
    throw unsupported("getPropertyDescendants");
  }

  @Override
  public List<OntologyProperty> getPropertyParents(String id, String ontology, String apiKey) {
    throw unsupported("getPropertyParents");
  }

  @Override
  public Value findRegularValue(String id, String ontology, String apiKey) {
    throw unsupported("findRegularValue");
  }

  @Override
  public Value findValue(String id, String ontology, String apiKey) {
    throw unsupported("findValue");
  }

  @Override
  public PagedResults<Value> findAllValuesInValueSetByValue(String id, String ontology, int page, int pageSize,
                                                            String apiKey) {
    throw unsupported("findAllValuesInValueSetByValue");
  }

  /**
   * Class search over a single locally-served ontology — the primitive behind the picker's term
   * search and the type-ahead autocomplete. Two shapes are served:
   * <ul>
   *   <li>ontology-scoped: a single acronym in {@code sources}, matching labels across the ontology;</li>
   *   <li>branch-scoped: {@code source} + {@code subtreeRootId}, matching labels within that class
   *       and its descendants.</li>
   * </ul>
   * Everything else falls through to the remote backend by throwing {@link UnsupportedOperationException}:
   * non-class scopes (value sets, values, properties), multi-source searches, and the {@code all}
   * scope (which would also need value sets).
   *
   * <p>Limitations, deliberately left to the equivalence harness to quantify: matching is a plain
   * case-insensitive substring on the preferred label, not BioPortal's Solr behaviour, so
   * {@code suggest} is treated the same as a normal search; and {@code maxDepth} is not honoured —
   * a branch search returns the whole subtree.
   */
  @Override
  public PagedResults<SearchResult> search(String q, List<String> scope, List<String> sources, boolean suggest,
                                           String source, String subtreeRootId, int maxDepth, int page, int pageSize,
                                           boolean displayContext, boolean displayLinks, String apiKey,
                                           List<String> valueSetsIds) throws IOException {
    boolean classOnly = scope != null && !scope.isEmpty()
        && scope.stream().allMatch(s -> s.equalsIgnoreCase("classes"));
    if (!classOnly) {
      throw unsupported("search (only class-scoped search is served locally)");
    }
    String query = q == null ? "" : q.trim();
    try {
      List<SnapshotStore.Concept> rows;
      String ontology;
      if (subtreeRootId != null && !subtreeRootId.isEmpty()) {
        ontology = source;
        rows = store(ontology).searchByLabelUnderRoot(subtreeRootId, query, false, 0);
      } else {
        if (sources == null || sources.size() != 1) {
          throw unsupported("search (expects a single ontology source)");
        }
        ontology = sources.get(0);
        rows = store(ontology).searchByLabel(query, false, 0);
      }
      return pagedSearchResults(rows, ontology, page, pageSize);
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  @Override
  public PagedResults<SearchResult> propertySearch(String q, List<String> sources, boolean exactMatch,
                                                   boolean requireDefinitions, int page, int pageSize,
                                                   boolean displayContext, boolean displayLinks, String apiKey) {
    throw unsupported("propertySearch");
  }

  /**
   * The CEDAR Embeddable Editor's single terminology call: search for conforming values given a
   * controlled-term field's {@code valueConstraints} and any typed text. Served locally for the
   * single-source shapes the snapshot can answer:
   * <ul>
   *   <li>a single ontology — enumerate on empty text, else label search;</li>
   *   <li>a single branch (class + descendants) — label search within the subtree;</li>
   *   <li>an explicit set of enumerated classes — filtered and sorted by preferred label
   *       (self-contained, needs no snapshot).</li>
   * </ul>
   * Value sets, multiple sources, and mixed constraints throw so the router falls back to BioPortal.
   */
  @Override
  public PagedResults<SearchResult> integratedSearch(Optional<String> q, ValueConstraints valueConstraints, int page,
                                                     int pageSize, String apiKey, String lang) throws IOException {
    if (valueConstraints == null) {
      throw unsupported("integratedSearch (no value constraints)");
    }
    List<OntologyValueConstraint> ontologies = valueConstraints.getOntologies();
    List<BranchValueConstraint> branches = valueConstraints.getBranches();
    List<ValueSetValueConstraint> valueSets = valueConstraints.getValueSets();
    List<ClassValueConstraint> classes = valueConstraints.getClasses();
    boolean hasOntologies = ontologies != null && !ontologies.isEmpty();
    boolean hasBranches = branches != null && !branches.isEmpty();
    boolean hasValueSets = valueSets != null && !valueSets.isEmpty();
    boolean hasClasses = classes != null && !classes.isEmpty();

    // A single value set: its values are the children of the value-set class in its vsCollection
    // snapshot — BioPortal serves the same via GET /ontologies/{vsCollection}/classes/{vsId}/children.
    // Enumerate (empty text) or filter by preferred-label substring, sorted by preferred label as
    // BioPortal does. The vsCollection is served like any ontology snapshot.
    if (hasValueSets && valueSets.size() == 1 && !hasOntologies && !hasBranches && !hasClasses) {
      ValueSetValueConstraint vs = valueSets.get(0);
      String acronym = vsCollectionAcronym(vs.getVsCollection());
      try {
        SnapshotStore st = store(acronym, vs.getVersion());
        List<SnapshotStore.Concept> values = new ArrayList<>(st.childrenDetailed(decodeIri(vs.getUri())));
        String query = q.map(String::trim).orElse("");
        if (!query.isEmpty()) {
          String needle = query.toLowerCase();
          values.removeIf(c -> c.prefLabel() == null || !c.prefLabel().toLowerCase().contains(needle));
        }
        values.sort(Comparator.comparing(c -> c.prefLabel() == null ? "" : c.prefLabel(),
            String.CASE_INSENSITIVE_ORDER));
        return pagedSearchResults(values, acronym, page, pageSize, st, lang);
      } catch (SQLException e) {
        throw new IOException(e);
      }
    }
    if (hasValueSets) {
      throw unsupported("integratedSearch (value sets: multi-source or mixed)");
    }
    // Enumerated classes on their own — self-contained, no snapshot needed.
    if (hasClasses && !hasOntologies && !hasBranches) {
      return integratedSearchEnumeratedClasses(q, classes, page, pageSize);
    }
    // A single ontology — enumerate on empty text, else label-search — served at the constraint's
    // pinned version (null = latest). Resolved here rather than via the public search() so the version
    // can be honored; the picker's live search() stays latest.
    if (hasOntologies && ontologies.size() == 1 && !hasBranches && !hasClasses) {
      OntologyValueConstraint ont = ontologies.get(0);
      String query = q.map(String::trim).orElse("");
      try {
        SnapshotStore st = store(ont.getAcronym(), ont.getVersion());
        List<SnapshotStore.Concept> rows =
            query.isEmpty() ? st.allConceptsDetailed() : st.searchByLabel(query, false, 0);
        return pagedSearchResults(rows, ont.getAcronym(), page, pageSize, st, lang);
      } catch (SQLException e) {
        throw new IOException(e);
      }
    }
    // A single branch (class + descendants) at the constraint's pinned version. The branch root IRI is
    // decoded first: some ontologies (e.g. GDMT) store it percent-encoded and BioPortal decodes before
    // matching; a normal IRI has nothing to decode.
    if (hasBranches && branches.size() == 1 && !hasOntologies && !hasClasses) {
      BranchValueConstraint branch = branches.get(0);
      String query = q.map(String::trim).orElse("");
      try {
        SnapshotStore st = store(branch.getAcronym(), branch.getVersion());
        List<SnapshotStore.Concept> rows =
            st.searchByLabelUnderRoot(decodeIri(branch.getUri()), query, false, 0);
        return pagedSearchResults(rows, branch.getAcronym(), page, pageSize, st, lang);
      } catch (SQLException e) {
        throw new IOException(e);
      }
    }
    throw unsupported("integratedSearch (multi-source or mixed constraints)");
  }

  /**
   * Filters an explicit set of enumerated classes by preferred label (case-insensitive substring),
   * sorted by preferred label as BioPortal does, then paginates. The classes are self-describing, so
   * this needs no snapshot.
   */
  private PagedResults<SearchResult> integratedSearchEnumeratedClasses(Optional<String> q,
                                                                       List<ClassValueConstraint> classes,
                                                                       int page, int pageSize) {
    String query = q.map(String::trim).map(String::toLowerCase).orElse("");
    List<SearchResult> all = new ArrayList<>();
    classes.stream()
        .filter(c -> c.getPrefLabel() != null && c.getPrefLabel().toLowerCase().contains(query))
        .sorted(Comparator.comparing(ClassValueConstraint::getPrefLabel, String.CASE_INSENSITIVE_ORDER))
        .forEach(c -> all.add(ObjectConverter.toSearchResult(c)));
    return pageOf(all, page, pageSize);
  }

  /** Paginates an already-built, ordered result list using BioPortal's single-source contract. */
  private PagedResults<SearchResult> pageOf(List<SearchResult> all, int page, int pageSize) {
    int total = all.size();
    if (total == 0) {
      return new PagedResults<>(page, 0, 0, 0, null, null, new ArrayList<>());
    }
    int reqSize = pageSize <= 0 ? total : pageSize;
    int pageCount = (int) Math.ceil((double) total / reqSize);
    int from = Math.min(Math.max(0, (page - 1) * reqSize), total);
    int to = Math.min(total, from + reqSize);
    List<SearchResult> slice = new ArrayList<>(all.subList(from, to));
    Integer prev = page > 1 ? page - 1 : null;
    Integer next = page < pageCount ? page + 1 : null;
    return new PagedResults<>(page, pageCount, slice.size(), total, prev, next, slice);
  }

  @Override
  public PagedResults<SearchResult> integratedRetrieve(ValueConstraints valueConstraints, int page, int pageSize,
                                                       String apiKey) {
    throw unsupported("integratedRetrieve");
  }

  @Override
  public OntologyClass createProvisionalClass(OntologyClass c, String apiKey) {
    throw unsupported("createProvisionalClass");
  }

  @Override
  public OntologyClass findProvisionalClass(String id, String apiKey) {
    throw unsupported("findProvisionalClass");
  }

  @Override
  public PagedResults<OntologyClass> findAllProvisionalClasses(String ontology, int page, int pageSize, String apiKey) {
    throw unsupported("findAllProvisionalClasses");
  }

  @Override
  public void updateProvisionalClass(OntologyClass c, String apiKey) {
    throw unsupported("updateProvisionalClass");
  }

  @Override
  public void deleteProvisionalClass(String id, String apiKey) {
    throw unsupported("deleteProvisionalClass");
  }

  @Override
  public Relation createProvisionalRelation(Relation relation, String apiKey) {
    throw unsupported("createProvisionalRelation");
  }

  @Override
  public Relation findProvisionalRelation(String id, String apiKey) {
    throw unsupported("findProvisionalRelation");
  }

  @Override
  public void deleteProvisionalRelation(String id, String apiKey) {
    throw unsupported("deleteProvisionalRelation");
  }

  @Override
  public ValueSet createProvisionalValueSet(ValueSet vs, String apiKey) {
    throw unsupported("createProvisionalValueSet");
  }

  @Override
  public ValueSet findProvisionalValueSet(String id, String apiKey) {
    throw unsupported("findProvisionalValueSet");
  }

  @Override
  public ValueSet findRegularValueSet(String id, String vsCollection, String apiKey) {
    throw unsupported("findRegularValueSet");
  }

  @Override
  public ValueSet findValueSet(String id, String vsCollection, String apiKey) {
    throw unsupported("findValueSet");
  }

  @Override
  public ValueSet findValueSetByValue(String id, String vsCollection, String apiKey) {
    throw unsupported("findValueSetByValue");
  }

  @Override
  public void updateProvisionalValueSet(ValueSet vs, String apiKey) {
    throw unsupported("updateProvisionalValueSet");
  }

  @Override
  public void deleteProvisionalValueSet(String id, String apiKey) {
    throw unsupported("deleteProvisionalValueSet");
  }

  @Override
  public PagedResults<ValueSet> findValueSetsByVsCollection(String vsCollection, int page, int pageSize, String apiKey) {
    throw unsupported("findValueSetsByVsCollection");
  }

  @Override
  public List<ValueSet> findAllValueSets(String apiKey) {
    throw unsupported("findAllValueSets");
  }

  @Override
  public PagedResults<Value> findValuesByValueSet(String vsId, String vsCollection, int page, int pageSize,
                                                  String apiKey) {
    throw unsupported("findValuesByValueSet");
  }

  @Override
  public List<ValueSetCollection> findAllVSCollections(boolean includeDetails, String apiKey) {
    throw unsupported("findAllVSCollections");
  }

  @Override
  public Value createProvisionalValue(Value v, String apiKey) {
    throw unsupported("createProvisionalValue");
  }

  @Override
  public Value findProvisionalValue(String id, String apiKey) {
    throw unsupported("findProvisionalValue");
  }

  @Override
  public void updateProvisionalValue(Value v, String apiKey) {
    throw unsupported("updateProvisionalValue");
  }

  @Override
  public void deleteProvisionalValue(String id, String apiKey) {
    throw unsupported("deleteProvisionalValue");
  }

  @Override
  public TreeNode getValueTree(String id, String vsCollection, String apiKey) {
    throw unsupported("getValueTree");
  }

  @Override
  public TreeNode getValueSetTree(String id, String vsCollection, String apiKey) {
    throw unsupported("getValueSetTree");
  }
}
