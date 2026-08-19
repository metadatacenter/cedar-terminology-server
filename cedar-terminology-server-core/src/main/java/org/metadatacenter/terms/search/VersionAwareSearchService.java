package org.metadatacenter.terms.search;

import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.search.SearchRequest.SourceSelector;
import org.metadatacenter.terms.search.SearchResponse.BranchHit;
import org.metadatacenter.terms.search.SearchResponse.ClassHit;
import org.metadatacenter.terms.search.SearchResponse.Hit;
import org.metadatacenter.terms.search.SearchResponse.MatchedLabel;
import org.metadatacenter.terms.search.SearchResponse.OntologyHit;
import org.metadatacenter.terms.search.SearchResponse.SourceBlock;
import org.metadatacenter.terms.search.SearchResponse.TermRef;
import org.metadatacenter.terms.search.SearchResponse.TypeResults;
import org.metadatacenter.terms.search.SearchResponse.ValueSetHit;
import org.metadatacenter.terms.search.SearchResponse.VersionInfo;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SearchIndexStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Searches the local terminology store at a named version or the current one, across the constraint
 * types a controlled-term field can carry.
 *
 * The design, and why this is not a parameter on {@code /bioportal/search}, is in
 * {@code cedar-development/ops/VERSIONING-ROADMAP.md}, "The Search API". In short: that route takes no version, so
 * an author who pins a constraint to an older ontology and then searches is searching the current
 * one, and can select a term the pinned version does not contain.
 *
 * <p>Two limits of this implementation, both reported rather than hidden:
 * <ul>
 *   <li>Only locally-served sources are searched. A source the store does not hold is reported
 *       {@code unavailable}, never quietly omitted — proxying it to BioPortal is designed but not
 *       built, and reporting it as {@code proxied} while returning none of its terms would be the
 *       silent wrong answer this endpoint exists to prevent.</li>
 *   <li>A search names its sources. Searching the whole served corpus would mean opening and
 *       querying every snapshot in the catalog, one SQLite file at a time, so it needs a
 *       cross-snapshot index rather than a loop.</li>
 * </ul>
 */
public class VersionAwareSearchService {

  /** How many matches of one type are counted before the count becomes a ceiling. */
  static final int COUNT_CAP = 1000;

  /**
   * Where counting a corpus-wide match stops.
   *
   * Exact below it, "more than" above. Counting every match of a broad query means deduplicating a
   * large part of the corpus — "cell" takes 3.2 seconds unbounded and 40 milliseconds here, measured
   * 2026-08-13 — and nobody acts on the difference between ten thousand rows and three hundred
   * thousand.
   */
  static final int FACET_CAP = 10_000;

  /**
   * The shortest corpus-wide query the server will answer.
   *
   * A single character matches a large fraction of 24 million names, and the cost is in reaching the
   * cap rather than in the cap itself: "a" took 18.6 seconds where "melanoma" takes 0.11, measured
   * 2026-08-13. Two characters is not a filter on what an author can look for — it is the point at
   * which a search of everything is a search rather than an enumeration.
   */
  static final int MIN_CORPUS_QUERY_LENGTH = 2;

  /**
   * How many vocabularies the ontology results can name.
   *
   * Generous, because the facet is a group-by over the match either way and the limit only trims its
   * output — and because every name match needs its count from the same map, however far down the
   * ranking it sits.
   */
  private static final int VOCABULARY_FACET_LIMIT = 1000;

  /** Deep enough for any vocabulary the store holds, and a bound on a hierarchy that cycles. */
  private static final int MAX_ANCESTOR_DEPTH = 32;

  /** Children shown at once. A SNOMED node can have hundreds; the count says how many were left. */
  private static final int CHILD_LIMIT = 50;

  /** How many descendants a branch row illustrates. */
  private static final int EXAMPLE_COUNT = 3;

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 200;

  private final CatalogSnapshotProvider provider;
  private final SearchIndexStore index;

  public VersionAwareSearchService(CatalogSnapshotProvider provider) {
    this(provider, null);
  }

  /**
   * @param index the cross-snapshot index, or null when none is configured. Without it a search must
   *              name its sources: the alternative is opening every snapshot in the catalog per query.
   */
  public VersionAwareSearchService(CatalogSnapshotProvider provider, SearchIndexStore index) {
    this.provider = provider;
    this.index = index;
  }

  /** A request the server will not answer, as opposed to one whose answer is empty. */
  public static class BadSearchRequestException extends RuntimeException {
    public BadSearchRequestException(String message) {
      super(message);
    }
  }

