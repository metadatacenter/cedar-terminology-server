package org.metadatacenter.terms;

import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface ITerminologyService {

  /**
   * Search
   */
  PagedResults<SearchResult> search(String q, List<String> scope, List<String> sources, boolean suggest, String
      source, String
                                        subtreeRootId, int maxDepth, int page, int pageSize, boolean displayContext,
                                    boolean displayLinks, String apiKey, List<String> valueSetsIds) throws IOException;

  PagedResults<SearchResult> propertySearch(String q, List<String> sources, boolean exactMatch, boolean
      requireDefinitions, int page, int pageSize, boolean displayContext, boolean displayLinks, String apiKey) throws
      IOException;

  /**
   * CEDAR Integrated Search
   */
  /** Integrated search, returning result labels in {@code lang} (BCP-47) for locally-served, single-source
   *  constraints; null/blank keeps the default label. The remote path uses BioPortal's own default. */
  PagedResults<SearchResult> integratedSearch(Optional<String> q, ValueConstraints valueConstraints,
                        int page, int pageSize, String apiKey, String lang) throws IOException;

  default PagedResults<SearchResult> integratedSearch(Optional<String> q, ValueConstraints valueConstraints,
                        int page, int pageSize, String apiKey) throws IOException {
    return integratedSearch(q, valueConstraints, page, pageSize, apiKey, null);
  }

  /**
   * CEDAR Integrated Retrieve
   */
  PagedResults<SearchResult> integratedRetrieve(ValueConstraints valueConstraints,
                        int page, int pageSize, String apiKey) throws IOException;

  /**
   * Ontologies
   */
  List<Ontology> findAllOntologies(boolean includeDetails, String apiKey) throws IOException;

  /**
   * Whether this backend serves only its local catalog, with no BioPortal fallback. When true, the
   * ontology list is authoritatively small (just the versioned ontologies) rather than the full
   * ~1300-ontology registry, so callers must not treat a short list as a degraded remote fetch. The
   * plain BioPortal backend is never local-only.
   */
  default boolean isLocalOnly() {
    return false;
  }

  /**
   * The versions of an ontology available in the local version-pinned store, or empty when it is not
   * served locally (BioPortal has no equivalent). Each carries the content-hash id that pins it.
   */
  List<OntologyVersion> getVersions(String ontology) throws IOException;

  /**
   * The version triple ({@code id}, {@code effectiveDate}, {@code declaredVersion}) of an ontology's
   * current ("latest") snapshot, or {@code null} when it is not served locally (BioPortal has no
   * content-hash triple). This is the terminology server's one publish-time capability: the publish
   * pipeline calls it per value-constraint entry to freeze the entry against its ontology's current
   * state. A branch, class, or value-set entry passes its ontology's acronym — the triple pins the
   * ontology snapshot the sub-entry lives in.
   */
  VersionTriple resolveCurrentVersion(String ontology) throws IOException;

  /**
   * The version triple of the ontology that owns a class/term IRI — the freeze capability for a
   * class-valued constraint, which names a term but not its ontology. Maps the IRI to its ontology by
   * namespace, then resolves that ontology's current triple. {@code null} when the ontology cannot be
   * determined unambiguously or is not served locally.
   */
  VersionTriple resolveCurrentVersionForClass(String classIri) throws IOException;

  /**
   * The version triple of a value-set collection's current ("latest") snapshot — the freeze capability
   * for a value-set-valued constraint, whose value space is a BioPortal value-set collection rather
   * than an ontology. {@code null} when the collection is not ingested and served locally. Value-set
   * collections are versioned by the same content-hash mechanism as ontologies, so this mirrors
   * {@link #resolveCurrentVersion}, keyed by the collection acronym.
   */
  VersionTriple resolveCurrentVersionForValueSetCollection(String vsCollection) throws IOException;

  /**
   * The vocabulary diff between two locally-stored versions of an ontology (each a version_id or a
   * tag such as {@code latest}), or null when the ontology or a version is not available locally.
   */
  VersionDiff diffVersions(String ontology, String fromVersion, String toVersion) throws IOException;

  Ontology findOntology(String id, boolean includeDetails, String apiKey) throws IOException;

  List<OntologyClass> getRootClasses(String ontologyId, boolean isFlat, String apiKey) throws IOException;

  List<OntologyProperty> getRootProperties(String ontologyId, String apiKey) throws IOException;

  /**
   * Classes
   **/

  OntologyClass findProvisionalClass(String id, String apiKey) throws IOException;

  OntologyClass findRegularClass(String id, String ontology, String apiKey) throws IOException;

  /** Find a class, returning its label in {@code lang} (a BCP-47 code) when the ontology is served
   *  locally and has that language; null/blank {@code lang} keeps the default (English-preferred) label.
   *  The remote (BioPortal) path uses BioPortal's own default regardless. */
  OntologyClass findClass(String id, String ontology, String apiKey, String lang) throws IOException;

  default OntologyClass findClass(String id, String ontology, String apiKey) throws IOException {
    return findClass(id, ontology, apiKey, null);
  }

  PagedResults<OntologyClass> findAllClassesInOntology(String ontology, int page, int pageSize, String apiKey) throws
      IOException;

  PagedResults<OntologyClass> findAllProvisionalClasses(String ontology, int page, int pageSize, String apiKey)
      throws IOException;

  List<TreeNode> getClassTree(String id, String ontology, boolean isFlat, String apiKey) throws IOException;

  PagedResults<OntologyClass> getClassChildren(String id, String ontology, int page, int pageSize, String apiKey)
      throws IOException;

  PagedResults<OntologyClass> getClassDescendants(String id, String ontology, int page, int pageSize, String apiKey)
      throws IOException;

  List<OntologyClass> getClassParents(String id, String ontology, String apiKey) throws IOException;

  /**
   * Relations
   **/

  Relation findProvisionalRelation(String id, String apiKey) throws IOException;

  /**
   * Value sets
   **/

  ValueSet findProvisionalValueSet(String id, String apiKey) throws IOException;

  ValueSet findRegularValueSet(String id, String vsCollection, String apiKey) throws IOException;

  ValueSet findValueSet(String id, String vsCollection, String apiKey) throws IOException;

  ValueSet findValueSetByValue(String id, String vsCollection, String apiKey) throws IOException;

  // TODO: does not support provisional classes yet
  PagedResults<ValueSet> findValueSetsByVsCollection(String vsCollection, int page, int pageSize, String apiKey)
      throws IOException;

  List<ValueSet> findAllValueSets(String apiKey) throws IOException;

  // TODO: This call does not return provisional classes yet and the vs must be a regular class
  PagedResults<Value> findValuesByValueSet(String vsId, String vsCollection, int page, int pageSize, String apiKey)
      throws IOException;

  List<ValueSetCollection> findAllVSCollections(boolean includeDetails, String apiKey) throws IOException;

  /**
   * Values
   **/

  Value findProvisionalValue(String id, String apiKey) throws IOException;

  Value findRegularValue(String id, String ontology, String apiKey) throws IOException;

  Value findValue(String id, String ontology, String apiKey) throws IOException;

  TreeNode getValueTree(String id, String vsCollection, String apiKey) throws IOException;

  TreeNode getValueSetTree(String id, String vsCollection, String apiKey) throws IOException;

  PagedResults<Value> findAllValuesInValueSetByValue(String id, String ontology, int page, int pageSize, String
      apiKey) throws IOException;

  /**
   * Properties
   */

  OntologyProperty findProperty(String id, String ontology, String apiKey) throws IOException;

  List<OntologyProperty> findAllPropertiesInOntology(String ontology, String apiKey) throws IOException;

  List<TreeNode> getPropertyTree(String id, String ontology, String apiKey) throws IOException;

  List<OntologyProperty> getPropertyChildren(String id, String ontology, String apiKey)
      throws IOException;

  List<OntologyProperty> getPropertyDescendants(String id, String ontology, String apiKey)
      throws IOException;

  List<OntologyProperty> getPropertyParents(String id, String ontology, String apiKey) throws IOException;

}
