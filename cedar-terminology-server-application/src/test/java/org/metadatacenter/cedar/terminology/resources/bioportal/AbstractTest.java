package org.metadatacenter.cedar.terminology.resources.bioportal;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.util.test.TestUtil;

import javax.ws.rs.client.Client;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public abstract class AbstractTest {

  protected static CedarConfig cedarConfig;
  protected static Client client;
  protected static String authHeader;
  protected static final String BASE_URL = "http://localhost";

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUpAbstract() {
    cedarConfig = CedarConfig.getInstance();
    authHeader = TestUtil.getTestUser1AuthHeader();

    client = new JerseyClientBuilder(RULE.getEnvironment()).build("BioPortal search endpoint client");
    client.property(ClientProperties.CONNECT_TIMEOUT, cedarConfig.getTerminologyConfig().getBioPortal().getConnectTimeout());
    client.property(ClientProperties.READ_TIMEOUT, cedarConfig.getTerminologyConfig().getBioPortal().getSocketTimeout());
  }

  @ClassRule
  public static final DropwizardAppRule<TerminologyServerConfiguration> RULE =
      new DropwizardAppRule<>(TerminologyServerApplication.class, ResourceHelpers
          .resourceFilePath("test-config.yml"));
}