  public SearchResponse search(SearchRequest request) throws SQLException {
    List<String> types = validatedTypes(request.typesOrAll());
    String query = request.queryOrEmpty();
    int page = Math.max(1, request.page() == null ? 1 : request.page());
    int pageSize = clampPageSize(request.pageSize());

    List<Resolved> resolved = resolveSources(request.sourcesOrEmpty(), request.wantsVersions());
    // Keyed by acronym so an ontology hit can add the source it names without duplicating a block
    // the request already asked for.
    Map<String, SourceBlock> blocks = new LinkedHashMap<>();
    for (Resolved r : resolved) {
      blocks.putIfAbsent(r.acronym(), r.block());
    }
    List<Resolved> searchable = resolved.stream().filter(Resolved::isLocal).toList();

    // The index answers whenever nothing is pinned, whether or not sources are named. Narrowing to
    // an ontology is not a different kind of search — it is the same search over less — so it keeps
    // the index's matching, its label paging and its counts. Only a pinned version has to leave the
    // index, because the index holds current versions and no others.
    boolean pinned = request.sourcesOrEmpty().stream().anyMatch(s -> s.version() != null);
    List<String> scope = request.sourcesOrEmpty().stream()
        .map(SourceSelector::sourceAcronym)
        .filter(java.util.Objects::nonNull)
        .toList();
    boolean useIndex = index != null && !pinned;
    boolean corpusWide = useIndex && scope.isEmpty();
    if (corpusWide && query.length() < MIN_CORPUS_QUERY_LENGTH) {
      throw new BadSearchRequestException(query.isEmpty()
          ? "A corpus-wide search needs a query."
          : "A corpus-wide search needs at least " + MIN_CORPUS_QUERY_LENGTH
              + " characters. Name a source to search it with fewer.");
    }
    if (!useIndex && searchable.isEmpty() && needsASource(types)) {
      throw new BadSearchRequestException(needsASourceMessage(types));
    }
    // A value set is reached through the collection that holds it, which the index does not record.
    // A corpus-wide request therefore searches every collection the catalog knows — a bounded set,
    // and one the author should not have to name to be shown what is in it.
    List<Resolved> collections = corpusWide && types.contains(SearchRequest.TYPE_VALUE_SET)
        ? valueSetCollections()
        : List.of();
    for (Resolved collection : collections) {
      blocks.putIfAbsent(collection.acronym(), collection.block());
    }

    Map<String, TypeResults> results = new LinkedHashMap<>();
    for (String type : SearchRequest.ALL_TYPES) {
      if (!types.contains(type)) {
        continue;
      }
      List<? extends Hit> all = switch (type) {
        case SearchRequest.TYPE_ONTOLOGY -> ontologyHits(query, resolved, request.ordersOntologiesByMatches());
        case SearchRequest.TYPE_CLASS -> useIndex
            ? corpusHits(query, false, page, pageSize, scope) : classHits(query, searchable, request.lang());
        case SearchRequest.TYPE_BRANCH -> useIndex
            ? corpusHits(query, true, page, pageSize, scope) : branchHits(query, searchable, request.lang());
        case SearchRequest.TYPE_VALUE_SET -> valueSetHits(query, corpusWide ? collections : searchable);
        default -> List.of();
      };
      if (useIndex) {
        // The block must report the version the index holds, not the catalog's current one: a
        // re-ingested ontology the index has not caught up with was searched at the older snapshot,
        // and saying otherwise would attribute results to a version that did not produce them.
        for (Hit hit : all) {
          if (!blocks.containsKey(hit.sourceAcronym())) {
            blocks.put(hit.sourceAcronym(), indexedSourceBlock(hit.sourceAcronym()));
          }
        }
      }
      if (SearchRequest.TYPE_ONTOLOGY.equals(type)) {
        // An ontology hit is thin because its source block carries the rest, so the block has to
        // exist. Without this a client is handed an acronym and no way to learn its name or IRI —
        // which is precisely what a request that named no sources gets.
        for (Hit hit : all) {
          if (!blocks.containsKey(hit.sourceAcronym())) {
            blocks.put(hit.sourceAcronym(), resolveSource(
                new SourceSelector(hit.sourceSystem(), hit.sourceAcronym(), null)).block());
          }
        }
      }
      boolean facetable = useIndex
          && (SearchRequest.TYPE_CLASS.equals(type) || SearchRequest.TYPE_BRANCH.equals(type));
      results.put(type, facetable
          ? facetedPage(query, SearchRequest.TYPE_BRANCH.equals(type), all, page, pageSize, scope)
          : paged(all, page, pageSize));
    }
    return new SearchResponse(query, List.copyOf(blocks.values()), results);
  }

  /* ----------------------------------------------------------------------------------------------
   * Sources
   * ------------------------------------------------------------------------------------------- */

  /** A requested source, resolved to what will answer for it — or to why nothing will. */
  private record Resolved(SourceBlock block, String acronym, SnapshotStore store) {
    boolean isLocal() {
      return store != null;
    }
  }

  private List<Resolved> resolveSources(List<SourceSelector> selectors, boolean withVersions)
      throws SQLException {
    List<Resolved> out = new ArrayList<>();
    java.util.Set<String> seen = new java.util.LinkedHashSet<>();
    for (SourceSelector selector : selectors) {
      // One version per source per request. A hit names its source with the system and acronym pair
      // and nothing else, so the same acronym at two versions would leave every hit from it unable to
      // say which of the two it came from. Refused rather than collapsed, because collapsing would
      // quietly answer a different question than the one asked.
      String key = selector.systemOrDefault() + "/" + selector.sourceAcronym();
      if (!seen.add(key)) {
        throw new BadSearchRequestException(
            "Source " + key + " is named more than once. A search can ask a source for one version.");
      }
      out.add(resolveSource(selector, withVersions));
    }
    return out;
  }

  private Resolved resolveSource(SourceSelector selector) throws SQLException {
    return resolveSource(selector, false);
  }

