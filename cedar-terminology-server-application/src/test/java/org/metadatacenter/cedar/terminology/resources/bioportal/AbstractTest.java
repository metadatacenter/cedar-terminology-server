package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.*;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;
import org.metadatacenter.terms.util.Util;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.TestUtil;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
public abstract class AbstractTest {

  protected static CedarConfig cedarConfig;
  protected static Client client;
  protected static String authHeader;
  protected static final String BASE_URL = "http://localhost";
  protected static String baseUrlBp;
  protected static String baseUrlBpSearch;
  protected static String baseUrlBpOntologies;
  protected static String baseUrlBpVSCollections;

  protected static OntologyClass class1;
  protected static Relation relation1;
  protected static List<OntologyClass> createdClasses;
  protected static List<Relation> createdRelations;

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
  }

  /**
   * Tears down the test fixture.
   * (Called after every test case method.)
   */
  @After
  public void tearDownAbstract() {
    try {
      // Relations are removed before removing classes. Otherwise, when removing a class, BioPortal will
      // automatically remove the associated relation(s)
      deleteCreatedRelations();
      deleteCreatedClasses();
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

  protected static void deleteCreatedClasses() throws Exception {
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

  protected static void deleteCreatedRelations() throws Exception {
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

}
