package org.metadatacenter.terms;

import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * An {@link ITerminologyService} that dispatches each call to either a local, version-aware
 * backend (e.g. a SQLite-backed store) or a remote backend (BioPortal), one ontology at a time.
 *
 * This is the seam for the incremental migration away from the BioPortal proxy: an ontology is
 * served locally once it has been ingested and {@link LocalAvailability#isLocal(String)} reports
 * it as available; every other request falls through to the remote backend, so behavior is
 * unchanged until a local backend is wired in.
 *
 * Only the ontology-scoped hierarchy and lookup operations ("Bucket A") are eligible for local
 * routing. Search operations ("Bucket B") and provisional write operations ("Bucket C") are
 * always sent to the remote backend for now; both are migration decisions deferred to later work.
 */
public class RoutingTerminologyService implements ITerminologyService {

  /**
   * Decides whether a given ontology is currently served by the local backend.
   */
  @FunctionalInterface
  public interface LocalAvailability {
    boolean isLocal(String ontology);
  }

  private final ITerminologyService remote;
  private final ITerminologyService local;
  private final LocalAvailability availability;

  /**
   * Remote-only: every call is delegated to {@code remote}. Behavior is identical to using the
   * remote backend directly. Use this until a local backend exists.
   */
  public RoutingTerminologyService(ITerminologyService remote) {
    this(remote, null, ontology -> false);
  }

  public RoutingTerminologyService(ITerminologyService remote, ITerminologyService local,
                                   LocalAvailability availability) {
    this.remote = remote;
    this.local = local;
    this.availability = availability;
  }

  /**
   * Returns the backend that should serve a request scoped to {@code ontology}: the local backend
   * when it is present and reports the ontology as available, otherwise the remote backend.
   */
  private ITerminologyService route(String ontology) {
    if (local != null && ontology != null && availability.isLocal(ontology)) {
      return local;
    }
    return remote;
  }

  /* ---------------------------------------------------------------------------------------------
   * Bucket B — search. Always remote (ranking is not yet reproduced locally).
   * ------------------------------------------------------------------------------------------- */

  @Override
  public PagedResults<SearchResult> search(String q, List<String> scope, List<String> sources, boolean suggest,
                                           String source, String subtreeRootId, int maxDepth, int page, int pageSize,
                                           boolean displayContext, boolean displayLinks, String apiKey,
                                           List<String> valueSetsIds) throws IOException {
    return remote.search(q, scope, sources, suggest, source, subtreeRootId, maxDepth, page, pageSize, displayContext,
        displayLinks, apiKey, valueSetsIds);
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
                                                     int pageSize, String apiKey) throws IOException {
    return remote.integratedSearch(q, valueConstraints, page, pageSize, apiKey);
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
  public List<Ontology> findAllOntologies(boolean includeDetails, String apiKey) throws IOException {
    // Aggregates across all sources; served remotely until the local catalog is authoritative.
    return remote.findAllOntologies(includeDetails, apiKey);
  }

  @Override
  public Ontology findOntology(String id, boolean includeDetails, String apiKey) throws IOException {
    return route(id).findOntology(id, includeDetails, apiKey);
  }

  @Override
  public List<OntologyClass> getRootClasses(String ontologyId, boolean isFlat, String apiKey) throws IOException {
    return route(ontologyId).getRootClasses(ontologyId, isFlat, apiKey);
  }

  @Override
  public List<OntologyProperty> getRootProperties(String ontologyId, String apiKey) throws IOException {
    return route(ontologyId).getRootProperties(ontologyId, apiKey);
  }

  @Override
  public OntologyClass findRegularClass(String id, String ontology, String apiKey) throws IOException {
    return route(ontology).findRegularClass(id, ontology, apiKey);
  }

  @Override
  public OntologyClass findClass(String id, String ontology, String apiKey) throws IOException {
    return route(ontology).findClass(id, ontology, apiKey);
  }

  @Override
  public PagedResults<OntologyClass> findAllClassesInOntology(String ontology, int page, int pageSize, String apiKey)
      throws IOException {
    return route(ontology).findAllClassesInOntology(ontology, page, pageSize, apiKey);
  }

  @Override
  public List<TreeNode> getClassTree(String id, String ontology, boolean isFlat, String apiKey) throws IOException {
    return route(ontology).getClassTree(id, ontology, isFlat, apiKey);
  }

  @Override
  public PagedResults<OntologyClass> getClassChildren(String id, String ontology, int page, int pageSize, String apiKey)
      throws IOException {
    return route(ontology).getClassChildren(id, ontology, page, pageSize, apiKey);
  }

  @Override
  public PagedResults<OntologyClass> getClassDescendants(String id, String ontology, int page, int pageSize,
                                                         String apiKey) throws IOException {
    return route(ontology).getClassDescendants(id, ontology, page, pageSize, apiKey);
  }

  @Override
  public List<OntologyClass> getClassParents(String id, String ontology, String apiKey) throws IOException {
    return route(ontology).getClassParents(id, ontology, apiKey);
  }

  @Override
  public OntologyProperty findProperty(String id, String ontology, String apiKey) throws IOException {
    return route(ontology).findProperty(id, ontology, apiKey);
  }

  @Override
  public List<OntologyProperty> findAllPropertiesInOntology(String ontology, String apiKey) throws IOException {
    return route(ontology).findAllPropertiesInOntology(ontology, apiKey);
  }

  @Override
  public List<TreeNode> getPropertyTree(String id, String ontology, String apiKey) throws IOException {
    return route(ontology).getPropertyTree(id, ontology, apiKey);
  }

  @Override
  public List<OntologyProperty> getPropertyChildren(String id, String ontology, String apiKey) throws IOException {
    return route(ontology).getPropertyChildren(id, ontology, apiKey);
  }

  @Override
  public List<OntologyProperty> getPropertyDescendants(String id, String ontology, String apiKey) throws IOException {
    return route(ontology).getPropertyDescendants(id, ontology, apiKey);
  }

  @Override
  public List<OntologyProperty> getPropertyParents(String id, String ontology, String apiKey) throws IOException {
    return route(ontology).getPropertyParents(id, ontology, apiKey);
  }

  /* ---------------------------------------------------------------------------------------------
   * Values scoped to an ontology — Bucket A.
   * ------------------------------------------------------------------------------------------- */

  @Override
  public Value findRegularValue(String id, String ontology, String apiKey) throws IOException {
    return route(ontology).findRegularValue(id, ontology, apiKey);
  }

  @Override
  public Value findValue(String id, String ontology, String apiKey) throws IOException {
    return route(ontology).findValue(id, ontology, apiKey);
  }

  @Override
  public PagedResults<Value> findAllValuesInValueSetByValue(String id, String ontology, int page, int pageSize,
                                                            String apiKey) throws IOException {
    return route(ontology).findAllValuesInValueSetByValue(id, ontology, page, pageSize, apiKey);
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
  public OntologyClass createProvisionalClass(OntologyClass c, String apiKey) throws IOException {
    return remote.createProvisionalClass(c, apiKey);
  }

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
  public void updateProvisionalClass(OntologyClass c, String apiKey) throws IOException {
    remote.updateProvisionalClass(c, apiKey);
  }

  @Override
  public void deleteProvisionalClass(String id, String apiKey) throws IOException {
    remote.deleteProvisionalClass(id, apiKey);
  }

  @Override
  public Relation createProvisionalRelation(Relation relation, String apiKey) throws IOException {
    return remote.createProvisionalRelation(relation, apiKey);
  }

  @Override
  public Relation findProvisionalRelation(String id, String apiKey) throws IOException {
    return remote.findProvisionalRelation(id, apiKey);
  }

  @Override
  public void deleteProvisionalRelation(String id, String apiKey) throws IOException {
    remote.deleteProvisionalRelation(id, apiKey);
  }

  @Override
  public ValueSet createProvisionalValueSet(ValueSet vs, String apiKey) throws IOException {
    return remote.createProvisionalValueSet(vs, apiKey);
  }

  @Override
  public ValueSet findProvisionalValueSet(String id, String apiKey) throws IOException {
    return remote.findProvisionalValueSet(id, apiKey);
  }

  @Override
  public void updateProvisionalValueSet(ValueSet vs, String apiKey) throws IOException {
    remote.updateProvisionalValueSet(vs, apiKey);
  }

  @Override
  public void deleteProvisionalValueSet(String id, String apiKey) throws IOException {
    remote.deleteProvisionalValueSet(id, apiKey);
  }

  @Override
  public Value createProvisionalValue(Value v, String apiKey) throws IOException {
    return remote.createProvisionalValue(v, apiKey);
  }

  @Override
  public Value findProvisionalValue(String id, String apiKey) throws IOException {
    return remote.findProvisionalValue(id, apiKey);
  }

  @Override
  public void updateProvisionalValue(Value v, String apiKey) throws IOException {
    remote.updateProvisionalValue(v, apiKey);
  }

  @Override
  public void deleteProvisionalValue(String id, String apiKey) throws IOException {
    remote.deleteProvisionalValue(id, apiKey);
  }
}