  private Resolved resolveSource(SourceSelector selector, boolean withVersions) throws SQLException {
    String system = selector.systemOrDefault();
    String acronym = selector.sourceAcronym();
    String pinned = selector.version() == null ? null : selector.version().id();

    if (acronym == null || acronym.isBlank()) {
      throw new BadSearchRequestException("A source needs a sourceAcronym.");
    }

    CatalogStore catalog = provider.catalog();
    String name = catalog.ontologyName(acronym);
    String iri = catalog.ontologyIri(acronym).orElse(null);

    if (!provider.serves(acronym)) {
      // Whether the catalog has never heard of it or simply does not serve it are different facts to
      // an operator: one is a typo, the other an allowlist.
      String reason = name == null && iri == null
          ? SourceBlock.REASON_SOURCE_UNKNOWN
          : SourceBlock.REASON_SOURCE_NOT_SERVED;
      return unavailable(system, acronym, name, iri, reason, selector.version());
    }

    Optional<CatalogStore.SnapshotInfo> info = provider.snapshotInfo(acronym, pinned);
    if (info.isEmpty()) {
      // Served, but not at what was asked for. With a pin that is the pin's fault; without one the
      // ontology has no snapshot at all, which is an allowlist that outran the catalog.
      String reason = pinned == null
          ? SourceBlock.REASON_SOURCE_NOT_SERVED
          : SourceBlock.REASON_VERSION_NOT_HELD;
      return unavailable(system, acronym, name, iri, reason, selector.version());
    }

    Optional<SnapshotStore> store = provider.forOntology(acronym, pinned);
    if (store.isEmpty()) {
      return unavailable(system, acronym, name, iri, SourceBlock.REASON_SOURCE_NOT_SERVED, selector.version());
    }

    CatalogStore.SnapshotInfo snapshot = info.get();
    // Only asked of a pinned version: without a pin the answer is the current extraction by
    // construction, and asking would be a query a search runs once an ontology.
    Boolean superseded = pinned == null || !provider.catalog().isSuperseded(snapshot.versionId(), acronym)
        ? null : Boolean.TRUE;
    VersionInfo version = new VersionInfo(snapshot.versionId(), snapshot.releasedAt(),
        snapshot.declaredVersion(), superseded);
    List<VersionInfo> history = versionsOf(acronym);
    SourceBlock block = new SourceBlock(system, acronym, name, iri,
        SourceBlock.SERVED_LOCAL, authorityOf(acronym, snapshot.versionId()), true, version, history.size(),
        withVersions ? history : null, null, null);
    return new Resolved(block, acronym, store.get());
  }

  private static Resolved unavailable(String system, String acronym, String name, String iri, String reason,
                                      SearchRequest.VersionSelector requested) {
    // No authority on a source that was not served: it names where a release came from, and there
    // is no release here.
    SourceBlock block = new SourceBlock(system, acronym, name, iri,
        SourceBlock.SERVED_UNAVAILABLE, null, false, null, null, null, reason, requested);
    return new Resolved(block, acronym, null);
  }

  /* ----------------------------------------------------------------------------------------------
   * Types
   * ------------------------------------------------------------------------------------------- */

  /**
   * Ontologies whose acronym or name matches, over the catalog rather than over the class results.
   * The tab is therefore empty for a query like "melanoma", where no vocabulary is named that —
   * which is a true answer, and one a client has to render as such.
   */
  private List<OntologyHit> ontologyHits(String query, List<Resolved> resolved, boolean byMatches)
      throws SQLException {
    if (query.isEmpty()) {
      return List.of();
    }
    String needle = query.toLowerCase(Locale.ROOT);
    List<OntologyHit> hits = new ArrayList<>();
    // A name that begins with the query is a strong match; one that merely contains it is not.
    java.util.Set<String> startsWithName = new java.util.LinkedHashSet<>();
    for (CatalogStore.OntologyInfo ontology : provider.catalog().listOntologies()) {
      if (!provider.serves(ontology.acronym())) {
        continue;
      }
      if (!resolved.isEmpty() && resolved.stream().noneMatch(r -> r.acronym().equals(ontology.acronym()))) {
        continue;
      }
      String acronym = ontology.acronym() == null ? "" : ontology.acronym();
      String name = ontology.name() == null ? "" : ontology.name();
      boolean byAcronym = acronym.toLowerCase(Locale.ROOT).contains(needle);
      boolean byName = name.toLowerCase(Locale.ROOT).contains(needle);
      if (!byAcronym && !byName) {
        continue;
      }
      hits.add(new OntologyHit(SearchRequest.TYPE_ONTOLOGY, SearchRequest.BIOPORTAL, acronym, null,
          byAcronym ? SearchResponse.MATCH_SOURCE_ACRONYM : SearchResponse.MATCH_SOURCE_NAME, null));
      if (name.toLowerCase(Locale.ROOT).startsWith(needle)) {
        startsWithName.add(acronym);
      }
    }
    // The two halves are ranked together rather than one after the other. Segregating them looks
    // tidy and fails on the queries this tab exists for: "disease" appears in 39 vocabulary names,
    // so a names-first list spends its whole first page alphabetically — ABD, AD-CDO, AD-DROP —
    // and never reaches the vocabularies that actually hold disease terms.
    //
    // So a name match earns its place only when it is strong: an exact acronym, or a name that
    // starts with the query. A name that merely contains the query ranks by how many of its terms
    // matched, like every other vocabulary.
    Map<String, Integer> matches = index == null || !resolved.isEmpty()
        ? Map.of()
        : index.vocabularyFacet(query, VOCABULARY_FACET_LIMIT).stream()
            .collect(java.util.stream.Collectors.toMap(
                SearchIndexStore.VocabularyMatch::acronym, SearchIndexStore.VocabularyMatch::matchCount,
                (a, b) -> a, LinkedHashMap::new));

    List<OntologyHit> withCounts = new ArrayList<>();
    java.util.Set<String> named = new java.util.LinkedHashSet<>();
    for (OntologyHit hit : hits) {
      named.add(hit.sourceAcronym());
      withCounts.add(new OntologyHit(hit.type(), hit.sourceSystem(), hit.sourceAcronym(),
          hit.termCount(), hit.matchType(), matches.get(hit.sourceAcronym())));
    }
    for (Map.Entry<String, Integer> found : matches.entrySet()) {
      if (!named.contains(found.getKey())) {
        withCounts.add(new OntologyHit(SearchRequest.TYPE_ONTOLOGY, SearchRequest.BIOPORTAL,
            found.getKey(), null, SearchResponse.MATCH_TERMS, found.getValue()));
      }
    }
    Comparator<OntologyHit> byMatchCount = Comparator
        .comparingInt((OntologyHit h) -> h.matchCount() == null ? 0 : h.matchCount()).reversed()
        .thenComparing(OntologyHit::sourceAcronym);
    // Ranking by name first is right for browsing and wrong for narrowing: a vocabulary aptly named
    // after the query may hold almost none of its terms, and narrowing to it hides the answer.
    withCounts.sort(byMatches
        ? byMatchCount
        : Comparator.comparingInt((OntologyHit h) -> strength(h, needle, startsWithName))
            .thenComparing(byMatchCount));
    return withCounts;
  }

