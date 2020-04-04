package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.SearchResult;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static org.metadatacenter.constant.HttpConstants.BAD_REQUEST;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;
import static org.metadatacenter.model.ModelNodeNames.*;
import static org.metadatacenter.cedar.terminology.util.Constants.*;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class IntegratedSearchResourceTest extends AbstractTerminologyServerResourceTest {



  /* Objects used by the test methods */
  private static ObjectNode branch1;
  private static ObjectNode ontology1;
  private static int ontology1Size = 157000; // Approx size of NCIT (slightly lower than the actual size)
  private static int branch1Size = 240; // Approx size of branch (slightly lower than the actual size)

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUp() {
    ontology1 = mapper.createObjectNode();
    ontology1.put(VALUE_CONSTRAINTS_ACRONYM, "NCIT");

    branch1 = mapper.createObjectNode();
    branch1.put(VALUE_CONSTRAINTS_URI, "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C14283"); // "Virus" branch
    branch1.put(VALUE_CONSTRAINTS_ACRONYM, "NCIT"); // "Virus" branch
  }

  /**
   * Sets up the test fixture.
   * (Called before every test case method.)
   */
  @Before
  public void setUp() {
  }

  /**
   * Tears down the test fixture.
   * (Called after every test case method.)
   */
  @After
  public void tearDown() {
  }

  /* Tests */

  @Test
  public void searchEmptyBody() {
    ObjectNode emptyBody = mapper.createObjectNode();
    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(emptyBody));
    // Check HTTP response
    Assert.assertEquals(STATUS_CODE_UNPROCESSABLE_ENTITY, response.getStatus());
  }

  @Test
  public void searchEmptyParamObject() {
    ObjectNode requestBody = mapper.createObjectNode();
    requestBody.set(BP_INTEGRATED_SEARCH_PARAMS_FIELD, mapper.createObjectNode());
    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(STATUS_CODE_UNPROCESSABLE_ENTITY, response.getStatus());
  }

  @Test
  public void searchEmptyConstraints() {
    ObjectNode requestBody = mapper.createObjectNode();
    ObjectNode parameterObject = mapper.createObjectNode();
    parameterObject.set(BP_INTEGRATED_SEARCH_PARAM_VALUE_CONSTRAINTS, mapper.createObjectNode());
    requestBody.set(BP_INTEGRATED_SEARCH_PARAMS_FIELD, parameterObject);
    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(STATUS_CODE_UNPROCESSABLE_ENTITY, response.getStatus());
  }

  @Test
  public void searchInvalidField() {
    ObjectNode requestBody = mapper.createObjectNode();
    requestBody.put("invalidField", "invalidFieldValue");
    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(BAD_REQUEST, response.getStatus());
  }

  @Test
  public void searchClassesInOntologyEmptyInputText() { // It should retrieve all the ontology classes
    ObjectNode requestBody = generateRequestBody("", mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode());
    requestBody.put(BP_PAGE_SIZE_PARAM, 1); // Minimum page size allowed to speed up test execution

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    int numResultsFound = results.getPageSize() * results.getPageCount();
    Assert.assertTrue("The number of results found (" +  numResultsFound +
        ") is lower than expected (" + ontology1Size + ")" , numResultsFound >= ontology1Size);
  }

  @Test
  public void searchClassesInOntologyMissingInputText() { // It should retrieve all the ontology classes
    ObjectNode requestBody = generateRequestBody(null, mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode());
    requestBody.put(BP_PAGE_SIZE_PARAM, 1); // Minimum page size allowed to speed up test execution

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    int numResultsFound = results.getPageSize() * results.getPageCount();
    Assert.assertTrue("The number of results found (" +  numResultsFound +
        ") is lower than expected (" + ontology1Size + ")" , numResultsFound >= ontology1Size);
    Assert.assertTrue("The number of results found (" +  numResultsFound +
        ") is higher than expected (" + ontology1Size + ")" , numResultsFound < 2 * ontology1Size);
  }

  @Test
  public void searchClassesInOntologyWrongLabel() { // Search for ontology classes in a given branch
    String inputText = "aaabbbcccddd"; // Non-existing class label
    int pageSize = 1;
    int expectedNumberOfResults = 0;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode());
    requestBody.put(BP_PAGE_SIZE_PARAM, pageSize);

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {
    });
    int numResultsFound = results.getPageSize() * results.getPageCount();
    Assert.assertTrue("The number of search results for '" + inputText + " (" + numResultsFound + ") " +
            "' is different from expected" + " (" + expectedNumberOfResults + ") ",
        numResultsFound == expectedNumberOfResults);
  }

  @Test
  public void searchClassesInOntology() { // Search for ontology classes in a given ontology
    String inputText = "virus"; // Class: http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C14283"
    int pageSize = 20;
    int minNumberOfResults = 100;
    int maxNumberOfResults = 10000;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode());
    requestBody.put(BP_PAGE_SIZE_PARAM, pageSize);

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {
    });
    int numResultsFound = results.getPageSize() * results.getPageCount();
    Assert.assertTrue("The number of search results for '" + inputText + " (" + numResultsFound + ") " +
        "' is lower than expected" + " (" + minNumberOfResults + ") ", numResultsFound > minNumberOfResults);
    Assert.assertTrue("The number of search results for '" + inputText + " (" + numResultsFound + ") " +
        "' is higher than expected" + " (" + minNumberOfResults + ") ", numResultsFound < maxNumberOfResults);
    // Check that the first result is right
    Assert.assertTrue("Unexpected result: ",
        results.getCollection().get(0).getPrefLabel().toLowerCase().contains(inputText.toLowerCase()));
    // Check that the retrieved classes are from the right source. We limit this check to the first page or results to speed up the tests
    for (SearchResult r : results.getCollection()) {
      String resultSourceAcronym = r.getSource().substring(r.getSource().lastIndexOf("/") + 1);
      Assert.assertTrue("Class source does not match the expected source",
          resultSourceAcronym.equals(ontology1.get(VALUE_CONSTRAINTS_ACRONYM).asText()));
    }
  }

  @Test
  public void searchClassesInBranchEmptyInputText() { // It should retrieve all the classes in the branch
    String inputText = "";
    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode());

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    int numResultsFound = results.getPageSize() * results.getPageCount();
    Assert.assertTrue("The number of results found (" +  numResultsFound +
        ") is lower than expected (" + branch1Size + ")" , numResultsFound >= branch1Size);
    Assert.assertTrue("The number of results found (" +  numResultsFound +
        ") is higher than expected (" + branch1Size + ")" , numResultsFound < 10 * branch1Size);
  }

  @Test
  public void searchClassesInBranchMissingInputText() {  // It should retrieve all the classes in the branch
    ObjectNode requestBody = generateRequestBody(null, mapper.createArrayNode(),
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode());
    requestBody.put(BP_PAGE_SIZE_PARAM, 1); // Minimum page size allowed to speed up test execution

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    int numResultsFound = results.getPageSize() * results.getPageCount();
    Assert.assertTrue("The number of results found (" +  numResultsFound +
        ") is lower than expected (" + branch1Size + ")" , numResultsFound >= branch1Size);
    Assert.assertTrue("The number of results found (" +  numResultsFound +
        ") is higher than expected (" + branch1Size + ")" , numResultsFound < 10 * branch1Size);
  }

  @Test
  public void searchClassesInBranch() { // Search for ontology classes in a given branch
    String inputText = "coronavirus"; // http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C14283"
    int pageSize = 10;
    int minNumberOfResults = 5;
    int maxNumberOfResults = 100;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode());
    requestBody.put(BP_PAGE_SIZE_PARAM, pageSize);

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {
    });
    int numResultsFound = results.getPageSize() * results.getPageCount();
    Assert.assertTrue("The number of search results for '" + inputText + " (" + numResultsFound + ") " +
        "' is lower than expected" + " (" + minNumberOfResults + ") ", numResultsFound >= minNumberOfResults);
    Assert.assertTrue("The number of search results for '" + inputText + " (" + numResultsFound + ") " +
        "' is higher than expected" + " (" + minNumberOfResults + ") ", numResultsFound < maxNumberOfResults);
    // Check that the first result is right
    Assert.assertTrue("Unexpected result: ",
        results.getCollection().get(0).getPrefLabel().toLowerCase().contains(inputText.toLowerCase()));
    // Check that the retrieved classes are from the right source. We limit this check to the first page or results to speed up the tests
    for (SearchResult r : results.getCollection()) {
      String resultSourceAcronym = r.getSource().substring(r.getSource().lastIndexOf("/") + 1);
      Assert.assertTrue("Class source does not match the expected source",
          resultSourceAcronym.equals(ontology1.get(VALUE_CONSTRAINTS_ACRONYM).asText()));
    }
  }

  @Test
  public void searchClassesInBranchWrongLabel() { // Search for ontology classes in a given branch
    String inputText = "aaabbbcccddd"; // Non-existing class label
    int pageSize = 1;
    int expectedNumberOfResults = 0;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode());
    requestBody.put(BP_PAGE_SIZE_PARAM, pageSize);

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {
    });
    int numResultsFound = results.getPageSize() * results.getPageCount();
    Assert.assertTrue("The number of search results for '" + inputText + " (" + numResultsFound + ") " +
        "' is different from expected" + " (" + expectedNumberOfResults + ") ",
        numResultsFound == expectedNumberOfResults);
  }

  /**
   *  Utility methods
   * */
  private static ObjectNode generateRequestBody(String inputText, ArrayNode ontologies, ArrayNode branches,
                                                ArrayNode valueSets, ArrayNode classes) {
    // valueConstraints object
    ObjectNode valueConstraints = mapper.createObjectNode();
    valueConstraints.set(VALUE_CONSTRAINTS_ONTOLOGIES, ontologies);
    valueConstraints.set(VALUE_CONSTRAINTS_BRANCHES, branches);
    valueConstraints.set(VALUE_CONSTRAINTS_VALUE_SETS, valueSets);
    valueConstraints.set(VALUE_CONSTRAINTS_CLASSES, classes);
    // parameterObject object
    ObjectNode parameterObject = mapper.createObjectNode();
    parameterObject.set(BP_INTEGRATED_SEARCH_PARAM_VALUE_CONSTRAINTS, valueConstraints);
    if (inputText != null) {
      parameterObject.put(BP_INTEGRATED_SEARCH_PARAM_INPUT_TEXT, inputText);
    }
    // Request body
    ObjectNode requestBody = mapper.createObjectNode();
    requestBody.set(BP_INTEGRATED_SEARCH_PARAMS_FIELD, parameterObject);
    return requestBody;
  }

}
