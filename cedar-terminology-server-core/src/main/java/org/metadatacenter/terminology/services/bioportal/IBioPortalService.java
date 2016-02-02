package org.metadatacenter.terminology.services.bioportal;

import org.metadatacenter.terminology.services.bioportal.domainObjects.OntologyClass;
import org.metadatacenter.terminology.services.bioportal.domainObjects.Relation;
import org.metadatacenter.terminology.services.bioportal.domainObjects.SearchResults;

import java.io.IOException;
import java.util.List;

public interface IBioPortalService
{
  SearchResults search(String q, List<String> scope, List<String> sources, int page, int pageSize, boolean displayContext,
    boolean displayLinks, String apiKey) throws IOException;

  /** Provisional classes **/
  OntologyClass createClass(OntologyClass c, String apiKey) throws IOException;
  OntologyClass findClass(String id, String apiKey) throws IOException;
  List<OntologyClass> findAllProvisionalClasses(String ontology, String apiKey) throws IOException;
  // TODO: This call is pending (to be released by the BioPortal team first)
  OntologyClass updateClass(OntologyClass c, String apiKey) throws IOException;
  // TODO: This call is pending (to be released by the BioPortal team first)
  void deleteClass(String classId, String apiKey) throws IOException;

  /** Provisional relations **/
  Relation createRelation(Relation relation, String apiKey) throws IOException;
  Relation findRelation(String id, String apiKey) throws IOException;
  // TODO: This call is pending (to be released by the BioPortal team first)
  Relation deleteRelation(String id, String apiKey) throws IOException;

  /** Value sets **/
  // TODO:
  // - Create a Value Set
  // - Return all value sets in a specific value set collection
  // - Return all values in a value set (including provisional classes)
  // - Retrieve value set by id
  // - Update value set- Delete value set

  /** Value set items **/
  // TODO:
  //  - Create a Value Set Item
  //  - Retrieve value set item by id
  //  - Update value set item
  //  - Delete value set item

  /** TODO:
   * - Get all ontologies
   * - Get ontology details
   * - Get ontology size
   * - Get ontology categories
   * - Get class details / and value set details
   * - Get class tree
   * - Get class children
   * - Get class parents
   * - Get ontology tree root
   * - Get all value sets in a value set collection
   * - Get ontology classes
   */
}
