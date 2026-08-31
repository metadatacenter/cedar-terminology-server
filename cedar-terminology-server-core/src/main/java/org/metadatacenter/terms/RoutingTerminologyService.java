package org.metadatacenter.terms;

import org.metadatacenter.cedar.terminology.validation.integratedsearch.BranchValueConstraint;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ClassValueConstraint;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.OntologyValueConstraint;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueSetValueConstraint;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;
import org.metadatacenter.terms.util.Util;
import org.metadatacenter.terms.util.ValueSetIds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * An {@link ITerminologyService} that dispatches each call to either a local, version-aware
 * backend (e.g. a SQLite-backed store) or a remote backend (BioPortal), one ontology at a time.
 *
 * This is the seam for the incremental migration away from the BioPortal proxy: an ontology is
 * served locally once it has been ingested and {@link LocalAvailability#isLocal(String)} reports
 * it as available. Local backends may be partial — a routed call that the local backend answers
 * with {@link UnsupportedOperationException} falls through to the remote backend, so local coverage
 * can grow one operation at a time without breaking any request.
 *
 * Only the ontology-scoped hierarchy and lookup operations ("Bucket A") are eligible for local
 * routing. Search operations ("Bucket B") and provisional write operations ("Bucket C") are always
 * sent to the remote backend for now; both are migration decisions deferred to later work.
 */
public class RoutingTerminologyService implements ITerminologyService {

  /**
   * Decides whether a given ontology is currently served by the local backend.
   */
  @FunctionalInterface
  public interface LocalAvailability {
    boolean isLocal(String ontology);
  }

  @FunctionalInterface
  private interface Call<T> {
    T apply(ITerminologyService service) throws IOException;
  }

  private final ITerminologyService remote;
  private final ITerminologyService local;
  private final LocalAvailability availability;
  private final LocalAvailability browseAvailability;
  private final boolean localOnly;

  /**
   * Remote-only: every call is delegated to {@code remote}. Behavior is identical to using the
   * remote backend directly. Use this until a local backend exists.
   */
  public RoutingTerminologyService(ITerminologyService remote) {
    this(remote, null, ontology -> false, ontology -> false, false);
  }

  public RoutingTerminologyService(ITerminologyService remote, ITerminologyService local,
                                   LocalAvailability availability) {
    this(remote, local, availability, availability, false);
  }

  public RoutingTerminologyService(ITerminologyService remote, ITerminologyService local,
                                   LocalAvailability availability, boolean localOnly) {
    this(remote, local, availability, availability, localOnly);
  }

  /**
   * Per-endpoint routing. {@code availability} governs the search and point-lookup operations
   * (integrated-search, class/children/descendants) — the paths the equivalence gate proves against
   * BioPortal. {@code browseAvailability} governs the tree-browse entry points (root classes and the
   * class tree): a locally-served ontology is browsed locally only when its roots are also proven
   * equivalent, otherwise those calls go to BioPortal. This lets an ontology whose integrated-search
   * is equivalent but whose local roots still diverge (orphan/import overcount) be cut over for the
   * high-value search path without regressing the picker's tree. Pass the same availability for both
   * to serve everything locally.
   *
   * When {@code localOnly} is true, a call for a locally-served ontology is never allowed to fall
   * back to the remote backend: a local {@link UnsupportedOperationException} propagates instead of
   * being masked by a remote result. This is the strict mode the equivalence harness runs under.
   */
  public RoutingTerminologyService(ITerminologyService remote, ITerminologyService local,
                                   LocalAvailability availability, LocalAvailability browseAvailability,
                                   boolean localOnly) {
    this.remote = remote;
    this.local = local;
    this.availability = availability;
    this.browseAvailability = browseAvailability;
    this.localOnly = localOnly;
  }

  /**
   * Serves an ontology-scoped call from the local backend when it is present and reports the
   * ontology as available, falling back to the remote backend if the local backend is absent or
   * does not implement the operation. In {@code localOnly} mode a locally-served ontology does not
   * fall back — an unimplemented operation propagates.
   */
  private <T> T dispatch(String ontology, Call<T> call) throws IOException {
    return dispatchWith(availability, ontology, call);
  }

  /** Like {@link #dispatch}, but gated on {@link #browseAvailability} — for the tree-browse entry
   *  points (root classes, class tree) that require roots equivalence, not just search equivalence. */
  private <T> T dispatchBrowse(String ontology, Call<T> call) throws IOException {
    return dispatchWith(browseAvailability, ontology, call);
  }

  private <T> T dispatchWith(LocalAvailability avail, String ontology, Call<T> call) throws IOException {
    if (local != null && ontology != null && avail.isLocal(ontology)) {
      if (localOnly) {
        return call.apply(local);
      }
      try {
        return call.apply(local);
      } catch (UnsupportedOperationException notImplementedLocally) {
        // Fall through to remote: local coverage is partial by design.
      }
    }
    return call.apply(remote);
  }

  /* ---------------------------------------------------------------------------------------------
   * Bucket B — search. Routed to local when the search targets a single locally-served ontology
   * (a class search scoped to one ontology, or a branch search); anything the local backend cannot
   * answer (multi-source, non-class scopes, value sets) throws and falls through to remote.
   * ------------------------------------------------------------------------------------------- */

  @Override
  public PagedResults<SearchResult> search(String q, List<String> scope, List<String> sources, boolean suggest,
                                           String source, String subtreeRootId, int maxDepth, int page, int pageSize,
                                           boolean displayContext, boolean displayLinks, String apiKey,
                                           ValueSetIds valueSetIds) throws IOException {
    String localOntology = singleLocalSearchOntology(sources, source, subtreeRootId);
    if (local != null && localOntology != null) {
      if (localOnly) {
        return local.search(q, scope, sources, suggest, source, subtreeRootId, maxDepth, page, pageSize,
            displayContext, displayLinks, apiKey, valueSetIds);
      }
      try {
        return local.search(q, scope, sources, suggest, source, subtreeRootId, maxDepth, page, pageSize,
            displayContext, displayLinks, apiKey, valueSetIds);
      } catch (UnsupportedOperationException notImplementedLocally) {
        // Fall through to remote.
      }
    }
    return remote.search(q, scope, sources, suggest, source, subtreeRootId, maxDepth, page, pageSize, displayContext,
        displayLinks, apiKey, valueSetIds);
  }

  /**
   * The single locally-served ontology a search targets, or {@code null} if it is not a candidate
   * for local routing. A branch search names its ontology in {@code source}; an ontology-scoped
   * search names exactly one acronym in {@code sources}.
   */
  private String singleLocalSearchOntology(List<String> sources, String source, String subtreeRootId) {
    if (subtreeRootId != null && !subtreeRootId.isEmpty()) {
      return source != null && availability.isLocal(source) ? source : null;
    }
    if (sources != null && sources.size() == 1 && availability.isLocal(sources.get(0))) {
      return sources.get(0);
    }
    return null;
  }

  @Override
  public PagedResults<SearchResult> propertySearch(String q, List<String> sources, boolean exactMatch,
                                                   boolean requireDefinitions, int page, int pageSize,
                                                   boolean displayContext, boolean displayLinks, String apiKey)
      throws IOException {
    return remote.propertySearch(q, sources, exactMatch, requireDefinitions, page, pageSize, displayContext,
        displayLinks, apiKey);
  }

  @Override
  public PagedResults<SearchResult> integratedSearch(Optional<String> q, ValueConstraints valueConstraints, int page,
                                                     int pageSize, String apiKey, String lang) throws IOException {
    if (local != null && integratedSearchServedLocally(valueConstraints)) {
      if (localOnly) {
        return local.integratedSearch(q, valueConstraints, page, pageSize, apiKey, lang);
      }
      try {
        return local.integratedSearch(q, valueConstraints, page, pageSize, apiKey, lang);
      } catch (UnsupportedOperationException notImplementedLocally) {
        // The local backend cannot serve this search *shape* (e.g. multi-source or mixed constraints).
        // Routing to the remote adapter is fine for an unpinned request, but never for a pinned one:
        // BioPortal serves latest, which would silently break the freeze. (A pinned *version* miss is a
        // PinnedVersionUnavailableException — not an UnsupportedOperationException — so it is not caught
        // here; it propagates and fails loud.)
        if (hasExplicitVersionPin(valueConstraints)) {
          throw new PinnedVersionUnavailableException(
              "A constraint pins a version, but the local backend cannot serve this search; refusing to "
                  + "serve latest from the remote adapter.");
        }
        // Fall through to remote.
      }
    } else if (hasExplicitVersionPin(valueConstraints)) {
      // The pinned source is not served locally at all; the remote adapter would serve latest, breaking
      // the freeze. Fail loud instead of silently resolving against current content.
      throw new PinnedVersionUnavailableException(
          "A constraint pins a version, but its source is not served locally; refusing to serve latest "
              + "from the remote adapter.");
    }
    // A constraint that names a non-BioPortal source is served from the local store or not at all — it is
    // never proxied to BioPortal. Identity is source-independent, so a locally-served snapshot already
    // answered above; reaching here means it is not served locally. Return no results rather than
    // BioPortal's content for a different source.
    if (hasNonBioPortalSource(valueConstraints)) {
      return emptyResults(page, pageSize);
    }
    return remote.integratedSearch(q, valueConstraints, page, pageSize, apiKey, lang);
  }

  /** Whether any ontology / branch / value-set constraint names a source system other than BioPortal (a
   *  non-null, non-blank {@code sourceSystem} that is not {@code "bioportal"}). Such a constraint must be
   *  served locally or reported unavailable — never proxied to BioPortal. */
  private static boolean hasNonBioPortalSource(ValueConstraints vc) {
    if (vc == null) {
      return false;
    }
    if (vc.getOntologies() != null) {
      for (OntologyValueConstraint o : vc.getOntologies()) {
        if (isNonBioPortalSource(o.getSourceSystem())) {
          return true;
        }
      }
    }
    if (vc.getBranches() != null) {
      for (BranchValueConstraint b : vc.getBranches()) {
        if (isNonBioPortalSource(b.getSourceSystem())) {
          return true;
        }
      }
    }
    if (vc.getValueSets() != null) {
      for (ValueSetValueConstraint v : vc.getValueSets()) {
        if (isNonBioPortalSource(v.getSourceSystem())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isNonBioPortalSource(String sourceSystem) {
    return sourceSystem != null && !sourceSystem.isBlank() && !"bioportal".equalsIgnoreCase(sourceSystem);
  }

  private static PagedResults<SearchResult> emptyResults(int page, int pageSize) {
    return new PagedResults<>(page, 0, pageSize, 0, null, null, java.util.List.of());
  }

  /** Whether any ontology / branch / value-set constraint carries an explicit version pin (a value that
   *  is not null, blank, or the "latest" sentinel). Enumerated classes cannot be pinned. */
  private static boolean hasExplicitVersionPin(ValueConstraints vc) {
    if (vc == null) {
      return false;
    }
    if (vc.getOntologies() != null) {
      for (OntologyValueConstraint o : vc.getOntologies()) {
        if (isVersionPin(o.getVersion())) {
          return true;
        }
      }
    }
    if (vc.getBranches() != null) {
      for (BranchValueConstraint b : vc.getBranches()) {
        if (isVersionPin(b.getVersion())) {
          return true;
        }
      }
    }
    if (vc.getValueSets() != null) {
      for (ValueSetValueConstraint v : vc.getValueSets()) {
        if (isVersionPin(v.getVersion())) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isVersionPin(String version) {
    return version != null && !version.isBlank() && !"latest".equalsIgnoreCase(version);
  }

  /**
   * Whether an integrated search can be served locally: it names at least one source and every
   * source it names — the ontology of each ontology/branch constraint and the source ontology of
   * each enumerated class — is locally served. Value-set constraints are never local. This is
   * conservative on purpose: a search touching any non-local source goes wholly to BioPortal.
   */
  private boolean integratedSearchServedLocally(ValueConstraints vc) {
    if (vc == null) {
      return false;
    }
    List<String> acronyms = new ArrayList<>();
    if (vc.getOntologies() != null) {
      for (OntologyValueConstraint o : vc.getOntologies()) {
        acronyms.add(o.getAcronym());
      }
    }
    if (vc.getBranches() != null) {
      for (BranchValueConstraint b : vc.getBranches()) {
        acronyms.add(b.getAcronym());
      }
    }
    if (vc.getClasses() != null) {
      for (ClassValueConstraint c : vc.getClasses()) {
        acronyms.add(c.getSource() == null ? null : Util.getShortIdentifier(c.getSource()));
      }
    }
    // A value-set constraint is served locally when its collection is served locally: the collection
    // is a snapshot and the values are the value-set class's children.
    if (vc.getValueSets() != null) {
      for (ValueSetValueConstraint v : vc.getValueSets()) {
        acronyms.add(vsCollectionAcronym(v.getVsCollection()));
      }
    }
    if (acronyms.isEmpty()) {
      return false;
    }
    for (String acronym : acronyms) {
      if (acronym == null || !availability.isLocal(acronym)) {
        return false;
      }
    }
    return true;
  }

  /** The snapshot acronym for a value-set collection: the bare acronym, or the last path segment
   *  when it is given as the full registry URL (e.g. .../ontologies/CEDARVS -> CEDARVS). */
  private static String vsCollectionAcronym(String vsCollection) {
    if (vsCollection == null) {
      return null;
    }
    int slash = vsCollection.lastIndexOf('/');
    return slash >= 0 ? vsCollection.substring(slash + 1) : vsCollection;
  }

  @Override
  public PagedResults<SearchResult> integratedRetrieve(ValueConstraints valueConstraints, int page, int pageSize,
                                                       String apiKey) throws IOException {
    return remote.integratedRetrieve(valueConstraints, page, pageSize, apiKey);
  }

  /* ---------------------------------------------------------------------------------------------
   * Bucket A — ontologies, classes, properties. Ontology-scoped reads route to local when ready.
   * ------------------------------------------------------------------------------------------- */

  @Override
  public boolean isLocalOnly() {
    return localOnly;
  }

  @Override
  public List<Ontology> findAllOntologies(boolean includeDetails, String apiKey) throws IOException {
    // Only under localOnly (a fully offline deployment) does the server report just the ontologies it
    // versions locally. Otherwise the local store is a partial, incremental cutover and BioPortal is
    // still the source for everything not migrated, so the list must be the full remote registry, plus
    // any locally-served ontology BioPortal happens to omit — reporting only the allowlist would drop
    // every not-yet-migrated ontology from the picker. Local and remote share the same ontology @id
    // (https://data.bioontology.org/ontologies/ACRONYM), so the merge dedups cleanly. The remote entry
    // wins for an ontology present in both: its list metadata (full display name, submission details)
    // is richer than the catalog's, and the picker shows that name — overwriting DOID's
    // "Human Disease Ontology" with the bare acronym would degrade every migrated ontology's label.
    if (local != null) {
      List<Ontology> served = local.findAllOntologies(includeDetails, apiKey);
      if (localOnly) {
        return served;
      }
      if (!served.isEmpty()) {
        LinkedHashMap<String, Ontology> merged = new LinkedHashMap<>();
        for (Ontology o : remote.findAllOntologies(includeDetails, apiKey)) {
          merged.put(o.getId(), o);
        }
        for (Ontology o : served) {   // add only ontologies BioPortal omits; never clobber its metadata
          merged.putIfAbsent(o.getId(), o);
        }
        return new ArrayList<>(merged.values());
      }
    }
    return remote.findAllOntologies(includeDetails, apiKey);
  }

  @Override
  public List<OntologyVersion> getVersions(String ontology) throws IOException {
    // Versions are a local-store concept; a locally-served ontology reports its versions, everything
    // else reports none (the remote backend returns an empty list).
    return dispatch(ontology, s -> s.getVersions(ontology));
  }

  @Override
  public VersionTriple resolveCurrentVersion(String ontology) throws IOException {
    // A local-store concept: a locally-served ontology resolves its current triple; everything else
    // returns null (the remote backend has no content-hash triple), signalling "cannot freeze here".
    return dispatch(ontology, s -> s.resolveCurrentVersion(ontology));
  }

  @Override
  public VersionTriple resolveCurrentVersionForClass(String classIri) throws IOException {
    // Only the local store can map a class IRI's namespace to an ontology and resolve its triple; the
    // remote backend has none. The ontology is derived inside, so this cannot go through dispatch(ontology).
    return local != null ? local.resolveCurrentVersionForClass(classIri) : null;
  }

  @Override
  public VersionTriple resolveCurrentVersionForValueSetCollection(String vsCollection) throws IOException {
    // Only the local store has content-hash snapshots of value-set collections; BioPortal has none.
    // The collection is not one of the routed ontologies, so this bypasses dispatch(ontology).
    return local != null ? local.resolveCurrentVersionForValueSetCollection(vsCollection) : null;
  }

  @Override
  public VersionDiff diffVersions(String ontology, String fromVersion, String toVersion) throws IOException {
    return dispatch(ontology, s -> s.diffVersions(ontology, fromVersion, toVersion));
  }

  @Override
  public Ontology findOntology(String id, boolean includeDetails, String apiKey) throws IOException {
    return dispatch(id, s -> s.findOntology(id, includeDetails, apiKey));
  }

  @Override
  public List<OntologyClass> getRootClasses(String ontologyId, boolean isFlat, String apiKey) throws IOException {
    return dispatchBrowse(ontologyId, s -> s.getRootClasses(ontologyId, isFlat, apiKey));
  }

  @Override
  public List<OntologyProperty> getRootProperties(String ontologyId, String apiKey) throws IOException {
    return dispatchBrowse(ontologyId, s -> s.getRootProperties(ontologyId, apiKey));
  }

  @Override
  public OntologyClass findRegularClass(String id, String ontology, String apiKey) throws IOException {
    return dispatch(ontology, s -> s.findRegularClass(id, ontology, apiKey));
  }

  @Override
  public OntologyClass findClass(String id, String ontology, String apiKey, String lang) throws IOException {
    return dispatch(ontology, s -> s.findClass(id, ontology, apiKey, lang));
  }

  @Override
  public PagedResults<OntologyClass> findAllClassesInOntology(String ontology, int page, int pageSize, String apiKey)
      throws IOException {
    return dispatch(ontology, s -> s.findAllClassesInOntology(ontology, page, pageSize, apiKey));
  }

  @Override
  public List<TreeNode> getClassTree(String id, String ontology, boolean isFlat, String apiKey) throws IOException {
    return dispatchBrowse(ontology, s -> s.getClassTree(id, ontology, isFlat, apiKey));
  }

  @Override
  public PagedResults<OntologyClass> getClassChildren(String id, String ontology, int page, int pageSize, String apiKey)
      throws IOException {
    return dispatch(ontology, s -> s.getClassChildren(id, ontology, page, pageSize, apiKey));
  }

  @Override
  public PagedResults<OntologyClass> getClassDescendants(String id, String ontology, int page, int pageSize,
                                                         String apiKey) throws IOException {
    return dispatch(ontology, s -> s.getClassDescendants(id, ontology, page, pageSize, apiKey));
  }

  @Override
  public List<OntologyClass> getClassParents(String id, String ontology, String apiKey) throws IOException {
    return dispatch(ontology, s -> s.getClassParents(id, ontology, apiKey));
  }

  @Override
  public OntologyProperty findProperty(String id, String ontology, String apiKey) throws IOException {
    return dispatch(ontology, s -> s.findProperty(id, ontology, apiKey));
  }

  @Override
  public List<OntologyProperty> findAllPropertiesInOntology(String ontology, String apiKey) throws IOException {
    return dispatch(ontology, s -> s.findAllPropertiesInOntology(ontology, apiKey));
  }

  @Override
  public List<TreeNode> getPropertyTree(String id, String ontology, String apiKey) throws IOException {
    return dispatchBrowse(ontology, s -> s.getPropertyTree(id, ontology, apiKey));
  }

  @Override
  public List<OntologyProperty> getPropertyChildren(String id, String ontology, String apiKey) throws IOException {
    return dispatch(ontology, s -> s.getPropertyChildren(id, ontology, apiKey));
  }

  @Override
  public List<OntologyProperty> getPropertyDescendants(String id, String ontology, String apiKey) throws IOException {
    return dispatch(ontology, s -> s.getPropertyDescendants(id, ontology, apiKey));
  }

  @Override
  public List<OntologyProperty> getPropertyParents(String id, String ontology, String apiKey) throws IOException {
    return dispatch(ontology, s -> s.getPropertyParents(id, ontology, apiKey));
  }

  /* ---------------------------------------------------------------------------------------------
   * Values scoped to an ontology — Bucket A.
   * ------------------------------------------------------------------------------------------- */

  @Override
  public Value findRegularValue(String id, String ontology, String apiKey) throws IOException {
    return dispatch(ontology, s -> s.findRegularValue(id, ontology, apiKey));
  }

  @Override
  public Value findValue(String id, String ontology, String apiKey) throws IOException {
    return dispatch(ontology, s -> s.findValue(id, ontology, apiKey));
  }

  @Override
  public PagedResults<Value> findAllValuesInValueSetByValue(String id, String ontology, int page, int pageSize,
                                                            String apiKey) throws IOException {
    return dispatch(ontology, s -> s.findAllValuesInValueSetByValue(id, ontology, page, pageSize, apiKey));
  }

  /* ---------------------------------------------------------------------------------------------
   * Value sets and value-set collections. Keyed by value-set collection, not ontology; local
   * routing for these is a later decision, so they are served remotely for now.
   * ------------------------------------------------------------------------------------------- */

  @Override
  public ValueSet findRegularValueSet(String id, String vsCollection, String apiKey) throws IOException {
    return remote.findRegularValueSet(id, vsCollection, apiKey);
  }

  @Override
  public ValueSet findValueSet(String id, String vsCollection, String apiKey) throws IOException {
    return remote.findValueSet(id, vsCollection, apiKey);
  }

  @Override
  public ValueSet findValueSetByValue(String id, String vsCollection, String apiKey) throws IOException {
    return remote.findValueSetByValue(id, vsCollection, apiKey);
  }

  @Override
  public PagedResults<ValueSet> findValueSetsByVsCollection(String vsCollection, int page, int pageSize, String apiKey)
      throws IOException {
    return remote.findValueSetsByVsCollection(vsCollection, page, pageSize, apiKey);
  }

  @Override
  public List<ValueSet> findAllValueSets(String apiKey) throws IOException {
    return remote.findAllValueSets(apiKey);
  }

  @Override
  public PagedResults<Value> findValuesByValueSet(String vsId, String vsCollection, int page, int pageSize,
                                                  String apiKey) throws IOException {
    return remote.findValuesByValueSet(vsId, vsCollection, page, pageSize, apiKey);
  }

  @Override
  public List<ValueSetCollection> findAllVSCollections(boolean includeDetails, String apiKey) throws IOException {
    return remote.findAllVSCollections(includeDetails, apiKey);
  }

  @Override
  public TreeNode getValueTree(String id, String vsCollection, String apiKey) throws IOException {
    return remote.getValueTree(id, vsCollection, apiKey);
  }

  @Override
  public TreeNode getValueSetTree(String id, String vsCollection, String apiKey) throws IOException {
    return remote.getValueSetTree(id, vsCollection, apiKey);
  }

  /* ---------------------------------------------------------------------------------------------
   * Bucket C — provisional writes and their reads. The local store is read-only, so these are
   * always served remotely.
   * ------------------------------------------------------------------------------------------- */

  @Override
  public OntologyClass findProvisionalClass(String id, String apiKey) throws IOException {
    return remote.findProvisionalClass(id, apiKey);
  }

  @Override
  public PagedResults<OntologyClass> findAllProvisionalClasses(String ontology, int page, int pageSize, String apiKey)
      throws IOException {
    return remote.findAllProvisionalClasses(ontology, page, pageSize, apiKey);
  }

  @Override
  public Relation findProvisionalRelation(String id, String apiKey) throws IOException {
    return remote.findProvisionalRelation(id, apiKey);
  }

  @Override
  public ValueSet findProvisionalValueSet(String id, String apiKey) throws IOException {
    return remote.findProvisionalValueSet(id, apiKey);
  }

  @Override
  public Value findProvisionalValue(String id, String apiKey) throws IOException {
    return remote.findProvisionalValue(id, apiKey);
  }

}
