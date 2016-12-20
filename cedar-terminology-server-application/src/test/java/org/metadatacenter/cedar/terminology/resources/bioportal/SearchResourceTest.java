package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.util.test.TestUtil;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class SearchResourceTest {

  private static CedarConfig cedarConfig;
  private static Client client;
  private static String authHeader;
  private static final String BASE_URL = "http://localhost";
  private static final String BP_SEARCH = "bioportal/search";
  private static String baseUrlSearch;
  private static final String jsonContentType = "application/json";
  private static final String contentTypeHeader = "Content-Type";

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUp() {
    cedarConfig = CedarConfig.getInstance();
    authHeader = TestUtil.getTestUser1AuthHeader();
    baseUrlSearch = BASE_URL + ":" + RULE.getLocalPort() + "/" + BP_SEARCH;

    client = new JerseyClientBuilder(RULE.getEnvironment()).build("BioPortal search endpoint client");
    client.property(ClientProperties.CONNECT_TIMEOUT, cedarConfig.getTerminologyConfig().getBioPortal()
        .getConnectTimeout());
    client.property(ClientProperties.READ_TIMEOUT, cedarConfig.getTerminologyConfig().getBioPortal().getSocketTimeout
        ());
  }

  @ClassRule
  public static final DropwizardAppRule<TerminologyServerConfiguration> RULE =
      new DropwizardAppRule<>(TerminologyServerApplication.class, ResourceHelpers
          .resourceFilePath("test-config.yml"));

  @Test
  public void searchAllTest() {
    // Query parameters
    String q = "white blood cell";
    // Service invocation - Search all
    Response response = client.target(baseUrlSearch).queryParam("q", q).request(MediaType.APPLICATION_JSON_TYPE)
        .header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(jsonContentType, response.getHeaderString(contentTypeHeader));
    // Check the number of results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    int pageCount = jsonResponse.get("pageCount").asInt();
    int lowLimitPageCount = 2000;
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", pageCount >
        lowLimitPageCount);
  }

  @Test
  public void searchClassesTest() {
    // Query parameters
    String q = "white blood cell";
    String scope = "classes";
    // Service invocation - Search classes
    Response response = client.target(baseUrlSearch).queryParam("q", q).queryParam("scope", scope).request(MediaType
        .APPLICATION_JSON_TYPE)
        .header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(jsonContentType, response.getHeaderString(contentTypeHeader));
    // Check the number of results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    int pageCount = jsonResponse.get("pageCount").asInt();
    int lowLimitPageCount = 2000;
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", pageCount >
        lowLimitPageCount);
  }

  @Test
  public void searchClassesBySourceTest() {
    // Query parameters
    String q = "cell";
    String scope = "classes";
    String source = "OBI";
    // Service invocation - Search classes
    Response response = client.target(baseUrlSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .queryParam("sources", source)
        .request(MediaType.APPLICATION_JSON_TYPE).header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(jsonContentType, response.getHeaderString(contentTypeHeader));
    // Check that there are some results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    JsonNode results = jsonResponse.get("collection");
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", results.size() > 1);
    // Check that the retrieved classes are from the right source
    for (JsonNode r : results) {
      String resultSource = r.get("source").asText();
      String shortResultSource = resultSource.substring(resultSource.lastIndexOf("/") + 1);
      Assert.assertTrue("Class source does not match the expected source",
          source.compareTo(shortResultSource) == 0);
    }
  }


}

























