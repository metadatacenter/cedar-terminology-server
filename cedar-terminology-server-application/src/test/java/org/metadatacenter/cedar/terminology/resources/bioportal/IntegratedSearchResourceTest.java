package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jdk.dynalink.linker.ConversionComparator;
import org.junit.*;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ClassValueConstraint;
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
import java.io.IOException;
import java.util.*;

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
    int pageSize = 10; // Small page size to speed up test execution
    ObjectNode requestBody = generateRequestBody("", mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(),
        Optional.of(pageSize));

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == pageSize);
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 1);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == pageSize);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() >= ontology1Size);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  @Test
  public void searchClassesInOntologyMissingInputText() { // It should retrieve all the ontology classes
    int pageSize = 10; // Small page size to speed up test execution
    ObjectNode requestBody = generateRequestBody(null, mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(),
        Optional.of(pageSize));

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == pageSize);
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 1);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == pageSize);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() >= ontology1Size);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  @Test
  public void searchClassesInOntologyWrongLabel() { // Search for ontology classes in a given branch
    String inputText = "aaabbbcccddd"; // Non-existing class label
    int pageSize = 10;
    int expectedNumberOfResults = 0;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(),
        Optional.of(pageSize));

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == expectedNumberOfResults);
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 0);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == 0);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() == 0);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  @Test
  public void searchClassesInOntology() { // Search for ontology classes in a given ontology
    String inputText = "virus"; // Class: http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C14283"
    int pageSize = 20;
    int expectedPageCountLowerLimit = 35;
    int expectedTotalCountLowerLimit = 694;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode().add(ontology1),
        mapper.createArrayNode(), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(),
        Optional.of(pageSize));

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == pageSize);
    // Check that the first result is right
    Assert.assertTrue("Unexpected result: ",
        results.getCollection().get(0).getPrefLabel().toLowerCase().contains(inputText.toLowerCase()));
    // Check that the retrieved classes are from the right source. We limit this check to the first page or results
    // to speed up the tests
    for (SearchResult r : results.getCollection()) {
      String resultSourceAcronym = r.getSource().substring(r.getSource().lastIndexOf("/") + 1);
      Assert.assertTrue("Class source does not match the expected source",
          resultSourceAcronym.equals(ontology1.get(VALUE_CONSTRAINTS_ACRONYM).asText()));
    }
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() >= expectedPageCountLowerLimit);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == pageSize);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() >= expectedTotalCountLowerLimit);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == 2);
  }

  /**
   * Search Classes in Branches
   */

  @Test
  public void searchClassesInBranchEmptyInputText() { // It should retrieve all the classes in the branch
    String inputText = "";
    int pageSize = 10;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(),
        Optional.of(pageSize));

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == pageSize);
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 1);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == pageSize);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() >= branch1Size);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  @Test
  public void searchClassesInBranchMissingInputText() {  // It should retrieve all the classes in the branch
    int pageSize = 10;
    ObjectNode requestBody = generateRequestBody(null, mapper.createArrayNode(),
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(),
        Optional.of(pageSize));

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == pageSize);
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 1);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == pageSize);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() >= branch1Size);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  @Test
  public void searchClassesInBranch() { // Search for ontology classes in a given branch
    String inputText = "coronavirus"; // Search for 'coronavirus' in the 'virus' branch of NCIT
    int pageSize = 100;
    int expectedNumberOfResultsLowerLimit = 7; // There are at least 7 'coronavirus' classes in the branch
    int expectedNumberOfResultsUpperLimit = branch1Size;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(),
        Optional.of(pageSize));

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() >= expectedNumberOfResultsLowerLimit);
    Assert.assertTrue("Wrong number of results", results.getCollection().size() <= expectedNumberOfResultsUpperLimit);
    // Check that the first result is right
    Assert.assertTrue("Unexpected result: ",
        results.getCollection().get(0).getPrefLabel().toLowerCase().contains(inputText.toLowerCase()));
    // Check that the retrieved classes are from the right source. We limit this check to the first page or results
    // to speed up the tests
    for (SearchResult r : results.getCollection()) {
      String resultSourceAcronym = r.getSource().substring(r.getSource().lastIndexOf("/") + 1);
      Assert.assertTrue("Class source does not match the expected source",
          resultSourceAcronym.equals(ontology1.get(VALUE_CONSTRAINTS_ACRONYM).asText()));
    }
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() > 0);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() >= expectedNumberOfResultsLowerLimit);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() <= pageSize);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() >= expectedNumberOfResultsLowerLimit);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() <= expectedNumberOfResultsUpperLimit);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  @Test
  public void searchClassesInBranchWrongLabel() { // Search for ontology classes in a given branch
    String inputText = "aaabbbcccddd"; // Non-existing class label
    int pageSize = 1;
    int expectedNumberOfResults = 0;

    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode().add(branch1), mapper.createArrayNode(), mapper.createArrayNode(), Optional.empty(),
        Optional.of(pageSize));

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == expectedNumberOfResults);
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 0);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() == 0);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == 0);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  /**
   * Search Values in Value Sets
   */

  @Test
  public void searchValuesNonProvisionalValueSetEmptyInputText() { // It should retrieve all the values in the value set
    String inputText = "";
    int pageSize = 200; // higher than valueSet1Size to ensure that all the results are returned
    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode().add(valueSet1), mapper.createArrayNode(), Optional.empty()
        , Optional.of(pageSize));

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == valueSet1Size);
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 1);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() == valueSet1Size);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == valueSet1Size);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  @Test
  public void searchValuesNonProvisionalValueSet() { // Search for values that match the inputText in the value set
    String inputText = "barton";
    int expectedResultsCount = 2;
    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode().add(valueSet1), mapper.createArrayNode(), Optional.empty()
        , Optional.empty());

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == expectedResultsCount);
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 1);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() == expectedResultsCount);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == expectedResultsCount);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  @Test
  public void searchValuesProvisionalValueSetEmptyInputText() { // It should retrieve all the values in the value set
    // Create provisional value set with two values
    ValueSet createdVs = createValueSet(vs1);
    Value createdValue1 = createValue(createdVs.getLdId(), value1);
    Value createdValue2 = createValue(createdVs.getLdId(), value2);
    int createdVsSize = 2;
    // Wait
    //shortWaitToEnsureBioPortalIndexUpdated();
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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == createdVsSize);
    // Check that the results are right
    for (SearchResult r : results.getCollection()) {
      Assert.assertTrue("Unexpected value", r.getLdId().equals(createdValue1.getLdId()) ||
          r.getLdId().equals(createdValue2.getLdId()));
    }
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 1);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() == createdVsSize);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == createdVsSize);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  @Test
  public void searchValuesProvisionalValueSet() { // Search for values in the value set that match the inputText
    // Create provisional value set with two values
    ValueSet createdVs = createValueSet(vs2);
    createValue(createdVs.getLdId(), value1);
    Value createdValue2 = createValue(createdVs.getLdId(), value2);
    // Wait
    //shortWaitToEnsureBioPortalIndexUpdated();
    longWaitToEnsureBioPortalIndexUpdated();

    // Generate input body based on the created value set
    ObjectNode vs = mapper.createObjectNode();
    vs.put(VALUE_CONSTRAINTS_URI, createdVs.getLdId());
    vs.put(VALUE_CONSTRAINTS_VS_COLLECTION, Util.getShortIdentifier(createdVs.getVsCollection()));

    String inputText = "Value2"; // Note that the label of the second value created is value2_test
    ObjectNode requestBody = generateRequestBody(inputText, mapper.createArrayNode(),
        mapper.createArrayNode(), mapper.createArrayNode().add(vs), mapper.createArrayNode(), Optional.empty(),
        Optional.empty());

    // Service invocation
    Response response = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(requestBody));
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results retrieved
    int expectedNumberOfResults = 1;
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {
    });
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == expectedNumberOfResults);
    // Check that the result found matches the expected result
    Assert.assertTrue("Unexpected value",
        results.getCollection().get(0).getLdId().equals(createdValue2.getLdId()));
    // Check pagination information
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == 1);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() == 1);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == 1);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  /**
   * Search Enumerated Classes
   */

  @Test
  public void searchEnumeratedClassesEmptySearch() throws IOException {
    String inputText = "";
    int pageSize = 10;
    ArrayNode enumeratedClasses =
        mapper.createArrayNode().add(enumeratedClass1).add(enumeratedClass2).add(enumeratedClass3);

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == enumeratedClasses.size());
    // Check that the results are right
    // Sort the enumerated classes by prefLabel so that we can compare them in order with the returned results
    List<ClassValueConstraint> classes = Arrays.asList(mapper.readValue(enumeratedClasses.toString(),
        ClassValueConstraint[].class));
    classes.sort(Comparator.comparing(ClassValueConstraint::getPrefLabel, String.CASE_INSENSITIVE_ORDER));

    for (int i = 0; i < classes.size(); i++) {
      String ldId = classes.get(i).getUri();
      String id = Util.getShortIdentifier(ldId);
      String type = classes.get(i).getType();
      String ldType = BP_TYPE_BASE + type;
      String prefLabel = classes.get(i).getPrefLabel();
      String source = BP_API_BASE + BP_ONTOLOGIES + classes.get(i).getSource();
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
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  @Test
  public void searchEnumeratedClassesEmptySearchTwoPages() throws IOException {
    String inputText = "";
    int pageSize = 2;
    ArrayNode enumeratedClasses =
        mapper.createArrayNode().add(enumeratedClass1).add(enumeratedClass2).add(enumeratedClass3);

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
    Assert.assertTrue("Wrong number of results", resultsPage1.getCollection().size() == 2);
    Assert.assertTrue("Wrong number of results", resultsPage2.getCollection().size() == 1);

    List<SearchResult> results = new ArrayList<>();
    results.add(resultsPage1.getCollection().get(0));
    results.add(resultsPage1.getCollection().get(1));
    results.add(resultsPage2.getCollection().get(0));

    // Check that the results are right
    // Sort the enumerated classes by prefLabel so that we can compare them in order with the returned results
    List<ClassValueConstraint> classes = Arrays.asList(mapper.readValue(enumeratedClasses.toString(),
        ClassValueConstraint[].class));
    classes.sort(Comparator.comparing(ClassValueConstraint::getPrefLabel, String.CASE_INSENSITIVE_ORDER));
    for (int i = 0; i < classes.size(); i++) {
      String ldId = classes.get(i).getUri();
      String id = Util.getShortIdentifier(ldId);
      String type = classes.get(i).getType();
      String ldType = BP_TYPE_BASE + type;
      String prefLabel = classes.get(i).getPrefLabel();
      String source = BP_API_BASE + BP_ONTOLOGIES + classes.get(i).getSource();

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
    Assert.assertTrue("Wrong prevPage", resultsPage1.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", resultsPage1.getNextPage() == 2);

    // Check pagination information (page 2)
    Assert.assertTrue("Wrong page", resultsPage2.getPage() == 2);
    Assert.assertTrue("Wrong pageCount", resultsPage2.getPageCount() == 2);
    Assert.assertTrue("Wrong pageSize", resultsPage2.getPageSize() == 1);
    Assert.assertTrue("Wrong totalCount", resultsPage2.getTotalCount() == 3);
    Assert.assertTrue("Wrong prevPage", resultsPage2.getPrevPage() == 1);
    Assert.assertTrue("Wrong prevPage", resultsPage2.getNextPage() == null);
  }

  @Test
  public void searchEnumeratedClasses() {
    String inputText = "Mycosis";
    int pageSize = 10;
    int numberOfExpectedResults = 1;
    ArrayNode enumeratedClasses =
        mapper.createArrayNode().add(enumeratedClass1).add(enumeratedClass2).add(enumeratedClass3);

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
    Assert.assertTrue("Wrong number of results", results.getCollection().size() == 1);
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
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() == 1);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == 1);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  /**
   * Search based on multiple constraints
   */

  @Test
  public void searchEnumeratedClassesAndOntologyEmptySearch() {
    String inputText = "";
    int pageSize = 10;

    // I will set the label of one enumerated class to "000" to ensure that it shows up at the top, and the label of
    // other class to "zzz" to ensure that it's not in the first page
    enumeratedClass1.put(VALUE_CONSTRAINTS_PREFLABEL, "000");
    enumeratedClass2.put(VALUE_CONSTRAINTS_PREFLABEL, "zzz");
    ArrayNode enumeratedClasses =
        mapper.createArrayNode().add(enumeratedClass1).add(enumeratedClass2);
    ArrayNode ontologies = mapper.createArrayNode().add(ontology1);

    // Generate request
    ObjectNode requestBody = generateRequestBody(inputText, ontologies,
        mapper.createArrayNode(), mapper.createArrayNode(),
        enumeratedClasses, Optional.empty(), Optional.of(pageSize));

    // Initial request to get some results (20) from the ontology, so that I can check when making the other request
    // that nothing is missing
    ObjectNode ontSizeRequestBody = generateRequestBody(inputText, ontologies,
        mapper.createArrayNode(), mapper.createArrayNode(),
        mapper.createArrayNode(), Optional.of(1), Optional.of(pageSize));
    Response ontSizeResponse = client.target(baseUrlBpIntegratedSearch).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(ontSizeRequestBody));
    PagedResults<SearchResult> ontology1Response = ontSizeResponse.readEntity(new GenericType<PagedResults<SearchResult>>() {
    });
    List<SearchResult> ontology1Classes = ontology1Response.getCollection();
    int ontology1CurrentSize = ontology1Response.getTotalCount();

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

    Assert.assertTrue("Wrong page size", pageSize == results.getPageSize());
    Assert.assertTrue("Wrong page size", pageSize == results.getCollection().size());

    // Check that the class with label "000" is returned at the top
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

    // Check that the rest of the results are right. We start with i=1 because we've already checked the first result
    int ontologyClassesIndex = 0;
    for (int i = 1; i < pageSize; i++) {
      ldId = ontology1Classes.get(ontologyClassesIndex).getLdId();
      id = ontology1Classes.get(ontologyClassesIndex).getId();
      type = ontology1Classes.get(ontologyClassesIndex).getType();
      ldType = ontology1Classes.get(ontologyClassesIndex).getLdType();
      prefLabel = ontology1Classes.get(ontologyClassesIndex).getPrefLabel();
      String definition = ontology1Classes.get(ontologyClassesIndex).getDefinition();
      source = ontology1Classes.get(ontologyClassesIndex).getSource();
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
    // Check pagination information. Note that when doing search on multiple sources we set totalCount, pageCount,
    // and nextPage to null to maximize performance.
    Assert.assertTrue("Wrong page", results.getPage() == 1);
    Assert.assertTrue("Wrong pageCount", results.getPageCount() == null);
    Assert.assertTrue("Wrong totalCount", results.getTotalCount() == ontology1CurrentSize + 2);
    Assert.assertTrue("Wrong pageSize", results.getPageSize() == pageSize);
    Assert.assertTrue("Wrong prevPage", results.getPrevPage() == null);
    Assert.assertTrue("Wrong prevPage", results.getNextPage() == null);
  }

  /**
   * Utility methods
   */

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
