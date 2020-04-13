package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.terms.domainObjects.ValueSet;
import org.metadatacenter.terms.util.Util;

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
  private static ObjectNode valueSet1;
  private static int ontology1Size = 157000; // Approx size of NCIT (slightly lower than the actual size)
  private static int branch1Size = 240; // Approx size of branch (slightly lower than the actual size)
  private static int valueSet1Size = 89;

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
    branch1.put(VALUE_CONSTRAINTS_ACRONYM, "NCIT");

    valueSet1 = mapper.createObjectNode();
    valueSet1.put(VALUE_CONSTRAINTS_URI, "http://purl.bioontology.org/ontology/NLMVS/2.16.840.1.113762.1.4.1045.59"); // "Delivery Procedures" value set
    valueSet1.put(VALUE_CONSTRAINTS_VS_COLLECTION, "NLMVS");
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

  /**
   * Invalid input
   */

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

  /**
   * Search Classes in Ontologies
   */

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

  /**
   * Search Classes in Branches
   */

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
   * Search Values in Value Sets
   */

  @Test
  public void searchValuesNonProvisionalValueSetEmptyInputText() { // It should retrieve all the values in the value set
    String inputText = "";
    int pageSize = 200;
    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode().add(valueSet1), mapper.createArrayNode());
    requestBody.put(BP_PAGE_SIZE_PARAM, pageSize);

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    int numResultsFound = results.getCollection().size();
    Assert.assertTrue("The number of results found (" +  numResultsFound +
        ") is different from expected (" + valueSet1Size + ")" , numResultsFound == valueSet1Size);
  }

  @Test
  public void searchValuesNonProvisionalValueSet() { // Search for values that match the inputText in the value set
    String inputText = "barton";
    int expectedResultsCount = 2;
    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode().add(valueSet1), mapper.createArrayNode());

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    int numResultsFound = results.getCollection().size();
    Assert.assertTrue("The number of results found (" +  numResultsFound +
        ") is different from expected (" + valueSet1Size + ")" , numResultsFound == expectedResultsCount);
  }

  @Test
  public void searchValuesProvisionalValueSetEmptyInputText() { // It should retrieve all the values in the value set
    // Create provisional value set with two values
    ValueSet createdVs = createValueSet(vs1);
    Value createdValue1 = createValue(createdVs.getLdId(), value1);
    Value createdValue2 = createValue(createdVs.getLdId(), value2);
    int createdVsSize = 2;
    // Wait
    longWaitToEnsureBioPortalIndexUpdated();

    // Generate input body based on the created value set
    ObjectNode vs = mapper.createObjectNode();
    vs.put(VALUE_CONSTRAINTS_URI, createdVs.getLdId());
    vs.put(VALUE_CONSTRAINTS_VS_COLLECTION, Util.getShortIdentifier(createdVs.getVsCollection()));

    String inputText = "";
    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode().add(vs), mapper.createArrayNode());

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    int numResultsFound = results.getCollection().size();

    Assert.assertTrue("The number of results found (" +  numResultsFound +
        ") is different from expected (" + createdVsSize + ")" , numResultsFound == createdVsSize);
    // Check that the results are right
    for (SearchResult r : results.getCollection()) {
      Assert.assertTrue("Unexpected value" , r.getLdId().equals(createdValue1.getLdId()) ||
          r.getLdId().equals(createdValue2.getLdId()));
    }
  }

  @Test
  public void searchValuesProvisionalValueSet() { // Search for values in the value set that match the inputText
    // Create provisional value set with two values
    ValueSet createdVs = createValueSet(vs2);
    createValue(createdVs.getLdId(), value1);
    Value createdValue2 = createValue(createdVs.getLdId(), value2);
    // Wait
    longWaitToEnsureBioPortalIndexUpdated();

    // Generate input body based on the created value set
    ObjectNode vs = mapper.createObjectNode();
    vs.put(VALUE_CONSTRAINTS_URI, createdVs.getLdId());
    vs.put(VALUE_CONSTRAINTS_VS_COLLECTION, Util.getShortIdentifier(createdVs.getVsCollection()));

    String inputText = "Value2"; // Note that the label of the second value created is value2_test
    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode().add(vs), mapper.createArrayNode());

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    int numResultsFound = results.getCollection().size();
    Assert.assertTrue("The number of results found (" +  numResultsFound +
        ") is different from expected (" + 1 + ")" , numResultsFound == 1);
    // Check that the result found matches the expected result
    Assert.assertTrue("Unexpected value" ,
        results.getCollection().get(0).getLdId().equals(createdValue2.getLdId()));
  }

  /**
   * Search Enumerated Classes
   */

  // TODO. Also:
  // - Test generation of paginated results (probably as a unit test instead of integration tests)
  // - Test search based on different constraints
  // - Test class arrangements (still to be implemented)

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
