package org.metadatacenter.terms;

import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;

import java.io.IOException;
import java.util.List;

public interface ITerminologyService {

  /**
   * Search
   */
  PagedResults<SearchResult> search(String q, List<String> scope, List<String> sources, boolean suggest, String source, String
      subtreeRootId, int maxDepth, int page, int pageSize, boolean displayContext,
                                    boolean displayLinks, String apiKey, List<String> valueSetsIds) throws IOException;

  PagedResults<PropertySearchResult> propertySearch(String q, List<String> sources, int page, int pageSize, boolean displayContext,
                                    boolean displayLinks, String apiKey) throws IOException;

  /**
   * Ontologies
   */
  List<Ontology> findAllOntologies(boolean includeDetails, String apiKey) throws IOException;

  Ontology findOntology(String id, boolean includeDetails, String apiKey) throws IOException;

  List<OntologyClass> getRootClasses(String ontologyId, boolean isFlat, String apiKey) throws IOException;

  /**
   * Classes
   **/

  OntologyClass createProvisionalClass(OntologyClass c, String apiKey) throws IOException;

  OntologyClass findProvisionalClass(String id, String apiKey) throws IOException;

  OntologyClass findRegularClass(String id, String ontology, String apiKey) throws IOException;

  OntologyClass findClass(String id, String ontology, String apiKey) throws IOException;

  PagedResults<OntologyClass> findAllClassesInOntology(String ontology, int page, int pageSize, String apiKey) throws IOException;

  PagedResults<OntologyClass> findAllProvisionalClasses(String ontology, int page, int pageSize, String apiKey) throws IOException;

  void updateProvisionalClass(OntologyClass c, String apiKey) throws IOException;

  void deleteProvisionalClass(String id, String apiKey) throws IOException;

  List<TreeNode> getClassTree(String id, String ontology, boolean isFlat, String apiKey) throws IOException;

  PagedResults<OntologyClass> getClassChildren(String id, String ontology, int page, int pageSize, String apiKey)
      throws IOException;

  PagedResults<OntologyClass> getClassDescendants(String id, String ontology, int page, int pageSize, String apiKey)
      throws IOException;

  List<OntologyClass> getClassParents(String id, String ontology, String apiKey) throws IOException;

  /**
   * Relations
   **/

  Relation createProvisionalRelation(Relation relation, String apiKey) throws IOException;

  Relation findProvisionalRelation(String id, String apiKey) throws IOException;

//  void updateProvisionalRelation(Relation r, String apiKey) throws IOException;

  void deleteProvisionalRelation(String id, String apiKey) throws IOException;

  /**
   * Value sets
   **/

  ValueSet createProvisionalValueSet(ValueSet vs, String apiKey) throws IOException;

  ValueSet findProvisionalValueSet(String id, String apiKey) throws IOException;

  ValueSet findRegularValueSet(String id, String vsCollection, String apiKey) throws IOException;

  ValueSet findValueSet(String id, String vsCollection, String apiKey) throws IOException;

  ValueSet findValueSetByValue(String id, String vsCollection, String apiKey) throws IOException;

  void updateProvisionalValueSet(ValueSet vs, String apiKey) throws IOException;

  void deleteProvisionalValueSet(String id, String apiKey) throws IOException;

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

  Value createProvisionalValue(Value v, String apiKey) throws IOException;

  Value findProvisionalValue(String id, String apiKey) throws IOException;

  Value findRegularValue(String id, String ontology, String apiKey) throws IOException;

  Value findValue(String id, String ontology, String apiKey) throws IOException;

  TreeNode getValueTree(String id, String vsCollection, String apiKey) throws IOException;

  TreeNode getValueSetTree(String id, String vsCollection, String apiKey) throws IOException;

  PagedResults<Value> findAllValuesInValueSetByValue(String id, String ontology, int page, int pageSize, String apiKey) throws IOException;

  void updateProvisionalValue(Value v, String apiKey) throws IOException;

  void deleteProvisionalValue(String id, String apiKey) throws IOException;
}
