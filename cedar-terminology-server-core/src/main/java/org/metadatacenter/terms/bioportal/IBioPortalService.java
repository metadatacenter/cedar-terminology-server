package org.metadatacenter.terms.bioportal;

import org.metadatacenter.terms.bioportal.domainObjects.BpClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalRelation;
import org.metadatacenter.terms.bioportal.customObjects.BpSearchResults;
import org.metadatacenter.terms.customObjects.SearchResults;

import java.io.IOException;
import java.util.List;

public interface IBioPortalService
{

  BpSearchResults<BpClass> search(String q, List<String> scope, List<String> sources, int page, int pageSize, boolean displayContext,
    boolean displayLinks, String apiKey) throws IOException;

  /**
   * Provisional Classes
   **/

  BpProvisionalClass createBpProvisionalClass(BpProvisionalClass pc, String apiKey) throws IOException;

  BpProvisionalClass findBpProvisionalClassById(String id, String apiKey) throws IOException;

  List<BpProvisionalClass> findAllProvisionalClasses(String ontology, String apiKey) throws IOException;

  void updateProvisionalClass(BpProvisionalClass c, String apiKey) throws IOException;

  //void deleteProvisionalClass(BpProvisionalClass c, String apiKey) throws IOException;

  /**
   * Provisional Relations
   **/

  BpProvisionalRelation createBpProvisionalRelation(BpProvisionalRelation pr, String apiKey) throws IOException;

  BpProvisionalRelation findProvisionalRelationById(String id, String apiKey) throws IOException;

  /**
   * Classes
   **/

  BpSearchResults<BpClass> findValueSetsByValueSetCollection(String vsCollection, String apiKey) throws IOException;

  BpSearchResults<BpClass> findValuesByValueSet(String vsId, String vsCollection, String apiKey) throws IOException;

}
