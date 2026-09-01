package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.OntologyProperty;

import java.util.List;

import static org.metadatacenter.cedar.terminology.utils.Constants.*;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */
// Exercises live BioPortal; excluded from the default build (surefire excludedGroups).
// Run with -DexcludedGroups= (or a bioportal profile) when a BioPortal API key is configured.
@Tag("bioportal")
public class OntologyResourceTest extends AbstractTerminologyServerResourceTest {

  private static Ontology ontology1;

  /**
   * One-time initialization code.
   * (Called once before any of the test methods in the class).
   */
  @BeforeAll
  public static void oneTimeSetUp() {
    // Initialize ontology information
    ontology1 = new Ontology("NCIT", "https://data.bioontology.org/ontologies/",
        "National Cancer Institute Thesaurus", false, null);
  }

  /**
   * Sets up the test fixture.
   * (Called before every test case method.)
   */
  @BeforeEach
  public void setUp() {
  }

  /**
   * Tears down the test fixture.
   * (Called after every test case method.)
   */
  @AfterEach
  public void tearDown() {
  }

  @Test
  @Disabled("Requires live BioPortal ontology data and is not deterministic in the default build")
  public void findAllOntologiesTest() {
    String url = baseUrlBpOntologies;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the results returned
    List<Ontology> ontologies = response.readEntity(new GenericType<>() {
    });
    response.close();
    Assertions.assertTrue( ontologies.size() > 0,"No ontologies returned");
    Assertions.assertTrue( ontologies.size() > 525,"Wrong number of ontologies returned");
  }

  @Test
  public void findOntologyTest() {
    String url = baseUrlBpOntologies + "/" + ontology1.getId();
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that the call returns the expected ontology
    Ontology ontology = response.readEntity(Ontology.class);
    response.close();
    Assertions.assertEquals( "NCIT", ontology.getId(),"Wrong ontology id");
    Assertions.assertEquals( "National Cancer Institute Thesaurus", ontology.getName(),"Wrong ontology name");
  }

  @Test
  public void findRootClassesTest() {
    String url = baseUrlBpOntologies + "/" + ontology1.getId() + "/" + BP_CLASSES + "/" + BP_ROOTS;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check results
    List<OntologyClass> roots = response.readEntity(new GenericType<>() {
    });
    response.close();
    Assertions.assertTrue( roots.size() > 0,"No roots returned");
    // Basic check to see whether "Biological Process" is found
    String rootId = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C17828";
    boolean found = false;
    for (OntologyClass c : roots) {
      if (c.getLdId().equals(rootId)) {
        found = true;
        break;
      }
    }
    Assertions.assertTrue( found,"Expected root class not found");
  }

  @Test
  public void findRootPropertiesTest() {
    String ontology = "BIBFRAME";
    String url = baseUrlBpOntologies + "/" + ontology + "/" + BP_PROPERTIES + "/" + BP_ROOTS;
    // Service invocation
    Response response = clientBuilder.build().target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check results
    List<OntologyProperty> roots = response.readEntity(new GenericType<>() {
    });
    response.close();
    Assertions.assertTrue( roots.size() > 0,"No roots returned");
    // Basic check to see if the "Administrative metadata" root property is found
    String rootId = "http://id.loc.gov/ontologies/bibframe/adminMetadata";
    boolean found = false;
    for (OntologyProperty property : roots) {
      if (property.getLdId().equals(rootId)) {
        found = true;
        break;
      }
    }
    Assertions.assertTrue( found,"Expected root property not found");
  }

}
