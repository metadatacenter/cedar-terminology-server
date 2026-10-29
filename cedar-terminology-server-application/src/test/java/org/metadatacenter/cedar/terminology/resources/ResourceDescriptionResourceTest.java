package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.search.ResourceDescriptionService;
import org.metadatacenter.terms.search.TerminologyLinksContract;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;
import org.metadatacenter.terms.store.SourcePresentation;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDescriptionResourceTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final ResourceDescriptionResource resource = new ResourceDescriptionResource();
  @TempDir Path dir;

  @Test void returnsTheClosedContractWithExplicitlyStoredLinks() throws Exception {
    Path file = dir.resolve("snapshot.sqlite");
    try (var store = SnapshotStore.openFile(file.toString())) {
      store.initSchema();
      store.addConcept("urn:term:a", "Term");
      store.setMeta(SourcePresentation.META_KEY,
          "{\"sourceSystem\":\"bioportal\",\"links\":{\"class\":{\"landingPage\":\"https://example.org/{termIri}\"}}}");
    }
    try (var catalog = CatalogStore.openFile(dir.resolve("catalog.sqlite").toString())) {
      catalog.initSchema();
      catalog.upsertOntology(new CatalogStore.OntologyInfo("EX", "Example", null, "OWL"));
      catalog.addSnapshot(new CatalogStore.SnapshotInfo("v1", "EX", null, null, null,
          "OWL", "subsumption", 1, 0, file.toString(), "raw", "public"));
      catalog.setTag("EX", CatalogStore.TAG_LATEST, "v1");
      try (var provider = new CatalogSnapshotProvider(catalog, Set.of("EX"))) {
        ResourceDescriptionResource.injectService(new ResourceDescriptionService(provider));
        try (var response = resource.describe(JSON.readTree(
            "{\"type\":\"class\",\"sourceSystem\":\"bioportal\",\"sourceAcronym\":\"EX\",\"termIri\":\"urn:term:a\"}"))) {
          assertEquals(200, response.getStatus());
          var body = JSON.valueToTree(response.getEntity());
          assertTrue(TerminologyLinksContract.schema("ResourceDescription").validate(body).isEmpty());
          assertEquals("https://example.org/urn%3Aterm%3Aa", body.path("links").path("landingPage").asText());
        } finally {
          ResourceDescriptionResource.injectService(new ResourceDescriptionService(null));
        }
      }
    }
  }

  @Test void rejectsExtensionsAndReturnsMissingPinsAsNormalHttpErrors() throws Exception {
    var reference = JSON.createObjectNode().put("type", "class").put("sourceSystem", "bioportal")
        .put("sourceAcronym", "EX").put("termIri", "urn:term:a");
    try (var response = resource.describe(reference.deepCopy().put("epa", "custom"))) {
      assertEquals(400, response.getStatus());
    }
    try (var response = resource.describe(reference)) {
      assertEquals(404, response.getStatus(), "unheld unpinned sources do not receive invented public links");
    }
    reference.putObject("version").put("id", "missing-release");
    try (var response = resource.describe(reference)) {
      assertEquals(404, response.getStatus());
    }
  }
}
