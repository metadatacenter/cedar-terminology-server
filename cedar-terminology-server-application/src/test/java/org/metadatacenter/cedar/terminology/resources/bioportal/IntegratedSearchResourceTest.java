package org.metadatacenter.cedar.terminology.resources.bioportal;

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
import static org.metadatacenter.terms.util.Constants.*;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public class IntegratedSearchResourceTest extends AbstractTerminologyServerResourceTest {



  /* Objects used by the test methods */
  private static ObjectNode branch1;
  private static ObjectNode ontology1;
  private static int ontology1Size = 157000;


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
  public void searchOntologyEmptyInputText() { // It should retrieve all the ontology classes
    // Ontology constraints
    ArrayNode ontologies = mapper.createArrayNode().add(ontology1);
    // Branch constraints
    ArrayNode branches = mapper.createArrayNode();
    // Value set constraints
    ArrayNode valueSets = mapper.createArrayNode();
    // Class constraints
    ArrayNode classes = mapper.createArrayNode();

    // valueConstraints object
    ObjectNode valueConstraints = mapper.createObjectNode();
    valueConstraints.set(VALUE_CONSTRAINTS_ONTOLOGIES, ontologies);
    valueConstraints.set(VALUE_CONSTRAINTS_BRANCHES, branches);
    valueConstraints.set(VALUE_CONSTRAINTS_VALUE_SETS, valueSets);
    valueConstraints.set(VALUE_CONSTRAINTS_CLASSES, classes);
    // parameterObject object
    ObjectNode parameterObject = mapper.createObjectNode();
    parameterObject.set(BP_INTEGRATED_SEARCH_PARAM_VALUE_CONSTRAINTS, valueConstraints);
    parameterObject.put(BP_INTEGRATED_SEARCH_PARAM_INPUT_TEXT, "");
    // Request body
    ObjectNode requestBody = mapper.createObjectNode();
    requestBody.set(BP_INTEGRATED_SEARCH_PARAMS_FIELD, parameterObject);

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
  public void searchOntologyMissingInputText() { // It should retrieve all the ontology classes
    // Ontology constraints
    ArrayNode ontologies = mapper.createArrayNode().add(ontology1);
    // Branch constraints
    ArrayNode branches = mapper.createArrayNode();
    // Value set constraints
    ArrayNode valueSets = mapper.createArrayNode();
    // Class constraints
    ArrayNode classes = mapper.createArrayNode();

    // valueConstraints object
    ObjectNode valueConstraints = mapper.createObjectNode();
    valueConstraints.set(VALUE_CONSTRAINTS_ONTOLOGIES, ontologies);
    valueConstraints.set(VALUE_CONSTRAINTS_BRANCHES, branches);
    valueConstraints.set(VALUE_CONSTRAINTS_VALUE_SETS, valueSets);
    valueConstraints.set(VALUE_CONSTRAINTS_CLASSES, classes);
    // parameterObject object
    ObjectNode parameterObject = mapper.createObjectNode();
    parameterObject.set(BP_INTEGRATED_SEARCH_PARAM_VALUE_CONSTRAINTS, valueConstraints);
    // Request body
    ObjectNode requestBody = mapper.createObjectNode();
    requestBody.set(BP_INTEGRATED_SEARCH_PARAMS_FIELD, parameterObject);
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

}
