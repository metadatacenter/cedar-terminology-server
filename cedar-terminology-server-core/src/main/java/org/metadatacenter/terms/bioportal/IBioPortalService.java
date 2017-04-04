package org.metadatacenter.terms.bioportal;

import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.domainObjects.*;

import java.io.IOException;
import java.util.List;

public interface IBioPortalService {

  /**
   * Search
   **/

  BpPagedResults<BpClass> search(String q, List<String> scope, List<String> sources, boolean suggest, String source,
                                 String subtreeRootId, int maxDepth, int page, int pageSize, boolean displayContext,
                                 boolean displayLinks, String apiKey) throws IOException;

  BpPagedResults<BpProperty> propertySearch(String q, List<String> sources, boolean exactMatch, boolean
      requireDefinitions, int page, int pageSize, boolean displayContext, boolean displayLinks, String apiKey) throws
      IOException;

  /**
   * Ontologies
   **/

  BpOntology findBpOntologyById(String id, String apiKey) throws IOException;

  BpOntologySubmission getOntologyLatestSubmission(String id, String apiKey) throws IOException;

  List<BpOntology> findAllOntologies(String apiKey) throws IOException;

  BpOntologyMetrics findOntologyMetrics(String id, String apiKey) throws IOException;

  List<BpOntologyCategory> findOntologyCategories(String id, String apiKey) throws IOException;

  List<BpClass> getRootClasses(String ontologyId, String apiKey) throws IOException;

  /**
   * Classes
   **/

  BpClass findBpClassById(String id, String ontology, String apiKey) throws IOException;

  BpPagedResults<BpClass> findAllClassesInOntology(String ontology, int page, int pageSize, String apiKey) throws
      IOException;

  List<BpTreeNode> getClassTree(String id, String ontology, String apiKey) throws IOException;

  BpPagedResults<BpClass> findValueSetsByValueSetCollection(String vsCollection, int page, int pageSize, String
      apiKey) throws IOException;

  BpPagedResults<BpClass> findValuesByValueSet(String vsId, String vsCollection, int page, int pageSize, String
      apiKey) throws IOException;

  BpPagedResults<BpClass> getClassChildren(String id, String ontology, int page, int pageSize, String apiKey) throws
      IOException;

  BpPagedResults<BpClass> getClassDescendants(String id, String ontology, int page, int pageSize, String apiKey)
      throws IOException;

  List<BpClass> getClassParents(String id, String ontology, String apiKey) throws IOException;

  /**
   * Provisional Classes
   **/

  BpProvisionalClass createBpProvisionalClass(BpProvisionalClass c, String apiKey) throws IOException;

  BpProvisionalClass findBpProvisionalClassById(String id, String apiKey) throws IOException;

  BpPagedResults<BpProvisionalClass> findAllProvisionalClasses(String ontology, int page, int pageSize, String
      apiKey) throws IOException;

  void updateProvisionalClass(BpProvisionalClass c, String apiKey) throws IOException;

  void deleteProvisionalClass(String id, String apiKey) throws IOException;

  /**
   * Provisional Relations
   **/

  BpProvisionalRelation createBpProvisionalRelation(BpProvisionalRelation r, String apiKey) throws IOException;

  BpProvisionalRelation findProvisionalRelationById(String id, String apiKey) throws IOException;

//  void updateProvisionalRelation(BpProvisionalRelation r, String apiKey) throws IOException;

  void deleteProvisionalRelation(String id, String apiKey) throws IOException;

}
