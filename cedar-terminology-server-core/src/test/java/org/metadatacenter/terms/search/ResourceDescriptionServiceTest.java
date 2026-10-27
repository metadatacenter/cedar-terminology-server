package org.metadatacenter.terms.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.store.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceDescriptionServiceTest {
  @TempDir Path dir;

  @Test void unknownProviderNeedsOnlyIngestedConfigurationAndKeepsEachSetSeparate() throws Exception {
    try (CatalogStore catalog = CatalogStore.openFile(dir.resolve("catalog.sqlite").toString())) {
      catalog.initSchema();
      Path file = dir.resolve("snapshot.sqlite");
      var profile = new SourcePresentation("future-registry", "Future registry",
          Map.of("term", List.of(new SourcePresentation.LinkPattern("landing",
              "https://future.example/releases/{versionId}/record?iri={resourceIri}", "Future registry", "exact"))),
          Map.of("urn:set:one", List.of(new SourcePresentation.LinkPattern("landing", "https://future.example/lists/1", "First list", "current")),
              "urn:set:two", List.of(new SourcePresentation.LinkPattern("landing", "https://future.example/lists/2", "Second list", "current"))),
          Map.of("valueSet", List.of("members"), "term", List.of("versions")));
      try (SnapshotStore store = SnapshotStore.openFile(file.toString())) {
        store.initSchema();
        store.addConcept("urn:term:a", "Original label", true, "urn:term:b");
        store.addConcept("urn:set:one", "First list"); store.addConcept("urn:set:two", "Second list");
        store.addEdge("urn:term:a", "urn:set:one", "member"); store.addEdge("urn:term:a", "urn:set:two", "member");
        store.materialize();
        String before = store.normalizedContentHash(true);
        store.setMeta(SourcePresentation.META_KEY, new ObjectMapper().writeValueAsString(profile));
        assertEquals(before, store.normalizedContentHash(true), "public links must not change semantic identity");
      }
      catalog.upsertOntology(new CatalogStore.OntologyInfo("FUTURE", "Future collection", null, "custom"));
      catalog.addSnapshot(new CatalogStore.SnapshotInfo("hash-v1", "FUTURE", "v1", "2026-01-01", "2026-01-02",
          "custom", "membership", 3, 2, file.toString(), "raw", "public"));
      catalog.setTag("FUTURE", CatalogStore.TAG_LATEST, "hash-v1");
      try (var provider = new CatalogSnapshotProvider(catalog, Set.of("FUTURE"))) {
        var service = new ResourceDescriptionService(provider);
        var term = service.describe(ref("term", "urn:term:a", "hash-v1"), null);
        assertEquals("deprecated", term.status()); assertEquals("urn:term:b", term.replacedBy());
        assertEquals("Original label", term.label()); assertEquals("hash-v1", term.ref().versionId());
        assertEquals("https://future.example/releases/hash-v1/record?iri=urn%3Aterm%3Aa", term.links().get(0).href());
        assertEquals("exact", term.links().get(0).versionScope());
        var first = service.describe(ref("valueSet", "urn:set:one", null), null);
        var second = service.describe(ref("valueSet", "urn:set:two", null), null);
        assertEquals("https://future.example/lists/1", first.links().get(0).href());
        assertEquals("https://future.example/lists/2", second.links().get(0).href());
        assertEquals(List.of("members"), first.capabilities());
        var absent = service.describe(ref("term", "urn:term:a", "missing-pin"), null);
        assertEquals("unavailable", absent.status()); assertEquals("versionNotHeld", absent.reason());
        assertTrue(absent.links().isEmpty());
        assertEquals("resourceNotHeld", service.describe(ref("term", "urn:missing", "hash-v1"), null).reason());
        assertEquals("sourceMismatch", service.describe(new ResourceDescription.Reference("term", "wrong",
            "FUTURE", null, "urn:term:a", "hash-v1"), null).reason());
      }
    }
  }

  @Test void validatesLinksAndDoesNotInventPagesForUnknownProviders() throws Exception {
    for (String href : List.of("javascript:alert(1)", "/relative", "https://user:secret@example.org/", "https://example.org/{unknown}")) {
      var profile = new SourcePresentation("future", "Future", Map.of("term", List.of(
          new SourcePresentation.LinkPattern("landing", href, "Read more", "current"))), Map.of(), Map.of());
      assertTrue(ResourceDescriptionService.links(profile, "term", "F", "urn:term:a", "v1").isEmpty());
    }
    var service = new ResourceDescriptionService(null);
    assertTrue(service.describe(ref("term", "urn:term:a", null), null).links().isEmpty());
    var legacy = service.describe(new ResourceDescription.Reference("branch", "bioportal", "A/B?x=1", null,
        "urn:term:a#b", null), null);
    assertEquals("unverified", legacy.status());
    assertEquals("https://bioportal.bioontology.org/ontologies/A%2FB%3Fx%3D1?p=classes&conceptid=urn%3Aterm%3Aa%23b", legacy.links().get(0).href());
    assertTrue(service.describe(new ResourceDescription.Reference("term", "bioportal", "EX", null,
        "urn:term:a", "missing"), null).links().isEmpty());
  }

  private static ResourceDescription.Reference ref(String kind, String iri, String version) {
    return new ResourceDescription.Reference(kind, "future-registry", "FUTURE", null, iri, version);
  }
}
