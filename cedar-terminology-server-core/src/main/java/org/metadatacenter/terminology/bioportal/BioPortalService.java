package org.metadatacenter.terminology.bioportal;

import org.metadatacenter.terminology.bioportal.dao.BpProvisionalClassDAO;
import org.metadatacenter.terminology.bioportal.domainObjects2.bioportal.BpProvisionalClass;

import java.io.IOException;

public class BioPortalService implements IBioPortalService
{
  private final int connectTimeout;
  private final int socketTimeout;

  private BpProvisionalClassDAO provClassDAO;
//  private RelationDAO relationDAO;
//  private ValueSetDAO valueSetDAO;
//  private ValueDAO valueDAO;

  /**
   * @param connectTimeout
   * @param socketTimeout
   */
  public BioPortalService(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
    this.provClassDAO = new BpProvisionalClassDAO(connectTimeout, socketTimeout);
//    relationDAO = new RelationDAO(connectTimeout, socketTimeout);
//    valueSetDAO = new ValueSetDAO(connectTimeout, socketTimeout);
//    valueDAO = new ValueDAO(connectTimeout, socketTimeout);
  }

  /** Provisional Classes **/

  public BpProvisionalClass createBpProvisionalClass(BpProvisionalClass c, String apiKey) throws IOException
  {
    return provClassDAO.create(c, apiKey);
  }


}

