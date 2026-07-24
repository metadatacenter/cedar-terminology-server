package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.Assert;
import org.junit.Test;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.metadatacenter.cedar.terminology.utils.Constants.BP_CHILDREN;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_CLASSES;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration test that the local SQLite backend is served through the real HTTP stack.
 *
 * A synthetic ontology "LOCALTEST" is written to a temporary catalog + snapshot before the app
 * starts, and the local store is enabled for it via system properties (set in the static
 * initializer, which runs before the {@code DropwizardAppRule} starts the app). The class-children
 * endpoint for a LOCALTEST class must then be answered from the snapshot, not BioPortal.
 *
 * Like the other tests here, this is a full-stack integration test: it inherits the authenticated
 * setup and therefore requires the CEDAR runtime (Neo4j-backed user resolution, a seeded test
 * user). It does not need BioPortal, since the request targets a locally served ontology.
 */
public class LocalStoreResourceTest extends AbstractTerminologyServerResourceTest {

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

      System.setProperty("cedar.terminology.catalogPath", catalog.toString());
      System.setProperty("cedar.terminology.localOntologies", ONT);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Test
  public void childrenServedFromLocalStore() {
    String classId = BASE + "cancer";
    String encoded = URLEncoder.encode(classId, StandardCharsets.UTF_8);
    String url = baseUrlBpOntologies + "/" + ONT + "/" + BP_CLASSES + "/" + encoded + "/" + BP_CHILDREN;

    Response response = clientBuilder.build().target(url).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();

    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    PagedResults<OntologyClass> children = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {
    });
    response.close();

    // "melanoma" is the only child of "cancer" in the local snapshot; served without BioPortal.
    Assert.assertEquals(Integer.valueOf(1), children.getTotalCount());
    OntologyClass child = children.getCollection().get(0);
    Assert.assertEquals(BASE + "melanoma", child.getLdId());
    Assert.assertEquals("Melanoma", child.getPrefLabel());
  }
}
