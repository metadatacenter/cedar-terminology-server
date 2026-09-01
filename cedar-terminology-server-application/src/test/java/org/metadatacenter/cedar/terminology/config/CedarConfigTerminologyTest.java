package org.metadatacenter.cedar.terminology.config;

import org.metadatacenter.config.BioPortal;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.test.AbstractCedarConfigTest;
import org.metadatacenter.config.CedarConfig;

public class CedarConfigTerminologyTest extends AbstractCedarConfigTest {

  @Override
  protected SystemComponent getSystemComponent() {
    return SystemComponent.SERVER_TERMINOLOGY;
  }

  /**
   * The BioPortal credentials are the terminology server's alone: no other component is granted
   * {@code CEDAR_BIOPORTAL_API_KEY} or {@code CEDAR_BIOPORTAL_REST_BASE}, and every other component
   * loads this same section with both placeholders intact.
   */
  @Override
  protected void assertServerSpecificConfig(CedarConfig config) {
    BioPortal bioPortal = config.getTerminologyConfig().getBioPortal();
    assertResolved("terminology.bioPortal.apiKey", bioPortal.getApiKey());
    assertResolved("terminology.bioPortal.basePath", bioPortal.getBasePath());
  }

}
