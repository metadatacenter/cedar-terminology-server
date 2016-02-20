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
  // TODO: Adapt BioPortal to return provisional classes too
  // TODO: Add attribute with result type on the BioPortal side
    SearchResults<OntologyClass> search(String q, List<String> scope, List<String> sources, int page, int pageSize, boolean displayContext,
      boolean displayLinks, String apiKey) throws IOException;

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

  void updateProvisionalClass(OntologyClass c, String apiKey) throws IOException;

  void deleteProvisionalClass(String classId, String apiKey) throws IOException;

  /**
   * Relations
   **/

  Relation createProvisionalRelation(Relation relation, String apiKey) throws IOException;

  Relation findProvisionalRelation(String id, String apiKey) throws IOException;

//  void updateProvisionalRelation(Relation r, String apiKey) throws IOException;

  void deleteProvisionalRelation(String classId, String apiKey) throws IOException;

  /**
   * Value sets
   **/

  ValueSet createProvisionalValueSet(ValueSet vs, String apiKey) throws IOException;

  // TODO: implement find for regular classes?
  // TODO: this call is not checking if the class that is returned is actually a value set. Some type property is required for each class to specify its type
  ValueSet findProvisionalValueSet(String id, String apiKey) throws IOException;

  // TODO: does not support provisional classes yet
  SearchResults<ValueSet> findValueSetsByVsCollection(String vsCollection, String apiKey) throws IOException;

  // TODO: This call does not return provisional classes yet and the vs must be a regular class
  SearchResults<Value> findValuesByValueSet(String vsId, String vsCollection, String apiKey) throws IOException;

  // TODO: not supported by BioPortal yet
  // - Update Provisional Value Set
  // - Delete Provisional Value Set

  /**
   * Values
   **/

  Value createProvisionalValue(Value v, String apiKey) throws IOException;

  Value findProvisionalValue(String id, String apiKey) throws IOException;

  // TODO: not supported by BioPortal yet
  //  - Update value set item
  //  - Delete value set item
}
