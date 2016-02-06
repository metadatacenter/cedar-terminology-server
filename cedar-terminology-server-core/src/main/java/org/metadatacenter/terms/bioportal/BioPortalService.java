package org.metadatacenter.terms.bioportal;

import org.metadatacenter.terms.bioportal.dao.BpClassDAO;
import org.metadatacenter.terms.bioportal.dao.BpProvisionalClassDAO;
import org.metadatacenter.terms.bioportal.dao.BpProvisionalRelationDAO;
import org.metadatacenter.terms.bioportal.domainObjects.BpClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalRelation;
import org.metadatacenter.terms.bioportal.customObjects.BpSearchResults;

import java.io.IOException;
import java.util.List;

public class BioPortalService implements IBioPortalService
{
  private final int connectTimeout;
  private final int socketTimeout;

  private BpProvisionalClassDAO bpProvClassDAO;
  private BpProvisionalRelationDAO bpProvRelationDAO;
  private BpClassDAO bpClassDAO;

  /**
   * @param connectTimeout
   * @param socketTimeout
   */
  public BioPortalService(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
    this.bpProvClassDAO = new BpProvisionalClassDAO(connectTimeout, socketTimeout);
    this.bpProvRelationDAO = new BpProvisionalRelationDAO(connectTimeout, socketTimeout);
    this.bpClassDAO = new BpClassDAO(connectTimeout, socketTimeout);
  }

  /**
   * Provisional Classes
   **/

  public BpProvisionalClass createBpProvisionalClass(BpProvisionalClass c, String apiKey) throws IOException
  {
    return bpProvClassDAO.create(c, apiKey);
  }

  public BpProvisionalClass findBpProvisionalClassById(String id, String apiKey) throws IOException
  {
    return bpProvClassDAO.find(id, apiKey);
  }

  public List<BpProvisionalClass> findAllProvisionalClasses(String ontology, String apiKey) throws IOException
  {
    return bpProvClassDAO.findAll(ontology, apiKey);
  }

  public BpProvisionalRelation createBpProvisionalRelation(BpProvisionalRelation pr, String apiKey) throws IOException
  {
    return bpProvRelationDAO.create(pr, apiKey);
  }

  public BpProvisionalRelation findProvisionalRelationById(String id, String apiKey) throws IOException
  {
    return bpProvRelationDAO.find(id, apiKey);
  }

  public BpSearchResults<BpClass> findValueSetsByValueSetCollection(String vsCollection, String apiKey)
    throws IOException
  {
    return bpClassDAO.findValueSetsByValueSetCollection(vsCollection, apiKey);
  }

  public BpSearchResults<BpClass> findValuesByValueSet(String vsId, String vsCollection, String apiKey)
    throws IOException
  {
    return bpClassDAO.findValuesByValueSet(vsId, vsCollection, apiKey);
  }

}