  /**
   * How strongly an ontology answers the query: 0 for a name that is the query or begins with it,
   * 1 for everything else. Two tiers rather than a gradient, because past "this vocabulary is named
   * after what you typed" the useful ordering is how much of it matched.
   */
  private static int strength(OntologyHit hit, String needle, java.util.Set<String> strongNames) {
    if (!SearchResponse.MATCH_TERMS.equals(hit.matchType())) {
      String acronym = hit.sourceAcronym().toLowerCase(Locale.ROOT);
      if (acronym.equals(needle) || acronym.startsWith(needle) || strongNames.contains(hit.sourceAcronym())) {
        return 0;
      }
    }
    return 1;
  }

  private static int nameRank(String acronym, String needle) {
    String lower = acronym.toLowerCase(Locale.ROOT);
    if (lower.equals(needle)) {
      return 0;
    }
    return lower.startsWith(needle) ? 1 : 2;
  }

  private List<ClassHit> classHits(String query, List<Resolved> sources, String lang) throws SQLException {
    List<ClassHit> hits = new ArrayList<>();
    for (Resolved source : sources) {
      SnapshotStore store = source.store();
      for (SnapshotStore.Concept concept : store.searchByLabel(query, false, COUNT_CAP)) {
        String label = displayLabel(store, concept, lang);
        List<MatchedLabel> matched = matchedLabels(store, concept, query, label);
        hits.add(new ClassHit(SearchRequest.TYPE_CLASS, source.block().sourceSystem(), source.acronym(),
            concept.iri(), SearchRequest.TYPE_CLASS, label,
            matched.isEmpty() ? SearchResponse.MATCH_TERM_LABEL : SearchResponse.MATCH_SYNONYM,
            matched.isEmpty() ? null : matched,
            concept.obsolete(), replacedBy(store, concept),
            concept.hasChildren(), store.descendantCount(concept.iri()), path(store, concept.iri()),
            snapshotNames(store.labels(concept.iri()), label),
            SnapshotStore.servedDefinition(store.definitions(concept.iri()))));
      }
    }
    hits.sort(hitOrder(query, ClassHit::termLabel, ClassHit::obsolete, ClassHit::termIri));
    return hits;
  }

  /**
   * The class results that have descendants, expressed as branch entries: what an author would be
   * constraining to, rather than one term.
   */
  private List<BranchHit> branchHits(String query, List<Resolved> sources, String lang) throws SQLException {
    List<BranchHit> hits = new ArrayList<>();
    for (Resolved source : sources) {
      SnapshotStore store = source.store();
      for (SnapshotStore.Concept concept : store.searchByLabel(query, false, COUNT_CAP)) {
        if (!concept.hasChildren()) {
          continue;
        }
        String label = displayLabel(store, concept, lang);
        List<MatchedLabel> matched = matchedLabels(store, concept, query, label);
        hits.add(new BranchHit(SearchRequest.TYPE_BRANCH, source.block().sourceSystem(), source.acronym(),
            concept.iri(), label, store.descendantCount(concept.iri()),
            matched.isEmpty() ? SearchResponse.MATCH_TERM_LABEL : SearchResponse.MATCH_SYNONYM,
            matched.isEmpty() ? null : matched,
            concept.obsolete(), path(store, concept.iri()), examples(store, concept.iri()),
            snapshotNames(store.labels(concept.iri()), label)));
      }
    }
    hits.sort(hitOrder(query, BranchHit::termBaseLabel, BranchHit::obsolete, BranchHit::termBaseIri));
    return hits;
  }

  /**
   * Value sets, from the collections among the named sources.
   *
   * A value-set collection is ingested exactly as an ontology and differs only in the catalog's kind
   * discriminator, so inside a snapshot a value set is a concept with values beneath it and a value
   * is a leaf. A match on a value therefore surfaces the value set that holds it, which is the thing
   * a field can actually be constrained to.
   */
  private List<ValueSetHit> valueSetHits(String query, List<Resolved> sources) throws SQLException {
    Map<String, ValueSetHit> byIri = new LinkedHashMap<>();
    for (Resolved source : sources) {
      if (!provider.catalog().isValueSetCollection(source.acronym())) {
        continue;
      }
      SnapshotStore store = source.store();
      for (SnapshotStore.Concept concept : store.searchByLabel(query, false, COUNT_CAP)) {
        if (concept.hasChildren()) {
          byIri.putIfAbsent(concept.iri(), new ValueSetHit(SearchRequest.TYPE_VALUE_SET,
              source.block().sourceSystem(), source.acronym(), concept.iri(), concept.prefLabel(),
              store.children(concept.iri()).size(), SearchResponse.MATCH_TERM_BASE_LABEL, null));
          continue;
        }
        // A matched value, credited to each value set that holds it.
        for (String parent : store.parents(concept.iri())) {
          ValueSetHit existing = byIri.get(parent);
          List<TermRef> matched = new ArrayList<>(
              existing == null || existing.matchedTerms() == null ? List.of() : existing.matchedTerms());
          matched.add(new TermRef(concept.iri(), concept.prefLabel()));
          if (existing != null && SearchResponse.MATCH_TERM_BASE_LABEL.equals(existing.matchType())) {
            byIri.put(parent, new ValueSetHit(existing.type(), existing.sourceSystem(), existing.sourceAcronym(),
                existing.termBaseIri(), existing.termBaseLabel(), existing.termCount(),
                existing.matchType(), matched));
            continue;
          }
          byIri.put(parent, new ValueSetHit(SearchRequest.TYPE_VALUE_SET, source.block().sourceSystem(),
              source.acronym(), parent, store.prefLabel(parent).orElse(null),
              store.children(parent).size(), SearchResponse.MATCH_MEMBER, matched));
        }
      }
    }
    List<ValueSetHit> hits = new ArrayList<>(byIri.values());
    hits.sort(hitOrder(query, ValueSetHit::termBaseLabel, h -> false, ValueSetHit::termBaseIri));
    return hits;
  }

