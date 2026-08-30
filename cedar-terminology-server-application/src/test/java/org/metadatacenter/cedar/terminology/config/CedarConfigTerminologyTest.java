package org.metadatacenter.cedar.terminology.config;

import org.junit.jupiter.api.Assertions;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.test.AbstractCedarConfigTest;

public class CedarConfigTerminologyTest extends AbstractCedarConfigTest {

  @Override
  protected SystemComponent getSystemComponent() {
    return SystemComponent.SERVER_TERMINOLOGY;
  }

  @Override
  protected void assertServerSpecificConfig(CedarConfig config) {
    Assertions.assertNotNull(config.getTerminologyConfig(),
        "the terminology server loaded no terminology configuration");
    Assertions.assertNotNull(config.getTerminologyConfig().getBioPortal(),
        "the terminology server loaded no BioPortal configuration");
  }

}
