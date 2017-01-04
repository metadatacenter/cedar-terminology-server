package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class SearchResourceTest extends AbstractTest {

  private static final String BP_SEARCH = "bioportal/search";
  private static String baseUrlSearch;

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUp() {
    baseUrlSearch = BASE_URL + ":" + RULE.getLocalPort() + "/" + BP_SEARCH;
  }

  @Test
  public void searchAllTest() {
    // Query parameters
    String q = "white blood cell";
    // Service invocation
    Response response = client.target(baseUrlSearch).queryParam("q", q).request()
        .header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
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
    // Service invocation
    Response response = client.target(baseUrlSearch).queryParam("q", q).queryParam("scope", scope).request()
        .header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
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
    // Service invocation
    Response response = client.target(baseUrlSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .queryParam("sources", source)
        .request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
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

  @Test
  public void searchClassesByWrongSourceTest() {
    // Query parameters
    String q = "cell";
    String scope = "classes";
    String source = "WRONG-SOURCE";
    // Service invocation
    Response response = client.target(baseUrlSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .queryParam("sources", source)
        .request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  public void searchValueSetsTest() {
    // Query parameters
    String q = "Amblyopia";
    String scope = "value_sets";
    // Service invocation
    Response response = client.target(baseUrlSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that there are some results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    JsonNode results = jsonResponse.get("collection");
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", results.size() > 1);
  }

  @Test
  public void searchValuesTest() {
    // Query parameters
    String q = "inclusion";
    String scope = "values";
    // Service invocation
    Response response = client.target(baseUrlSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .request().header("Authorization", authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that there are some results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    JsonNode results = jsonResponse.get("collection");
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", results.size() > 0);
  }

}