  /**
   * Class or branch results from the cross-snapshot index, across every ontology it holds.
   *
   * Two things a source-scoped search gives that this cannot, both because they need the snapshot
   * rather than the index: a branch row carries no path or examples, and {@code lang} does not
   * choose the label. Absent fields rather than wrong ones — a client can tell the difference, and
   * an author who has narrowed to a source gets both back.
   */
  private List<? extends Hit> corpusHits(String query, boolean branchesOnly, int page, int pageSize,
                                         List<String> scope) throws SQLException {
    // Fetch the page, not a thousand rows to slice one page out of. Ranking a broad match is where
    // the time goes — "ce" cost 2.2 seconds fetching a thousand and 0.2 fetching twenty, measured
    // 2026-08-13 — and the counts come from the facets rather than from the length of this list.
    // Terms are paged by distinct label, with every hit of the labels on the page, so a client that
    // folds identical labels can say how many vocabularies offer one rather than how many happened to
    // land on this page. Branches are paged by hit: their rows are already distinguished by parent.
    // Both kinds page by distinct label, and for the same reason. A term's label repeats across
    // ontologies; a branch's repeats within one, because a vocabulary can materialise a concept once
    // per position in its hierarchy — RH-MESH does it 11,528 times. Either way a client folding the
    // repetition needs the whole group, not the part that fitted on a page of hits.
    List<Hit> hits = new ArrayList<>();
    List<SearchIndexStore.IndexHit> found =
        index.searchByLabelPage(query, scope, branchesOnly, page, pageSize);
    Map<String, List<SearchIndexStore.IndexedName>> namesByTerm =
        index.namesOf(found.stream().map(hit -> hit.term().iri()).toList());
    for (SearchIndexStore.IndexHit hit : found) {
      SearchIndexStore.IndexedTerm term = hit.term();
      // What matched, and only when the label on screen does not already say it — the same rule the
      // snapshot path applies, for the same reason. A search reaches a concept through every name it
      // captured, so a term whose preferred label answers the query answers it through the synonyms
      // too: reporting one of those beside a row that reads "melanoma" explains a row that needs no
      // explaining, and picks an arbitrary synonym to do it with. The inner test is not covered by
      // the outer one, because matching folds diacritics: `aquifere` reaches a term labelled
      // `aquifère`, whose label does not contain the query but whose matched names include itself.
      List<MatchedLabel> matched = new ArrayList<>();
      if (!containsIgnoreCase(term.prefLabel(), query)) {
        for (SearchIndexStore.IndexedName name : hit.matched()) {
          if (name.value() != null && !name.value().equalsIgnoreCase(term.prefLabel())) {
            matched.add(new MatchedLabel(name.value(), blankToNull(name.lang())));
          }
        }
      }
      String matchType = matched.isEmpty() ? SearchResponse.MATCH_TERM_LABEL : SearchResponse.MATCH_SYNONYM;
      // A one-step path, which is what the index holds and what a label often needs: fifteen classes
      // labelled "Disease" in one vocabulary are told apart by their parent and by nothing else on
      // the row, and a vocabulary can label two of its own classes the same. Classes carry it for
      // the same reason branches do.
      //
      // A search naming one source gets the whole chain instead. It costs a recursive query a hit
      // and is affordable at a page of them, and it is what lets a client draw the page as the
      // ontology's own tree: the union of the chains is rooted by construction, so no separate call
      // for the roots and no walk down from them to find where the matches are.
      List<TermRef> parent = scope.size() == 1
          ? chainOf(term)
          : (term.parentLabel() == null
              ? null
              : List.of(new TermRef(term.parentIri(), term.parentLabel())));
      List<MatchedLabel> names = otherNames(
          namesByTerm.get(SearchIndexStore.nameKey(term.acronym(), term.iri())), term.prefLabel());
      if (branchesOnly) {
        hits.add(new BranchHit(SearchRequest.TYPE_BRANCH, SearchRequest.BIOPORTAL, term.acronym(),
            term.iri(), term.prefLabel(), term.descendantCount(), matchType,
            matched.isEmpty() ? null : matched, term.obsolete(), parent, null,
            names.isEmpty() ? null : names));
      } else {
        hits.add(new ClassHit(SearchRequest.TYPE_CLASS, SearchRequest.BIOPORTAL, term.acronym(),
            term.iri(), SearchRequest.TYPE_CLASS, term.prefLabel(), matchType,
            matched.isEmpty() ? null : matched, term.obsolete(),
            term.replacedBy() == null ? null : new TermRef(term.replacedBy(), null),
            term.hasChildren(), term.descendantCount(), parent, names.isEmpty() ? null : names,
            term.definition()));
      }
    }
    return hits;
  }

