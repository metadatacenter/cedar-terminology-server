package org.metadatacenter.terms;

import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;
import org.metadatacenter.terms.customObjects.SearchResults;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.terms.domainObjects.ValueSet;

import java.io.IOException;
import java.util.List;

public interface ITerminologyService
{
  //  SearchResults search(String q, List<String> scope, List<String> sources, int page, int pageSize, boolean
  // displayContext,
  //    boolean displayLinks, String apiKey) throws IOException;
  //
  //  /** TODO:
  //   * - Get all ontologies
  //   * - Get ontology details
  //   * - Get ontology size
  //   * - Get ontology categories
  //   * - Get class details / and value set details
  //   * - Get class tree
  //   * - Get class children
  //   * - Get class parents
  //   * - Get ontology tree root
  //   * - Get ontology classes
  //   */
  //

  /**
   * Classes
   **/

  // Currently, all classes are created as BioPortal provisional classes by default. There is no way to create
  // regular classes through the BioPortal API
  OntologyClass createProvisionalClass(OntologyClass c, String apiKey) throws IOException;

  OntologyClass findProvisionalClass(String id, String apiKey) throws IOException;

  List<OntologyClass> findAllProvisionalClasses(String ontology, String apiKey) throws IOException;

  // TODO: This call is pending (to be released by the BioPortal team first)
  //OntologyClass updateProvisionalClass(OntologyClass c, String apiKey) throws IOException;

  // TODO: This call is pending (to be released by the BioPortal team first)
  //void deleteProvisionalClass(String classId, String apiKey) throws IOException;

  /**
   * Relations
   **/

  Relation createProvisionalRelation(Relation relation, String apiKey) throws IOException;

  Relation findProvisionalRelation(String id, String apiKey) throws IOException;

  // TODO: This call is pending (to be released by the BioPortal team first)
  //  void deleteProvisionalRelation(String classId, String apiKey) throws IOException;

  /**
   * Value sets
   **/

  ValueSet createProvisionalValueSet(ValueSet vs, String apiKey) throws IOException;

  // TODO: implement find for regular classes?
  ValueSet findProvisionalValueSet(String id, String apiKey) throws IOException;

  // TODO: does not support provisional classes yet
  SearchResults<ValueSet> findValueSetsByVsCollection(String vsCollection, String apiKey) throws IOException;

  // TODO: This call does not return provisional classes yet and the vs must be a regular class
  SearchResults<Value> findValuesByValueSet(String vsId, String vsCollection, String apiKey) throws IOException;

  // TODO:
  // - Update value set- Delete value set

  /** Value set items **/
  // TODO:
  //  - Create a Value Set Item
  //  - Retrieve value set item by id
  //  - Update value set item
  //  - Delete value set item
}
