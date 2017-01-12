package org.metadatacenter.cedar.terminology.resources.bioportal;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.*;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.terms.domainObjects.ValueSet;
import org.metadatacenter.terms.util.Util;
import org.metadatacenter.util.test.TestUtil;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public abstract class AbstractTerminologyServerResourceTest {

  protected static CedarConfig cedarConfig;
  protected static Client client;
  protected static String authHeader;
  protected static final String BASE_URL = "http://localhost";
  protected static String baseUrlBp;
  protected static String baseUrlBpSearch;
  protected static String baseUrlBpOntologies;
  protected static String baseUrlBpVSCollections;

  protected static List<OntologyClass> createdClasses;
  protected static List<Relation> createdRelations;
  protected static List<ValueSet> createdValueSets;
  protected static List<Value> createdValues;

  protected static OntologyClass class1;
  protected static Relation relation1;
  protected static ValueSet vs1;
  protected static Value value1;
  protected static Value value2;

  @ClassRule
  public static final DropwizardAppRule<TerminologyServerConfiguration> RULE =
      new DropwizardAppRule<>(TerminologyServerApplication.class, ResourceHelpers
          .resourceFilePath("test-config.yml"));

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeClass
  public static void oneTimeSetUpAbstract() {
    // Wait while cache is being generated
    while ((Cache.ontologiesCache.size() == 0) || (Cache.valueSetsCache.size() == 0)) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    cedarConfig = CedarConfig.getInstance();
    authHeader = TestUtil.getTestUser1AuthHeader();

    baseUrlBp = BASE_URL + ":" + RULE.getLocalPort() + "/" + BP_ENDPOINT;
    baseUrlBpSearch = baseUrlBp + "/" + BP_SEARCH;
    baseUrlBpOntologies = baseUrlBp + "/" + BP_ONTOLOGIES;
    baseUrlBpVSCollections =  baseUrlBp + "/" + BP_VALUE_SET_COLLECTIONS;

    client = new JerseyClientBuilder(RULE.getEnvironment()).build("BioPortal search endpoint client");
    client.property(ClientProperties.CONNECT_TIMEOUT, cedarConfig.getTerminologyConfig().getBioPortal().getConnectTimeout());
    client.property(ClientProperties.READ_TIMEOUT, cedarConfig.getTerminologyConfig().getBioPortal().getSocketTimeout());

    // Initialize test class
    String classLabel = "class1_test";
    String classCreator = "http://data.bioontology.org/users/cedar-test";
    String classOntology = "http://data.bioontology.org/ontologies/CEDARPC";
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

    // Initialize test value set
    String vsLabel = "vs1_test";
    String vsCreator = "http://data.bioontology.org/users/cedar-test";
    String vsCollection = "http://data.bioontology.org/ontologies/CEDARVS";
    List vsDefinitions = new ArrayList<>();
    vsDefinitions.add("vsDefinition1");
    vsDefinitions.add("vsDefinition2");
    List vsSynonyms = new ArrayList<>();
    vsSynonyms.add("vsSynonym1");
    vsSynonyms.add("vsSynonym2");
    List vsRelations = new ArrayList<>();
    vs1 = new ValueSet(null, null, vsLabel, vsCreator, vsCollection, vsDefinitions, vsSynonyms, vsRelations, true, null);

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
    value1 = new Value(null, null, value1Label, value1Creator, null, value1VsCollection, value1Definitions,
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
    value2 = new Value(null, null, value2Label, value2Creator, null, value2VsCollection, value2Definitions,
        value2Synonyms, value2Relations, true, null);
  }

  /**
   * Sets up the test fixture.
   * (Called before every test case method.)
   */
  @Before
  public void setUpAbstract() {
    // Test objects
    createdClasses = new ArrayList<>();
    createdRelations = new ArrayList<>();
    createdValueSets = new ArrayList<>();
    createdValues = new ArrayList<>();
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

  /**
   * Test Utils
   */

  /* Classes */

  protected static OntologyClass createClass(OntologyClass c) {
    String url = baseUrlBpOntologies + "/" + Util.getShortIdentifier(c.getOntology()) + "/" + BP_CLASSES;
    // Service invocation
    Response response = client.target(url).request().header("Authorization", authHeader).post(Entity.json(c));
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
      Response findResponse = client.target(findUrl).request().header("Authorization", authHeader).get();
      if (findResponse.getStatus() == Response.Status.OK.getStatusCode()) {
        Response deleteResponse = client.target(deleteUrl).request().header("Authorization", authHeader).delete();
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
    Response response = client.target(url).request().header("Authorization", authHeader).post(Entity.json(r));
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
      Response findResponse = client.target(url).request().header("Authorization", authHeader).get();
      if (findResponse.getStatus() == Response.Status.OK.getStatusCode()) {
        Response deleteResponse = client.target(url).request().header("Authorization", authHeader).delete();
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
    Response response = client.target(url).request().header("Authorization", authHeader).post(Entity.json(vs));
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
      Response findResponse = client.target(findUrl).request().header("Authorization", authHeader).get();
      if (findResponse.getStatus() == Response.Status.OK.getStatusCode()) {
        Response deleteResponse = client.target(deleteUrl).request().header("Authorization", authHeader).delete();
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
    Response response = client.target(url).request().header("Authorization", authHeader).post(Entity.json(v));
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
      Response findResponse = client.target(findUrl).request().header("Authorization", authHeader).get();
      if (findResponse.getStatus() == Response.Status.OK.getStatusCode()) {
        Response deleteResponse = client.target(deleteUrl).request().header("Authorization", authHeader).delete();
        if (deleteResponse.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
          throw new Exception("Couldn't delete value: Id = " + v.getId());
        }
      } else {
        throw new Exception("Couldn't find value: Id = " + v.getId());
      }
    }
  }

}