  /**
   * What an ontology can be pinned to, newest first.
   *
   * Ordered for stepping rather than for storage: an author steps back from what is current, so the
   * list reads the way the walk does. Every entry carries the content hash that pins it and the
   * dates that name it, because a hash is what makes a pin reproducible and never what an author
   * recognises.
   */
  private List<VersionInfo> versionsOf(String acronym) throws SQLException {
    // Releases, not snapshots: re-extracting a release mints a second version id for bytes that did
    // not change, and offering both says the ontology was released twice. The superseded ones stay
    // in the catalog and stay resolvable — a pin written before one was superseded has to keep
    // meaning what it meant — they are only not offered.
    List<VersionInfo> versions = new ArrayList<>();
    for (CatalogStore.SnapshotInfo snapshot : provider.catalog().listCurrentSnapshots(acronym)) {
      versions.add(new VersionInfo(snapshot.versionId(), snapshot.releasedAt(), snapshot.declaredVersion()));
    }
    java.util.Collections.reverse(versions);
    return versions;
  }

  /** Every value-set collection the catalog knows, resolved as if the request had named them. */
  private List<Resolved> valueSetCollections() throws SQLException {
    List<Resolved> out = new ArrayList<>();
    for (String acronym : provider.catalog().listValueSetCollections()) {
      Resolved resolved = resolveSource(new SourceSelector(null, acronym, null));
      if (resolved.isLocal()) {
        out.add(resolved);
      }
    }
    return out;
  }

  /**
   * A page of corpus-wide results, counted by facet rather than by the size of the page's own list.
   *
   * The hits are capped before they are counted, so counting them would report the cap. The facet
   * counts the match itself, which is both true and cheap. Two counts, because they answer different
   * questions: how many terms matched, and how many rows a client that collapses identical labels
   * will render — for "melanoma" that is 5,439 and 2,552.
   */
  private TypeResults facetedPage(String query, boolean branchesOnly, List<? extends Hit> hits,
                                  int page, int pageSize, List<String> scope) throws SQLException {
    int total = index.matchCount(query, scope, false, branchesOnly, FACET_CAP);
    // Only the terms results carry a collapsed count. It is what that tab's badge shows, and each
    // facet is a second pass over the match: computing one nobody reads doubles the cost of a broad
    // query for nothing.
    Integer labels = index.matchCount(query, scope, true, branchesOnly, FACET_CAP);
    // The terms list is already the page — it holds every hit of the page's labels, which is more
    // rows than pageSize on purpose. Slicing it again would cut a label in half and leave a client
    // folding a partial group.
    // Already the page: it holds every hit of the page's labels, which is more rows than pageSize on
    // purpose. Slicing again would cut a label in half and leave a client folding a partial group.
    return new TypeResults(total, total >= FACET_CAP,
        labels, labels == null ? null : labels >= FACET_CAP,
        page, pageSize, List.copyOf(hits));
  }

  /** The source block for an ontology the index answered for, at the version the index holds. */
  /**
   * Where a term sits in its ontology.
   *
   * Answered from the index rather than the snapshot: the index holds one parent a term, which is
   * the step a row needs, and walking it in SQLite costs one query where opening a snapshot per
   * lookup would cost a file open and a hierarchy the caller did not ask for. Returns empty when
   * the index does not hold the term, which is the honest answer for a proxied or unheld source.
   */
  public Optional<HierarchyResponse> hierarchy(String acronym, String termIri) throws SQLException {
    return hierarchy(acronym, termIri, null, 0);
  }

  /** The same, at a named release, from the first child. */
  public Optional<HierarchyResponse> hierarchy(String acronym, String termIri, String versionId)
      throws SQLException {
    return hierarchy(acronym, termIri, versionId, 0);
  }

  /**
   * The same, at a named release.
   *
   * A hierarchy is a property of a release, not of an ontology: a term's parent can move between
   * two of them, and answering from the index — which holds each ontology's current version and no
   * other — would draw an author who pinned an older release the shape of today's. So a request
   * naming a version is answered from that snapshot, and only an unpinned one takes the index,
   * where the answer and the index agree by construction.
   */
  public Optional<HierarchyResponse> hierarchy(String acronym, String termIri, String versionId,
                                               int offset) throws SQLException {
    if (acronym == null || acronym.isBlank() || termIri == null || termIri.isBlank()) {
      return Optional.empty();
    }
    int from = Math.max(0, offset);
    if (versionId != null && !versionId.isBlank()) {
      return snapshotHierarchy(acronym, termIri, versionId, from);
    }
    if (index == null) {
      return Optional.empty();
    }
    Optional<SearchIndexStore.IndexedTerm> found = index.term(acronym, termIri);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    SearchIndexStore.IndexedTerm term = found.get();
    List<TermRef> path = new ArrayList<>();
    for (SearchIndexStore.IndexedTerm step : index.ancestors(acronym, termIri, MAX_ANCESTOR_DEPTH)) {
      path.add(new TermRef(step.iri(), step.prefLabel()));
    }
    List<HierarchyResponse.Child> children = new ArrayList<>();
    for (SearchIndexStore.IndexedTerm child : index.children(acronym, termIri, from, CHILD_LIMIT)) {
      children.add(new HierarchyResponse.Child(child.iri(), child.prefLabel(), child.hasChildren(),
          child.descendantCount(), child.definition()));
    }
    return Optional.of(new HierarchyResponse(SearchRequest.BIOPORTAL, acronym,
        indexedSourceBlock(acronym), path.isEmpty() ? null : path, term.iri(), term.prefLabel(),
        children.isEmpty() ? null : children, index.childCount(acronym, termIri), from,
        term.descendantCount()));
  }

