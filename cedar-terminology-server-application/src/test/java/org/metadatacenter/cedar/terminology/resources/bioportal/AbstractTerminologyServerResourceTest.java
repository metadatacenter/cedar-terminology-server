package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.*;
import org.metadatacenter.cedar.terminology.TerminologyServerApplicationTest;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.terms.TerminologyService;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;
import org.metadatacenter.terms.util.Util;
import org.metadatacenter.util.test.TestUserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public abstract class AbstractTerminologyServerResourceTest {

  protected static CedarConfig cedarConfig;
  protected static Client client;
  protected static String authHeader;
  protected static ObjectMapper mapper;
  protected static final String BASE_URL = "http://localhost";
  protected static String baseUrlBp;
  protected static String baseUrlBpSearch;
  protected static String baseUrlBpIntegratedSearch;
  protected static String baseUrlBpPropertySearch;
  protected static String baseUrlBpOntologies;
  protected static String baseUrlBpVSCollections;

  protected static List<OntologyClass> createdClasses;
  protected static List<Relation> createdRelations;
  protected static List<ValueSet> createdValueSets;
  protected static List<Value> createdValues;

  protected static OntologyClass class1;
  protected static Relation relation1;
  protected static ValueSet vs1;
  protected static ValueSet vs2;
  protected static Value value1;
  protected static Value value2;

  protected final static String classCreator = "http://data.bioontology.org/users/cedar-test";
  protected final static String classOntology = "http://data.bioontology.org/ontologies/CEDARPC";

  protected static final Logger log = LoggerFactory.getLogger(AbstractTerminologyServerResourceTest.class);

  /**
   * Status codes not part of the JAX-RS Status enum.
   */
  protected static final int STATUS_CODE_UNPROCESSABLE_ENTITY = 422;

  @ClassRule
  public static final DropwizardAppRule<TerminologyServerConfiguration> RULE =
      new DropwizardAppRule<>(TerminologyServerApplicationTest.class,
          ResourceHelpers.resourceFilePath("test-config.yml"));

  /**
   * One-time initialization code.
   * (Called once before any test class).
   */
  @BeforeClass
  public static void oneTimeSetUpAbstract() {

    SystemComponent systemComponent = SystemComponent.SERVER_TERMINOLOGY;
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(systemComponent);
    CedarConfig cedarConfig = CedarConfig.getInstance(environment);

    AbstractTerminologyServerResourceTest.cedarConfig = cedarConfig;

    baseUrlBp = BASE_URL + ":" + RULE.getLocalPort() + "/" + BP_ENDPOINT;
    baseUrlBpSearch = baseUrlBp + "/" + BP_SEARCH;
    baseUrlBpIntegratedSearch = baseUrlBp + "/" + BP_INTEGRATED_SEARCH;
    baseUrlBpPropertySearch = baseUrlBp + "/" + BP_PROPERTY_SEARCH;
    baseUrlBpOntologies = baseUrlBp + "/" + BP_ONTOLOGIES;
    baseUrlBpVSCollections = baseUrlBp + "/" + BP_VALUE_SET_COLLECTIONS;

    client = new JerseyClientBuilder(RULE.getEnvironment()).build("BioPortal search endpoint client");
    client.property(ClientProperties.CONNECT_TIMEOUT, cedarConfig.getTerminologyConfig().getBioPortal()
        .getConnectTimeout());
    client.property(ClientProperties.READ_TIMEOUT, cedarConfig.getTerminologyConfig().getBioPortal().getSocketTimeout
        () * 20); // enough time to build the ontologies and value sets cache if it has not been created yet


    log.info("CEDAR Config - Test user 1:");
    log.info(cedarConfig.getTestUsers().getTestUser1().getId());
    log.info("-------------------");
    authHeader = TestUserUtil.getTestUser1AuthHeader(cedarConfig);

    mapper = new ObjectMapper();

  }

  /**
   * Sets up the test fixture.
   * (Called before every test case method.)
   */
  @Before
  public void setUpAbstract() {

    // OPTIONAL. This block cleans up all the provisional classes created for testing. Uncomment and run it when needed
//    try {
//      deleteProvisionalClassesCreatedForTesting();
//      System.exit(1);
//    } catch (Exception e) {
//      e.printStackTrace();
//    }

    createdClasses = new ArrayList<>();
    createdRelations = new ArrayList<>();
    createdValueSets = new ArrayList<>();
    createdValues = new ArrayList<>();

    // Initialize test class
    String classLabel = "class1_test";
    List classDefinitions = new ArrayList<>();
    classDefinitions.add("classDefinition1");
    classDefinitions.add("classDefinition2");
    List classSynonyms = new ArrayList<>();
    classSynonyms.add("classSynonym1");
    classSynonyms.add("classSynonym2");
    List classRelations = new ArrayList<>();
    class1 = new OntologyClass(null, null, classLabel, classCreator, classOntology, classDefinitions,
        classSynonyms, null, classRelations, true, null, false);

    // Initialize test relation - the source class id will be set later
    String relationType = "http://www.w3.org/2004/02/skos/core#closeMatch";
    String relationCreator = "http://data.bioontology.org/users/cedar-test";
    String targetClassId = "http://purl.bioontology.org/ontology/CPT/1002796";
    String targetClassOntology = "http://data.bioontology.org/ontologies/CPT";
    relation1 = new Relation(null, null, null, relationType, targetClassId, targetClassOntology, null, relationCreator);

    // Initialize test value set 1
    String vs1Label = "vs1_test";
    String vs1Creator = "http://data.bioontology.org/users/cedar-test";
    String vs1Collection = "http://data.bioontology.org/ontologies/CEDARVS";
    List vs1Definitions = new ArrayList<>();
    vs1Definitions.add("vs1Definition1");
    vs1Definitions.add("vs1Definition2");
    List vs1Synonyms = new ArrayList<>();
    vs1Synonyms.add("vs1Synonym1");
    vs1Synonyms.add("vs1Synonym2");
    List vs1Relations = new ArrayList<>();
    vs1 = new ValueSet(null, null, vs1Label, vs1Creator, vs1Collection, vs1Definitions, vs1Synonyms, vs1Relations,
        true, null);

    // Initialize test value set 2
    String vs2Label = "vs2_test";
    String vs2Creator = "http://data.bioontology.org/users/cedar-test";
    String vs2Collection = "http://data.bioontology.org/ontologies/CEDARVS";
    List vs2Definitions = new ArrayList<>();
    vs2Definitions.add("vs2Definition1");
    vs2Definitions.add("vs2Definition2");
    List vs2Synonyms = new ArrayList<>();
    vs2Synonyms.add("vs2Synonym1");
    vs2Synonyms.add("vs2Synonym2");
    List vs2Relations = new ArrayList<>();
    vs2 = new ValueSet(null, null, vs2Label, vs2Creator, vs2Collection, vs2Definitions, vs2Synonyms, vs2Relations,
        true, null);

    // Initialize test value 1 - the vsId will be set later
    String value1Label = "value1_test";
    String value1Creator = "http://data.bioontology.org/users/cedar-test";
    String value1VsCollection = "http://data.bioontology.org/ontologies/CEDARVS";
    List value1Definitions = new ArrayList<>();
    value1Definitions.add("value1Definition1");
    value1Definitions.add("value1Definition2");
    List value1Synonyms = new ArrayList<>();
    value1Synonyms.add("value1Synonym1");
    value1Synonyms.add("value1Synonym2");
    List valueRelations = new ArrayList<>();
    value1 = new Value(null, null, value1Label, null, null, value1Creator, null, value1VsCollection, value1Definitions,
        value1Synonyms, valueRelations, true, null);

    // Initialize test value 2 - the vsId will be set later
    String value2Label = "value2_test";
    String value2Creator = "http://data.bioontology.org/users/cedar-test";
    String value2VsCollection = "http://data.bioontology.org/ontologies/CEDARVS";
    List value2Definitions = new ArrayList<>();
    value2Definitions.add("value2Definition1");
    value2Definitions.add("value2Definition2");
    List value2Synonyms = new ArrayList<>();
    value2Synonyms.add("value2Synonym1");
    value2Synonyms.add("value2Synonym2");
    List value2Relations = new ArrayList<>();
    value2 = new Value(null, null, value2Label, null, null, value2Creator, null, value2VsCollection, value2Definitions,
        value2Synonyms, value2Relations, true, null);
  }

  /**
   * Tears down the test fixture.
   * (Called after every test case method.)
   */
  @After
  public void tearDownAbstract() {
    try {
      // Relations should be removed before classes. Otherwise, when removing a class, BioPortal will
      // automatically remove the associated relation(s)
      deleteCreatedRelations();
      deleteCreatedClasses();
      deleteCreatedValueSets();
      deleteCreatedValues();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @AfterClass
  public static void oneTearDownAbstract() {
    client.close();
  }

  /**
   * Test Utils
   */

  /* Classes */
  protected static OntologyClass createClass(OntologyClass c) {
    String url = baseUrlBpOntologies + "/" + Util.getShortIdentifier(c.getOntology()) + "/" + BP_CLASSES;
    // Service invocation
    Response response = client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(c));
    // Check HTTP response
    Assert.assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    OntologyClass created = response.readEntity(OntologyClass.class);
    createdClasses.add(created);
    return created;
  }

  private static void deleteCreatedClasses() throws Exception {
    for (OntologyClass c : createdClasses) {
      // Check if the class still exists
      String findUrl = baseUrlBpOntologies + "/" + Util.getShortIdentifier(c.getOntology()) + "/" +
          BP_CLASSES + "/" + c.getId();
      String deleteUrl = baseUrlBp + "/" + BP_CLASSES + "/" + c.getId();
      Response findResponse = client.target(findUrl).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
      if (findResponse.getStatus() == Response.Status.OK.getStatusCode()) {
        Response deleteResponse =
            client.target(deleteUrl).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).delete();
        if (deleteResponse.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
          throw new Exception("Couldn't delete class: Id = " + c.getLdId());
        }
      } else {
        throw new Exception("Couldn't find class: Id = " + c.getLdId());
      }
    }
  }

  /* Relations */

  protected static Relation createRelation(OntologyClass c, Relation r) {
    String url = baseUrlBp + "/" + BP_RELATIONS;
    OntologyClass createdClass = createClass(c);
    // Create provisional relation
    r.setSourceClassId(createdClass.getId());
    Response response = client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(r));
    // Check HTTP response
    Assert.assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    Relation created = response.readEntity(Relation.class);
    createdRelations.add(created);
    return created;
  }

  private static void deleteCreatedRelations() throws Exception {
    for (Relation r : createdRelations) {
      // Check if the relation still exists
      String url = baseUrlBp + "/" + BP_RELATIONS + "/" + r.getId();
      Response findResponse = client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
      if (findResponse.getStatus() == Response.Status.OK.getStatusCode()) {
        Response deleteResponse = client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).delete();
        if (deleteResponse.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
          throw new Exception("Couldn't delete relation: Id = " + r.getLdId() +
              ". This relation could have been automatically removed by BioPortal when deleting the class that " +
              "contained it");
        }
      } else {
        throw new Exception("Couldn't find relation: Id = " + r.getLdId() +
            ". This relation could have been automatically removed by BioPortal when deleting the class that " +
            "contained it");
      }
    }
  }

  /* Value Sets */

  protected static ValueSet createValueSet(ValueSet vs) {
    String url = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(vs.getVsCollection()) + "/" + BP_VALUE_SETS;
    Response response =
        client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(vs));
    // Check HTTP response
    Assert.assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    ValueSet created = response.readEntity(ValueSet.class);
    createdValueSets.add(created);
    return created;
  }

  private static void deleteCreatedValueSets() throws Exception {
    for (ValueSet vs : createdValueSets) {
      // Check if the value set still exists
      String findUrl = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(vs.getVsCollection()) + "/" +
          BP_VALUE_SETS + "/" + vs.getId();
      String deleteUrl = baseUrlBp + "/" + BP_VALUE_SETS + "/" + vs.getId();
      Response findResponse = client.target(findUrl).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
      if (findResponse.getStatus() == Response.Status.OK.getStatusCode()) {
        Response deleteResponse =
            client.target(deleteUrl).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).delete();
        if (deleteResponse.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
          throw new Exception("Couldn't delete value set: Id = " + vs.getLdId());
        }
      } else {
        throw new Exception("Couldn't find value set: Id = " + vs.getLdId());
      }
    }
  }

  /* Values */

  // Creates a value v in a value set vs
  protected static Value createValue(String vsLdId, Value v) {
    v.setVsId(vsLdId);
    String url = null;
    try {
      url = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(v.getVsCollection()) + "/"
          + BP_VALUE_SETS + "/" + Util.encodeIfNeeded(v.getVsId()) + "/" + BP_VALUES;
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    // Service invocation
    Response response = client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).post(Entity.json(v));
    // Check HTTP response
    Assert.assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    Value created = response.readEntity(Value.class);
    createdValues.add(created);
    return created;
  }

  private static void deleteCreatedValues() throws Exception {
    for (Value v : createdValues) {
      // Check if the value still exists
      String findUrl = baseUrlBpVSCollections + "/" + Util.getShortIdentifier(v.getVsCollection()) + "/" + BP_VALUES
          + "/" + v.getId();
      String deleteUrl = baseUrlBp + "/" + BP_VALUES + "/" + v.getId();
      Response findResponse = client.target(findUrl).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
      if (findResponse.getStatus() == Response.Status.OK.getStatusCode()) {
        Response deleteResponse =
            client.target(deleteUrl).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).delete();
        if (deleteResponse.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
          throw new Exception("Couldn't delete value: Id = " + v.getId());
        }
      } else {
        throw new Exception("Couldn't find value: Id = " + v.getId());
      }
    }
  }

  /* Properties */
  protected static PagedResults<SearchResult> searchProperties(String q) {
    // Service invocation
    Response response =
        client.target(baseUrlBpPropertySearch).queryParam("q", q).request().header(HTTP_HEADER_AUTHORIZATION,
        authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results
    PagedResults<SearchResult> properties = response.readEntity(new GenericType<PagedResults<SearchResult>>() {
    });
    return properties;
  }

  /* Delete all provisional classes that were created for testing. Sometimes the tests fail and some classes are not
  deleted. This method can be used to perform a full clean-up of the provisional classes created for testing purposes */
  protected static void deleteProvisionalClassesCreatedForTesting() throws Exception {
    // Retrieve all provisional classes
    String url = baseUrlBp + "/" + BP_PROVISIONAL_CLASSES + "?pageSize=5000";
    // Service invocation
    Response response = client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    PagedResults<OntologyClass> results = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {
    });
    int deletedCount = 0;
    for (OntologyClass pc : results.getCollection()) {
      if (pc.getCreator() != null && pc.getCreator().equals(classCreator)) {
        if (pc.getPrefLabel().toLowerCase().contains("test")) {
          // Delete provisional class
          String deleteUrl = baseUrlBp + "/" + BP_CLASSES + "/" + pc.getId();
          Response deleteResponse =
              client.target(deleteUrl).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).delete();
          if (deleteResponse.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
            throw new Exception("Couldn't delete class: Id = " + pc.getLdId());
          }
          deletedCount++;
          System.out.println("Class deleted successfully: " + pc.getPrefLabel());
        }
      }
    }
    System.out.println("No. classes deleted: " + deletedCount);
  }

  protected static void shortWaitToEnsureBioPortalIndexUpdated() {
    // Wait to be sure that the BioPortal search index has been updated
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  protected static void longWaitToEnsureBioPortalIndexUpdated() {
    // Wait to be sure that the BioPortal search index has been updated
    try {
      Thread.sleep(60000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

}
