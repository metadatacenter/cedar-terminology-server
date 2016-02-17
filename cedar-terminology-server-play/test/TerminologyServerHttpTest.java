import com.fasterxml.jackson.databind.JsonNode;
import org.junit.*;
import org.metadatacenter.terms.TerminologyService;
import play.Configuration;
import play.Play;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSResponse;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static play.mvc.Http.HeaderNames.SERVER;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class TerminologyServerHttpTest {

  private static final int TEST_SERVER_PORT = 3333;
  private static final String SERVER_URL = "http://localhost:" + TEST_SERVER_PORT;
  private static final String SERVER_URL_BIOPORTAL = SERVER_URL + "/bioportal/";
  private static final int TIMEOUT_MS = 10000;

  private static String bioportalApikey;

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUp() {
  }

  /**
   * (Called once after all the test methods in the class).
   */
  @AfterClass
  public static void oneTimeTearDown() {
  }

  /**
   * Sets up the test fixture.
   * (Called before every test case method.)
   */
  @Before
  public void setUp() {
//    templateElement1 = Json.newObject().
//        put("@id", "http://metadatacenter.org/template-elements/682c8141-9a61-4899-9d21-7083e861b0bf").
//        put("name", "template element 1 name").put("value", "template element 1 value");
//    templateElement2 = Json.newObject().
//        put("@id", "http://metadatacenter.org/template-elements/1dd58530-fdba-4c06-8d31-539b18296d8b").
//        put("name", "template element 2 name").put("value", "template element 2 value");
//
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        deleteAllTemplateElements();
//      }
//    });
  }

  /**
   * Tears down the test fixture.
   * (Called after every test case method.)
   */
  @After
  public void tearDown() {
//    running(testServer(TEST_SERVER_PORT), new Runnable() {
//      public void run() {
//        // Remove the elements created
//        deleteAllTemplateElements();
//      }
//    });
  }

  @Test
   public void searchAllTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        Configuration config = Play.application().configuration();
        String url = SERVER_URL_BIOPORTAL + "search";
        // Query parameters
        String q = "white blood cell";
        // Authorization header
        String authHeader = "apikey token=" + config.getString("bioportal.apikey.test");
        // Service invocation - Search all
        WSResponse wsResponse = WS.url(url).setHeader("Authorization", authHeader).setQueryParameter("q", q).get().get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(OK, wsResponse.getStatus());
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponse.getHeader("Content-Type"));
        // Check the number of results
        JsonNode jsonResponse = wsResponse.asJson();
        int pageCount = jsonResponse.get("pageCount").asInt();
        int lowLimitPageCount = 2000;
        Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", pageCount > lowLimitPageCount);
      }
    });
  }

  @Test
  public void searchClassesTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        Configuration config = Play.application().configuration();
        String url = SERVER_URL_BIOPORTAL + "search";
        // Query parameters
        String q = "white blood cell";
        String scope = "classes";
        // Authorization header
        String authHeader = "apikey token=" + config.getString("bioportal.apikey.test");
        // Service invocation - Search all
        WSResponse wsResponse = WS.url(url).setHeader("Authorization", authHeader).setQueryParameter("q", q).setQueryParameter("scope", scope).get().get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(OK, wsResponse.getStatus());
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponse.getHeader("Content-Type"));
        // Check the number of results
        JsonNode jsonResponse = wsResponse.asJson();
        int pageCount = jsonResponse.get("pageCount").asInt();
        int lowLimitPageCount = 2000;
        Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", pageCount > lowLimitPageCount);
      }
    });
  }

  @Test
  public void searchValueSetsTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        Configuration config = Play.application().configuration();
        String url = SERVER_URL_BIOPORTAL + "search";
        // Query parameters
        String q = "Amblyopia";
        String scope = "value_sets";
        // Authorization header
        String authHeader = "apikey token=" + config.getString("bioportal.apikey.test");
        // Service invocation - Search all
        WSResponse wsResponse = WS.url(url).setHeader("Authorization", authHeader).setQueryParameter("q", q).setQueryParameter("scope", scope).get().get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(OK, wsResponse.getStatus());
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponse.getHeader("Content-Type"));
        // Check the number of results
        JsonNode jsonResponse = wsResponse.asJson();
        int resultsCount = jsonResponse.get("collection").size();
        int lowLimitCount = 1;
        Assert.assertTrue("The number of value sets found for '" + q + "' is lower than expected", resultsCount > lowLimitCount);
      }
    });
  }

  @Test
  public void searchValuesTest() {
    running(testServer(TEST_SERVER_PORT), new Runnable() {
      public void run() {
        Configuration config = Play.application().configuration();
        String url = SERVER_URL_BIOPORTAL + "search";
        // Query parameters
        String q = "inclusion";
        String scope = "values";
        // Authorization header
        String authHeader = "apikey token=" + config.getString("bioportal.apikey.test");
        // Service invocation - Search all
        WSResponse wsResponse = WS.url(url).setHeader("Authorization", authHeader).setQueryParameter("q", q).setQueryParameter("scope", scope).get().get(TIMEOUT_MS);
        // Check HTTP response
        Assert.assertEquals(OK, wsResponse.getStatus());
        // Check Content-Type
        Assert.assertEquals("application/json; charset=utf-8", wsResponse.getHeader("Content-Type"));
        // Check the number of results
        JsonNode jsonResponse = wsResponse.asJson();
        int resultsCount = jsonResponse.get("collection").size();
        int lowLimitCount = 1;
        Assert.assertTrue("The number of values found for '" + q + "' is lower than expected", resultsCount > lowLimitCount);
      }
    });
  }
}




