  /** The hierarchy as one snapshot records it, for a request that named a release. */
  private Optional<HierarchyResponse> snapshotHierarchy(String acronym, String termIri, String versionId,
                                                        int offset) throws SQLException {
    Resolved resolved = resolveSource(
        new SourceSelector(null, acronym, new SearchRequest.VersionSelector(versionId)));
    if (!resolved.isLocal()) {
      return Optional.empty();
    }
    SnapshotStore store = resolved.store();
    Optional<String> label = store.prefLabel(termIri);
    if (label.isEmpty() && !store.contains(termIri)) {
      return Optional.empty();
    }
    // By label and limited in one query. Taking the first CHILD_LIMIT by IRI and sorting those for
    // display gave an arbitrary subset of a large node's children, presented as though it were the
    // alphabetical head of them: ABD's "Disease" has 280 children, and the 50 that came back skipped
    // "African horse sickness" while showing "African swine fever" two rows on. It also matches how
    // the index answers this for the current release, so pinning changes the release read, nothing else.
    List<HierarchyResponse.Child> children = new ArrayList<>();
    for (SnapshotStore.LabelledConcept child : store.childrenByLabel(termIri, offset, CHILD_LIMIT)) {
      children.add(new HierarchyResponse.Child(child.iri(), child.prefLabel(),
          !store.children(child.iri()).isEmpty(), store.descendantCount(child.iri()),
          SnapshotStore.servedDefinition(store.definitions(child.iri()))));
    }
    List<TermRef> path = path(store, termIri);
    return Optional.of(new HierarchyResponse(SearchRequest.BIOPORTAL, acronym, resolved.block(),
        path, termIri, label.orElse(null), children.isEmpty() ? null : children,
        store.childCount(termIri), offset, store.descendantCount(termIri)));
  }

  /** The whole chain above an indexed term, root first, or null where it is a root itself. */
  private List<TermRef> chainOf(SearchIndexStore.IndexedTerm term) throws SQLException {
    List<TermRef> chain = new ArrayList<>();
    for (SearchIndexStore.IndexedTerm step : index.ancestors(term.acronym(), term.iri(), MAX_ANCESTOR_DEPTH)) {
      chain.add(new TermRef(step.iri(), step.prefLabel()));
    }
    return chain.isEmpty() ? null : chain;
  }

  private SourceBlock indexedSourceBlock(String acronym) throws SQLException {
    CatalogStore catalog = provider.catalog();
    String name = catalog.ontologyName(acronym);
    String iri = catalog.ontologyIri(acronym).orElse(null);
    String versionId = index.indexedVersion(acronym).orElse(null);
    VersionInfo version = null;
    if (versionId != null) {
      Optional<CatalogStore.SnapshotInfo> snapshot = catalog.resolveVersion(acronym, versionId);
      version = snapshot
          .map(s -> new VersionInfo(s.versionId(), s.releasedAt(), s.declaredVersion()))
          .orElseGet(() -> new VersionInfo(versionId, null, null));
    }
    return new SourceBlock(SearchRequest.BIOPORTAL, acronym, name, iri,
        SourceBlock.SERVED_LOCAL, authorityOf(acronym, versionId), versionId != null, version,
        versionsOf(acronym).size(), null, null, null);
  }

  /**
   * Which repository a release's bytes came from, or {@code null} where the catalog does not say.
   *
   * Recorded per snapshot rather than per ontology, because the same ontology can be harvested from
   * more than one — the content hash makes those the same release, and this says which of them
   * supplied the copy being served.
   */
  private String authorityOf(String acronym, String versionId) throws SQLException {
    if (versionId == null) {
      return null;
    }
    return provider.catalog().snapshotProvenance(versionId, acronym)
        .map(CatalogStore.SnapshotProvenance::backend)
        .orElse(null);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }

  private static String needsASourceMessage(List<String> types) {
    return "Searching for " + String.join(", ", types) + " needs at least one locally-served source, "
        + "because no cross-snapshot index is configured. Without one, a corpus-wide search would "
        + "have to open every snapshot in the catalog.";
  }

  /* ----------------------------------------------------------------------------------------------
   * Hit detail
   * ------------------------------------------------------------------------------------------- */

  private static String displayLabel(SnapshotStore store, SnapshotStore.Concept concept, String lang)
      throws SQLException {
    if (lang == null || lang.isBlank()) {
      return concept.prefLabel();
    }
    return store.labelInLang(concept.iri(), lang).orElse(concept.prefLabel());
  }

  /**
   * Every name a term carries other than the one on screen.
   *
   * What tells an author whether a concept is the one they meant, when its preferred label is a
   * word several vocabularies use differently. Deduplicated on the value: an ontology can record
   * one string as both an exact and a related synonym, and that distinction is not one a picker
   * can act on.
   */
  private static List<MatchedLabel> otherNames(List<SearchIndexStore.IndexedName> names, String shown) {
    if (names == null) {
      return List.of();
    }
    Map<String, MatchedLabel> byValue = new LinkedHashMap<>();
    for (SearchIndexStore.IndexedName name : names) {
      if (name.value() != null && !name.value().equalsIgnoreCase(shown)) {
        byValue.putIfAbsent(name.value(), new MatchedLabel(name.value(), blankToNull(name.lang())));
      }
    }
    return List.copyOf(byValue.values());
  }

