package org.metadatacenter.cedar.terminology.resources.bioportal;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.metadatacenter.cedar.terminology.util.Constants.*;
import static org.metadatacenter.constant.HttpConstants.BAD_REQUEST;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;
import static org.metadatacenter.model.ModelNodeNames.*;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class IntegratedSearchResourceTest extends AbstractTerminologyServerResourceTest {


  /* Objects used by the test methods */
  // Enumerated classes
  private static ObjectNode enumeratedClass1;
  private static String enumeratedClass1Uri = "http://purl.obolibrary.org/obo/DOID_0050134";
  private static String enumeratedClass1PrefLabel = "cutaneous mycosis";
  private static String enumeratedClass1Type = "OntologyClass";
  private static String enumeratedClass1Label = "cutaneous mycosis";
  private static String enumeratedClass1Source = "DOID";

  private static ObjectNode enumeratedClass2;
  private static String enumeratedClass2Uri = "http://purl.obolibrary.org/obo/DOID_0080014";
  private static String enumeratedClass2PrefLabel = "chromosomal disease";
  private static String enumeratedClass2Type = "OntologyClass";
  private static String enumeratedClass2Label = "chromosomal disease";
  private static String enumeratedClass2Source = "DOID";

  private static ObjectNode enumeratedClass3;
  private static String enumeratedClass3Uri = "http://purl.obolibrary.org/obo/DOID_0060072";
  private static String enumeratedClass3PrefLabel = "benign neoplasm";
  private static String enumeratedClass3Type = "OntologyClass";
  private static String enumeratedClass3Label = "benign neoplasm";
  private static String enumeratedClass3Source = "DOID";
  // Ontologies
  private static ObjectNode ontology1;
  private static int ontology1Size = 157000; // Approx size of NCIT (slightly lower than the actual size) 
  // Branches
  private static ObjectNode branch1;
  private static int branch1Size = 240; // Approx size of branch (slightly lower than the actual size)
  // Value sets
  private static ObjectNode valueSet1;
  private static int valueSet1Size = 89;


  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUp() {
    enumeratedClass1 = mapper.createObjectNode();
    enumeratedClass1.put(VALUE_CONSTRAINTS_URI, enumeratedClass1Uri);
    enumeratedClass1.put(VALUE_CONSTRAINTS_PREFLABEL, enumeratedClass1PrefLabel);
    enumeratedClass1.put(VALUE_CONSTRAINTS_TYPE, enumeratedClass1Type);
    enumeratedClass1.put(VALUE_CONSTRAINTS_LABEL, enumeratedClass1Label);
    enumeratedClass1.put(VALUE_CONSTRAINTS_SOURCE, enumeratedClass1Source);

    enumeratedClass2 = mapper.createObjectNode();
    enumeratedClass2.put(VALUE_CONSTRAINTS_URI, enumeratedClass2Uri);
    enumeratedClass2.put(VALUE_CONSTRAINTS_PREFLABEL, enumeratedClass2PrefLabel);
    enumeratedClass2.put(VALUE_CONSTRAINTS_TYPE, enumeratedClass2Type);
    enumeratedClass2.put(VALUE_CONSTRAINTS_LABEL, enumeratedClass2Label);
    enumeratedClass2.put(VALUE_CONSTRAINTS_SOURCE, enumeratedClass2Source);

    enumeratedClass3 = mapper.createObjectNode();
    enumeratedClass3.put(VALUE_CONSTRAINTS_URI, enumeratedClass3Uri);
    enumeratedClass3.put(VALUE_CONSTRAINTS_PREFLABEL, enumeratedClass3PrefLabel);
    enumeratedClass3.put(VALUE_CONSTRAINTS_TYPE, enumeratedClass3Type);
    enumeratedClass3.put(VALUE_CONSTRAINTS_LABEL, enumeratedClass3Label);
    enumeratedClass3.put(VALUE_CONSTRAINTS_SOURCE, enumeratedClass3Source);

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
    int pageSize = 1; // Minimum page size allowed to speed up test execution
    ObjectNode requestBody = generateRequestBody("", mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(), Optional.of(pageSize));

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
    Assert.assertTrue("The number of results found (" + numResultsFound +
        ") is lower than expected (" + ontology1Size + ")", numResultsFound >= ontology1Size);
  }

  @Test
  public void searchClassesInOntologyMissingInputText() { // It should retrieve all the ontology classes
    int pageSize = 1; // Minimum page size allowed to speed up test execution
    ObjectNode requestBody = generateRequestBody(null, mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(), Optional.of(pageSize));

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
    Assert.assertTrue("The number of results found (" + numResultsFound +
        ") is lower than expected (" + ontology1Size + ")", numResultsFound >= ontology1Size);
    Assert.assertTrue("The number of results found (" + numResultsFound +
        ") is higher than expected (" + ontology1Size + ")", numResultsFound < 2 * ontology1Size);
  }

  @Test
  public void searchClassesInOntologyWrongLabel() { // Search for ontology classes in a given branch
    String inputText = "aaabbbcccddd"; // Non-existing class label
    int pageSize = 1;
    int expectedNumberOfResults = 0;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(), Optional.of(pageSize));

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
    Assert.assertTrue("The number of search results for '" + inputText + "' (" + numResultsFound + ") " +
            "is different from expected" + " (" + expectedNumberOfResults + ") ",
        numResultsFound == expectedNumberOfResults);
  }

  @Test
  public void searchClassesInOntology() { // Search for ontology classes in a given ontology
    String inputText = "virus"; // Class: http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C14283"
    int pageSize = 20;
    int minNumberOfResults = 100;
    int maxNumberOfResults = 10000;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(), Optional.of(pageSize));

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
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(), Optional.empty());

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
    Assert.assertTrue("The number of results found (" + numResultsFound +
        ") is lower than expected (" + branch1Size + ")", numResultsFound >= branch1Size);
    Assert.assertTrue("The number of results found (" + numResultsFound +
        ") is higher than expected (" + branch1Size + ")", numResultsFound < 10 * branch1Size);
  }

  @Test
  public void searchClassesInBranchMissingInputText() {  // It should retrieve all the classes in the branch
    int pageSize = 1; // Minimum page size allowed to speed up test execution
    ObjectNode requestBody = generateRequestBody(null, mapper.createArrayNode(),
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(), Optional.of(pageSize));
    requestBody.put(BP_PAGE_SIZE_PARAM, 1); // Minimum page size allowed to speed up test execution

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
    Assert.assertTrue("The number of results found (" + numResultsFound +
        ") is lower than expected (" + branch1Size + ")", numResultsFound >= branch1Size);
    Assert.assertTrue("The number of results found (" + numResultsFound +
        ") is higher than expected (" + branch1Size + ")", numResultsFound < 10 * branch1Size);
  }

  @Test
  public void searchClassesInBranch() { // Search for ontology classes in a given branch
    String inputText = "coronavirus"; // http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C14283"
    int pageSize = 10;
    int minNumberOfResults = 5;
    int maxNumberOfResults = 100;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(), Optional.of(pageSize));
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
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(), Optional.of(pageSize));
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
    Assert.assertTrue("The number of search results for '" + inputText + "' (" + numResultsFound + ") " +
            "is different from expected" + " (" + expectedNumberOfResults + ") ",
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
        mapper.createArrayNode(), mapper.createArrayNode().add(valueSet1), mapper.createArrayNode(), Optional.empty(), Optional.of(pageSize));
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
    int numResultsFound = results.getCollection().size();
    Assert.assertTrue("The number of results found (" + numResultsFound +
        ") is different from expected (" + valueSet1Size + ")", numResultsFound == valueSet1Size);
  }

  @Test
  public void searchValuesNonProvisionalValueSet() { // Search for values that match the inputText in the value set
    String inputText = "barton";
    int expectedResultsCount = 2;
    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode().add(valueSet1), mapper.createArrayNode(), Optional.empty(), Optional.empty());

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
    int numResultsFound = results.getCollection().size();
    Assert.assertTrue("The number of results found (" + numResultsFound +
        ") is different from expected (" + valueSet1Size + ")", numResultsFound == expectedResultsCount);
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
        mapper.createArrayNode(), mapper.createArrayNode().add(vs), mapper.createArrayNode(),
        Optional.empty(), Optional.empty());

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
    int numResultsFound = results.getCollection().size();

    Assert.assertTrue("The number of results found (" + numResultsFound +
        ") is different from expected (" + createdVsSize + ")", numResultsFound == createdVsSize);
    // Check that the results are right
    for (SearchResult r : results.getCollection()) {
      Assert.assertTrue("Unexpected value", r.getLdId().equals(createdValue1.getLdId()) ||
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
        mapper.createArrayNode(), mapper.createArrayNode().add(vs), mapper.createArrayNode(), Optional.empty(), Optional.empty());

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
    int numResultsFound = results.getCollection().size();
    Assert.assertTrue("The number of results found (" + numResultsFound +
        ") is different from expected (" + 1 + ")", numResultsFound == 1);
    // Check that the result found matches the expected result
    Assert.assertTrue("Unexpected value",
        results.getCollection().get(0).getLdId().equals(createdValue2.getLdId()));
  }

  /**
   * Search Enumerated Classes
   */

  @Test
  public void searchEnumeratedClassesEmptySearch() {
    String inputText = "";
    int pageSize = 10;
    ArrayNode enumeratedClasses = mapper.createArrayNode().add(enumeratedClass1).add(enumeratedClass2).add(enumeratedClass3);

    // Add 3 enumerated classes
    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode(),
        enumeratedClasses, Optional.empty(), Optional.of(pageSize));

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
    int numResultsFound = results.getTotalCount();
    Assert.assertTrue("The number of search results for '" + inputText + "' (" + numResultsFound + ") " +
            "is different from expected" + " (" + enumeratedClasses.size() + ") ",
        numResultsFound == enumeratedClasses.size());
    // Check that the results are right
    for (int i = 0; i < enumeratedClasses.size(); i++) {
      String ldId = enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_URI).asText();
      String id = Util.getShortIdentifier(ldId);
      String type = enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_TYPE).asText();
      String ldType = BP_TYPE_BASE + type;
      String prefLabel = enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_PREFLABEL).asText();
      String source = BP_API_BASE + BP_ONTOLOGIES + enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_SOURCE).asText();

      Assert.assertTrue("Wrong ldId", results.getCollection().get(i).getLdId().equals(ldId));
      Assert.assertTrue("Wrong id", results.getCollection().get(i).getId().equals(id));
      Assert.assertTrue("Wrong type", results.getCollection().get(i).getType().equals(type));
      Assert.assertTrue("Wrong ldType", results.getCollection().get(i).getLdType().equals(ldType));
      Assert.assertTrue("Wrong prefLabel", results.getCollection().get(i).getPrefLabel().equals(prefLabel));
      Assert.assertTrue("Wrong source", results.getCollection().get(i).getSource().equals(source));
      Assert.assertTrue("Wrong definition", results.getCollection().get(i).getDefinition() == null);
    }
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 1);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == 3);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() == 3);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == 0);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == 0);
  }

  @Test
  public void searchEnumeratedClassesEmptySearchTwoPages() {
    String inputText = "";
    int pageSize = 2;
    ArrayNode enumeratedClasses = mapper.createArrayNode().add(enumeratedClass1).add(enumeratedClass2).add(enumeratedClass3);

    // 3 enumerated classes. Page 1
    ObjectNode requestBodyPage1 = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode(),
        enumeratedClasses, Optional.of(1), Optional.of(pageSize));
    ObjectNode requestBodyPage2 = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode(),
        enumeratedClasses, Optional.of(2), Optional.of(pageSize));

    // Service invocation
    Response responsePage1 = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBodyPage1));
    Response responsePage2 = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBodyPage2));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), responsePage1.getStatus());
    Assert.assertEquals(Status.OK.getStatusCode(), responsePage2.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, responsePage1.getHeaderString(HttpHeaders.CONTENT_TYPE));
    Assert.assertEquals(MediaType.APPLICATION_JSON, responsePage2.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> resultsPage1 = responsePage1.readEntity(new GenericType<PagedResults<SearchResult>>() {
    });
    PagedResults<SearchResult> resultsPage2 = responsePage2.readEntity(new GenericType<PagedResults<SearchResult>>() {
    });
    Assert.assertTrue("The number of search results for '" + inputText + "' (" + resultsPage1.getCollection().size() + ") " +
        "is different from expected" + " (" + 2 + ") ", resultsPage1.getCollection().size() == 2);
    Assert.assertTrue("The number of search results for '" + inputText + "' (" + resultsPage2.getCollection().size() + ") " +
        "is different from expected" + " (" + 1 + ") ", resultsPage2.getCollection().size() == 1);

    List<SearchResult> results = new ArrayList<>();
    results.add(resultsPage1.getCollection().get(0));
    results.add(resultsPage1.getCollection().get(1));
    results.add(resultsPage2.getCollection().get(0));

    // Check that the results are right
    for (int i = 0; i < enumeratedClasses.size(); i++) {
      String ldId = enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_URI).asText();
      String id = Util.getShortIdentifier(ldId);
      String type = enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_TYPE).asText();
      String ldType = BP_TYPE_BASE + type;
      String prefLabel = enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_PREFLABEL).asText();
      String source = BP_API_BASE + BP_ONTOLOGIES + enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_SOURCE).asText();

      Assert.assertTrue("Wrong ldId", results.get(i).getLdId().equals(ldId));
      Assert.assertTrue("Wrong id", results.get(i).getId().equals(id));
      Assert.assertTrue("Wrong type", results.get(i).getType().equals(type));
      Assert.assertTrue("Wrong ldType", results.get(i).getLdType().equals(ldType));
      Assert.assertTrue("Wrong prefLabel", results.get(i).getPrefLabel().equals(prefLabel));
      Assert.assertTrue("Wrong source", results.get(i).getSource().equals(source));
      Assert.assertTrue("Wrong definition", results.get(i).getDefinition() == null);
    }
    // Check pagination information (page 1)
    Assert.assertTrue("Wrong page", resultsPage1.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", resultsPage1.getPageCount() == 2);
    Assert.assertTrue("Wrong pageSize", resultsPage1.getPageSize() == 2);
    Assert.assertTrue("Wrong totalCount", resultsPage1.getTotalCount() == 3);
    Assert.assertTrue("Wrong prevPage", resultsPage1.getPrevPage() == 0);
    Assert.assertTrue("Wrong prevPage", resultsPage1.getNextPage() == 2);

    // Check pagination information (page 2)
    Assert.assertTrue("Wrong page", resultsPage2.getPage() == 2);
    Assert.assertTrue("Wrong pageCount", resultsPage2.getPageCount() == 2);
    Assert.assertTrue("Wrong pageSize", resultsPage2.getPageSize() == 1);
    Assert.assertTrue("Wrong totalCount", resultsPage2.getTotalCount() == 3);
    Assert.assertTrue("Wrong prevPage", resultsPage2.getPrevPage() == 1);
    Assert.assertTrue("Wrong prevPage", resultsPage2.getNextPage() == 0);
  }

  @Test
  public void searchEnumeratedClasses() {
    String inputText = "Mycosis";
    int pageSize = 10;
    int numberOfExpectedResults = 1;
    ArrayNode enumeratedClasses = mapper.createArrayNode().add(enumeratedClass1).add(enumeratedClass2).add(enumeratedClass3);

    // Add 3 enumerated classes
    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode(),
        enumeratedClasses, Optional.empty(), Optional.of(pageSize));

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
    int numResultsFound = results.getTotalCount();
    Assert.assertTrue("The number of search results for '" + inputText + "' (" + numResultsFound + ") " +
            "is different from expected" + " (" + numberOfExpectedResults + ") ",
        numResultsFound == numberOfExpectedResults);
    // Check that the result is right

    String ldId = enumeratedClass1.get(VALUE_CONSTRAINTS_URI).asText();
    String id = Util.getShortIdentifier(ldId);
    String type = enumeratedClass1.get(VALUE_CONSTRAINTS_TYPE).asText();
    String ldType = BP_TYPE_BASE + type;
    String prefLabel = enumeratedClass1.get(VALUE_CONSTRAINTS_PREFLABEL).asText();
    String source = BP_API_BASE + BP_ONTOLOGIES + enumeratedClass1.get(VALUE_CONSTRAINTS_SOURCE).asText();

    Assert.assertTrue("Wrong ldId", results.getCollection().get(0).getLdId().equals(ldId));
    Assert.assertTrue("Wrong id", results.getCollection().get(0).getId().equals(id));
    Assert.assertTrue("Wrong type", results.getCollection().get(0).getType().equals(type));
    Assert.assertTrue("Wrong ldType", results.getCollection().get(0).getLdType().equals(ldType));
    Assert.assertTrue("Wrong prefLabel", results.getCollection().get(0).getPrefLabel().equals(prefLabel));
    Assert.assertTrue("Wrong source", results.getCollection().get(0).getSource().equals(source));
    Assert.assertTrue("Wrong definition", results.getCollection().get(0).getDefinition() == null);

    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 1);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == 1);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() == 1);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == 0);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == 0);
  }

  /**
   * Search based on multiple constraints
   */

  @Test
  public void searchEnumeratedClassesAndOntologyEmptySearch() {
    String inputText = "";
    int pageSize = 10;
    ArrayNode enumeratedClasses = mapper.createArrayNode().add(enumeratedClass1).add(enumeratedClass2).add(enumeratedClass3);
    ArrayNode ontologies = mapper.createArrayNode().add(ontology1);

    // 3 enumerated classes and 1 ontology
    ObjectNode requestBody = generateRequestBody(inputText, ontologies,
        mapper.createArrayNode(), mapper.createArrayNode(),
        enumeratedClasses, Optional.empty(), Optional.of(pageSize));

    // Initial request to get some results (20) from the ontology
    ObjectNode ontSizeRequestBody = generateRequestBody(inputText, ontologies,
        mapper.createArrayNode(), mapper.createArrayNode(),
        mapper.createArrayNode(), Optional.of(1), Optional.of(20));
    Response ontSizeResponse = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(ontSizeRequestBody));
    List<SearchResult> topOntologyClasses = ontSizeResponse.readEntity(new GenericType<PagedResults<SearchResult>>() {
    }).getCollection();

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {});

    Assert.assertTrue("Wrong page size", pageSize == results.getPageSize());
    Assert.assertTrue("Wrong page size", pageSize == results.getCollection().size());

    // Check that the results are right (enumerated classes) (enumerated classes will be returned at the top)
    for (int i = 0; i < enumeratedClasses.size(); i++) {
      String ldId = enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_URI).asText();
      String id = Util.getShortIdentifier(ldId);
      String type = enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_TYPE).asText();
      String ldType = BP_TYPE_BASE + type;
      String prefLabel = enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_PREFLABEL).asText();
      String source = BP_API_BASE + BP_ONTOLOGIES + enumeratedClasses.get(i).get(VALUE_CONSTRAINTS_SOURCE).asText();

      Assert.assertTrue("Wrong ldId", results.getCollection().get(i).getLdId().equals(ldId));
      Assert.assertTrue("Wrong id", results.getCollection().get(i).getId().equals(id));
      Assert.assertTrue("Wrong type", results.getCollection().get(i).getType().equals(type));
      Assert.assertTrue("Wrong ldType", results.getCollection().get(i).getLdType().equals(ldType));
      Assert.assertTrue("Wrong prefLabel", results.getCollection().get(i).getPrefLabel().equals(prefLabel));
      Assert.assertTrue("Wrong source", results.getCollection().get(i).getSource().equals(source));
      Assert.assertTrue("Wrong definition", results.getCollection().get(i).getDefinition() == null);
    }
    // Check that the results are right (ontology classes) (enumerated classes will be returned at the top)
    int ontologyClassesIndex = 0;
    for (int i = enumeratedClasses.size(); i < pageSize; i++) {
      String ldId = topOntologyClasses.get(ontologyClassesIndex).getLdId();
      String id = topOntologyClasses.get(ontologyClassesIndex).getId();
      String type = topOntologyClasses.get(ontologyClassesIndex).getType();
      String ldType = topOntologyClasses.get(ontologyClassesIndex).getLdType();
      String prefLabel = topOntologyClasses.get(ontologyClassesIndex).getPrefLabel();
      String definition = topOntologyClasses.get(ontologyClassesIndex).getDefinition();
      String source = topOntologyClasses.get(ontologyClassesIndex).getSource();
      ontologyClassesIndex++;

      Assert.assertTrue("Wrong ldId", results.getCollection().get(i).getLdId().equals(ldId));
      Assert.assertTrue("Wrong id", results.getCollection().get(i).getId().equals(id));
      Assert.assertTrue("Wrong type", results.getCollection().get(i).getType().equals(type));
      Assert.assertTrue("Wrong ldType", results.getCollection().get(i).getLdType().equals(ldType));
      Assert.assertTrue("Wrong prefLabel", results.getCollection().get(i).getPrefLabel().equals(prefLabel));
      Assert.assertTrue("Wrong source", results.getCollection().get(i).getSource().equals(source));
      if (definition != null) {
        Assert.assertTrue("Wrong definition", results.getCollection().get(i).getDefinition().equals(definition));
      }
    }
    // Check pagination information. Note that when doing search on multiple sources we set totalCount, pageCount, and nextPage to 0 to maximize performance.
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 0);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == pageSize);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() == 0);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == 0);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == 0);
  }

  /**
   * TODO: Test pagination. Test arrangements.
   */

  /**
   *  Utility methods
   * */
  private static ObjectNode generateRequestBody(String inputText, ArrayNode ontologies, ArrayNode branches,
                                                ArrayNode valueSets, ArrayNode classes, Optional<Integer> page,
                                                Optional<Integer> pageSize) {
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
    if (page.isPresent()) {
      requestBody.put(BP_PAGE_PARAM, page.get());
    }
    if (pageSize.isPresent()) {
      requestBody.put(BP_PAGE_SIZE_PARAM, pageSize.get());
    }

    return requestBody;
  }

}
