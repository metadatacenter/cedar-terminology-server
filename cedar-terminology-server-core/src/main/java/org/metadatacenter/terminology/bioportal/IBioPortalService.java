package org.metadatacenter.terminology.bioportal;
import org.metadatacenter.terminology.bioportal.domainObjects2.bioportal.BpProvisionalClass;

import java.io.IOException;

public interface IBioPortalService
{

  /** Provisional Classes **/
  BpProvisionalClass createBpProvisionalClass(BpProvisionalClass pc, String apiKey) throws IOException;

}