  /** The same, over what a snapshot records. */
  private static List<MatchedLabel> snapshotNames(List<SnapshotStore.LabelEntry> labels, String shown) {
    Map<String, MatchedLabel> byValue = new LinkedHashMap<>();
    for (SnapshotStore.LabelEntry entry : labels) {
      if (entry.value() != null && !entry.value().equalsIgnoreCase(shown)) {
        byValue.putIfAbsent(entry.value(), new MatchedLabel(entry.value(), entry.lang()));
      }
    }
    return byValue.isEmpty() ? null : List.copyOf(byValue.values());
  }


  /**
   * What matched, when it was not the label on screen. A search reaches a concept through any
   * captured name, so a row labelled "melanoma" can be a French or synonym hit; without this the
   * result reads as a defect rather than as recall.
   */
  private static List<MatchedLabel> matchedLabels(SnapshotStore store, SnapshotStore.Concept concept,
                                                  String query, String shown) throws SQLException {
    if (query.isEmpty() || containsIgnoreCase(shown, query)) {
      return List.of();
    }
    List<MatchedLabel> out = new ArrayList<>();
    for (SnapshotStore.LabelEntry entry : store.matchingLabels(concept.iri(), query)) {
      out.add(new MatchedLabel(entry.value(), entry.lang()));
    }
    return out;
  }

  private static TermRef replacedBy(SnapshotStore store, SnapshotStore.Concept concept) throws SQLException {
    if (!concept.obsolete()) {
      return null;
    }
    Optional<SnapshotStore.ConceptMeta> meta = store.conceptMeta(concept.iri());
    if (meta.isEmpty() || meta.get().replacedBy() == null) {
      return null;
    }
    String iri = meta.get().replacedBy();
    return new TermRef(iri, store.prefLabel(iri).orElse(null));
  }

  /** The chain from a root down to the term, which is what separates one "disease" from another. */
  private static List<TermRef> path(SnapshotStore store, String iri) throws SQLException {
    List<TermRef> path = new ArrayList<>();
    String current = iri;
    // Bounded rather than recursive: a broader/narrower cycle, which some SKOS vocabularies carry,
    // would otherwise walk forever.
    for (int depth = 0; depth < 32; depth++) {
      List<String> parents = store.parents(current);
      if (parents.isEmpty()) {
        break;
      }
      String parent = parents.get(0);
      if (parent.equals(current) || path.stream().anyMatch(step -> step.termIri().equals(parent))) {
        break;
      }
      path.add(0, new TermRef(parent, store.prefLabel(parent).orElse(null)));
      current = parent;
    }
    return path.isEmpty() ? null : path;
  }

  /** A few of what lies beneath, so an author can see whether the subtree is the one they pictured. */
  private static List<TermRef> examples(SnapshotStore store, String iri) throws SQLException {
    List<TermRef> examples = new ArrayList<>();
    for (String child : store.children(iri)) {
      if (examples.size() == EXAMPLE_COUNT) {
        break;
      }
      examples.add(new TermRef(child, store.prefLabel(child).orElse(null)));
    }
    return examples.isEmpty() ? null : examples;
  }

  /* ----------------------------------------------------------------------------------------------
   * Ordering and paging
   * ------------------------------------------------------------------------------------------- */

  /**
   * A deterministic order, and no more than that. An exact label first, then a label that starts
   * with the query, then the shortest, with obsolete terms demoted and the IRI breaking every
   * remaining tie so the same query never returns two different orders.
   *
   * This is not the ordering work the roadmap describes. That is a ranking problem — match reason,
   * length normalisation, a total order over the whole corpus — and it lands here when it is done.
   */
  private static <H> Comparator<H> hitOrder(String query, java.util.function.Function<H, String> label,
                                            java.util.function.Predicate<H> obsolete,
                                            java.util.function.Function<H, String> iri) {
    String needle = query.toLowerCase(Locale.ROOT);
    return Comparator
        .comparing((H h) -> obsolete.test(h))
        .thenComparingInt(h -> labelRank(label.apply(h), needle))
        .thenComparingInt(h -> label.apply(h) == null ? Integer.MAX_VALUE : label.apply(h).length())
        .thenComparing(h -> label.apply(h) == null ? "" : label.apply(h))
        .thenComparing(h -> iri.apply(h) == null ? "" : iri.apply(h));
  }

  private static int labelRank(String label, String needle) {
    if (label == null || needle.isEmpty()) {
      return 3;
    }
    String lower = label.toLowerCase(Locale.ROOT);
    if (lower.equals(needle)) {
      return 0;
    }
    if (lower.startsWith(needle)) {
      return 1;
    }
    return 2;
  }

  private static TypeResults paged(List<? extends Hit> all, int page, int pageSize) {
    int total = all.size();
    boolean capped = total >= COUNT_CAP;
    int from = Math.min((page - 1) * pageSize, total);
    int to = Math.min(from + pageSize, total);
    return new TypeResults(total, capped, page, pageSize, List.copyOf(all.subList(from, to)));
  }

  /* ----------------------------------------------------------------------------------------------
   * Validation
   * ------------------------------------------------------------------------------------------- */

  private static List<String> validatedTypes(List<String> types) {
    for (String type : types) {
      if (!SearchRequest.ALL_TYPES.contains(type)) {
        throw new BadSearchRequestException(
            "Unknown type '" + type + "'. Accepted: " + String.join(", ", SearchRequest.ALL_TYPES) + ".");
      }
    }
    return types;
  }

  /** Every type but {@code ontology}, which reads the catalog rather than a snapshot. */
  private static boolean needsASource(List<String> types) {
    return types.stream().anyMatch(t -> !SearchRequest.TYPE_ONTOLOGY.equals(t));
  }

  private static int clampPageSize(Integer requested) {
    if (requested == null || requested <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }

  private static boolean containsIgnoreCase(String haystack, String needle) {
    return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
  }
}
