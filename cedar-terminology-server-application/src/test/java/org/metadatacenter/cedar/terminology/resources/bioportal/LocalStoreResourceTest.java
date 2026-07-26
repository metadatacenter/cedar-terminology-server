package org.metadatacenter.cedar.terminology.resources.bioportal;

import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.TerminologyServerApplicationTest;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;
import org.metadatacenter.util.test.TestAuthUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.metadatacenter.cedar.terminology.utils.Constants.BP_CHILDREN;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_CLASSES;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_ENDPOINT;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_ONTOLOGIES;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration test that the local SQLite backend is served through the real HTTP stack.
 *
 * A synthetic ontology "LOCALTEST" is written to a temporary catalog + snapshot before the app
 * starts, and the local store is enabled for it via system properties (set in the static
 * initializer, which runs before the {@code DropwizardTestSupport} starts the app). The class-children
 * endpoint for a LOCALTEST class must then be answered from the snapshot, not BioPortal.
 *
 * Auth is provided by {@link TestAuthUtil}'s in-memory user service (installed after startup), so
 * this test needs neither Neo4j nor BioPortal — only the CEDAR environment variables that
 * {@link CedarConfig} requires.
 */
public class LocalStoreResourceTest {

  static {
    // Must run before the test support boots the server, which reads the port env vars.
    // Alternate server ports, so the test instance never collides with a running dev server.
    java.util.Map<String, String> environment =
        new java.util.HashMap<>(org.metadatacenter.config.environment.CedarEnvironmentSource.getAll());
    environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "19004");
    environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "19104");
    environment.put("CEDAR_TERMINOLOGY_STOP_PORT", "19204");
    org.metadatacenter.config.environment.CedarEnvironmentSource.setOverride(environment);
  }

  private static final String ONT = "LOCALTEST";
  private static final String BASE = "http://localtest/";

  static {
    try {
      Path dir = Files.createTempDirectory("localstore-it");

      Path snapshot = dir.resolve("snap.sqlite");
      try (SnapshotStore s = SnapshotStore.openFile(snapshot.toString())) {
        s.initSchema();
        s.addConcept(BASE + "disease", "Disease");
        s.addConcept(BASE + "cancer", "Cancer");
        s.addConcept(BASE + "melanoma", "Melanoma");
        s.addEdge(BASE + "cancer", BASE + "disease", "rdfs:subClassOf");
        s.addEdge(BASE + "melanoma", BASE + "cancer", "rdfs:subClassOf");
        s.materialize();
      }

      Path catalog = dir.resolve("catalog.sqlite");
      try (CatalogStore c = CatalogStore.openFile(catalog.toString())) {
        c.initSchema();
        c.upsertOntology(new CatalogStore.OntologyInfo(ONT, "Local Test", null, "OWL"));
        c.addSnapshot(new CatalogStore.SnapshotInfo("v1", ONT, "1.0", "2025-01-01", "2025-01-01T00:00:00Z",
            "OWL", "subsumption", 3, 2, snapshot.toString(), "v1", "open"));
        c.setTag(ONT, CatalogStore.TAG_LATEST, "v1");
      }

      // Override the (empty) cedar-main.yml localStore config for this test. Uses the non-"cedar."
      // property names the app recognizes, set before the app starts so the local store is enabled.
      System.setProperty("terminologyStore.catalogPath", catalog.toString());
      System.setProperty("terminologyStore.localOntologies", ONT);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static final DropwizardTestSupport<TerminologyServerConfiguration> RULE =
      new DropwizardTestSupport<>(TerminologyServerApplicationTest.class,
          ResourceHelpers.resourceFilePath("test-config.yml"));

  private static ClientBuilder clientBuilder;
  private static String authHeader;
  private static String childrenUrlBase;

  @BeforeAll
  public static void setUp() throws Exception {
    RULE.before();
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_TERMINOLOGY);
    CedarConfig cedarConfig = CedarConfig.getInstance(environment);

    // Replace the app's Neo4j-backed user service with an in-memory one (no auth backend needed).
    TestAuthUtil.installInMemoryUserService(cedarConfig);
    authHeader = TestAuthUtil.getTestUser1AuthHeader(cedarConfig);

    clientBuilder = ClientBuilder.newBuilder();
    childrenUrlBase = "http://localhost:" + RULE.getLocalPort() + "/" + BP_ENDPOINT + "/" + BP_ONTOLOGIES;
  }

  @AfterAll
  public static void tearDown() {
    RULE.after();
  }

  @Test
  public void childrenServedFromLocalStore() {
    String classId = BASE + "cancer";
    String encoded = URLEncoder.encode(classId, StandardCharsets.UTF_8);
    String url = childrenUrlBase + "/" + ONT + "/" + BP_CLASSES + "/" + encoded + "/" + BP_CHILDREN;

    Response response = clientBuilder.build().target(url).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();

    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    PagedResults<OntologyClass> children = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {
    });
    response.close();

    // "melanoma" is the only child of "cancer" in the local snapshot; served without BioPortal.
    Assertions.assertEquals(Integer.valueOf(1), children.getTotalCount());
    OntologyClass child = children.getCollection().get(0);
    Assertions.assertEquals(BASE + "melanoma", child.getLdId());
    Assertions.assertEquals("Melanoma", child.getPrefLabel());
  }
}
