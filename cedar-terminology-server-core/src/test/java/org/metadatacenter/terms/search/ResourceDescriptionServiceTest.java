package org.metadatacenter.terms.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.store.*;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceDescriptionServiceTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  @TempDir Path dir;

  @Test void unknownProviderNeedsOnlyIngestedConfigurationAndKeepsEachSetSeparate() throws Exception {
    try (CatalogStore catalog = CatalogStore.openFile(dir.resolve("catalog.sqlite").toString())) {
      catalog.initSchema();
      Path file = dir.resolve("snapshot.sqlite");
      var profile = new SourcePresentation("future-registry", "Future registry",
          Map.of("class", new SourcePresentation.LinkPattern(
              "https://future.example/releases/{version.id}/record?iri={termIri}", "https://future.example/icon.svg")),
          Map.of("urn:set:one", new SourcePresentation.LinkPattern("https://future.example/lists/1", null),
              "urn:set:two", new SourcePresentation.LinkPattern("https://future.example/lists/2", null)));
      try (SnapshotStore store = SnapshotStore.openFile(file.toString())) {
        store.initSchema();
        store.addConcept("urn:term:a", "Original label", true, "urn:term:b");
        store.addConcept("urn:set:one", "First list"); store.addConcept("urn:set:two", "Second list");
        store.addEdge("urn:term:a", "urn:set:one", "member"); store.addEdge("urn:term:a", "urn:set:two", "member");
        store.materialize();
        String before = store.normalizedContentHash(true);
        store.setMeta(SourcePresentation.META_KEY, JSON.writeValueAsString(profile));
        assertEquals(before, store.normalizedContentHash(true), "public links must not change semantic identity");
      }
      catalog.upsertOntology(new CatalogStore.OntologyInfo("FUTURE", "Future collection", null, "custom"));
      catalog.addSnapshot(new CatalogStore.SnapshotInfo("hash-v1", "FUTURE", "v1", "2026-01-01", "2026-01-02",
          "custom", "membership", 3, 2, file.toString(), "raw", "public"));
      catalog.setTag("FUTURE", CatalogStore.TAG_LATEST, "hash-v1");
      try (var provider = new CatalogSnapshotProvider(catalog, Set.of("FUTURE"))) {
        var service = new ResourceDescriptionService(provider);
        var term = service.describe(ref("class", "urn:term:a", "hash-v1"));
        assertEquals("hash-v1", term.constraint().version().id());
        assertEquals("https://future.example/releases/hash-v1/record?iri=urn%3Aterm%3Aa", term.links().landingPage());
        assertEquals("hash-v1", term.constraint().version().id());
        assertValid("ResourceDescription", term);
        var first = service.describe(ref("valueSet", "urn:set:one", null));
        var second = service.describe(ref("valueSet", "urn:set:two", null));
        assertEquals("https://future.example/lists/1", first.links().landingPage());
        assertEquals("https://future.example/lists/2", second.links().landingPage());
        assertValid("ResourceDescription", first); assertValid("ResourceDescription", second);
        assertThrows(ResourceDescriptionService.NotFoundException.class,
            () -> service.describe(ref("class", "urn:term:a", "missing-pin")));
        assertThrows(ResourceDescriptionService.NotFoundException.class,
            () -> service.describe(ref("class", "urn:missing", "hash-v1")));
        assertThrows(ResourceDescriptionService.NotFoundException.class,
            () -> service.describe(new ResourceDescription.Reference("class", "wrong", "FUTURE", null,
                "urn:term:a", null, new SearchResponse.VersionInfo("hash-v1", null, null))));
      }
    }
  }

  @Test void validatesLinksAndDoesNotInventPagesForUnknownProviders() throws Exception {
    for (String href : List.of("javascript:alert(1)", "/relative", "https://user:secret@example.org/",
        "https://example.org/{unknown}", "https://example.org/{resourceIri}", "{resourceIriRaw}",
        "https://example.org/{versionId}")) {
      var profile = new SourcePresentation("future", "Future", Map.of("class", new SourcePresentation.LinkPattern(href, null)), Map.of());
      assertEquals(null, ResourceDescriptionService.landingPage(profile, "class", "F", "urn:term:a", "v1"));
    }
    var international = new SourcePresentation("future", "Future", Map.of("class", new SourcePresentation.LinkPattern("https://example.org/café", null)), Map.of());
    var page = ResourceDescriptionService.landingPage(international, "class", "F", "urn:term:猫", "v1");
    assertEquals("https://example.org/caf%C3%A9", page.landingPage());
    assertValid("ConstraintLinks", page);
    var service = new ResourceDescriptionService(null);
    assertThrows(ResourceDescriptionService.NotFoundException.class,
        () -> service.describe(ref("class", "urn:term:a", null)));
    var legacy = service.describe(new ResourceDescription.Reference("branch", "bioportal", "A/B?x=1", null,
        null, "urn:term:a#b", null));
    assertEquals("https://bioportal.bioontology.org/ontologies/A%2FB%3Fx%3D1?p=classes&conceptid=urn%3Aterm%3Aa%23b", legacy.links().landingPage());
    assertValid("ResourceDescription", legacy);
    assertThrows(ResourceDescriptionService.NotFoundException.class,
        () -> service.describe(new ResourceDescription.Reference("class", "bioportal", "EX", null,
            "urn:term:a", null, new SearchResponse.VersionInfo("missing", null, null))));
  }

  @Test void linksUseTheCoreModelSchemaAndDoNotCarryVersions() throws Exception {
    var page = new org.metadatacenter.artifacts.model.core.fields.constraints.ConstraintLinks(
        "https://example.org/term", "https://example.org/icon.svg");
    assertValid("ConstraintLinks", page);
    for (String extra : List.of("epa", "bioportal", "capabilities", "version", "versionScope", "label")) {
      ObjectNode extended = JSON.valueToTree(page);
      extended.put(extra, "unexpected");
      assertFalse(TerminologyLinksContract.schema("ConstraintLinks").validate(extended).isEmpty());
      assertThrows(IllegalArgumentException.class,
          () -> org.metadatacenter.model.validation.ConstraintLinksValidation.validate(extended));
    }
    try (var in = org.metadatacenter.model.validation.ConstraintLinksValidation.class.getResourceAsStream("/iri-field-meta-schema.json");
         var contract = getClass().getResourceAsStream("/contracts/terminology-links.schema.json")) {
      assertEquals(JSON.readTree(in).path("definitions").path("valueConstraintsLinksFieldContent"),
          JSON.readTree(contract).path("$defs").path("ConstraintLinks"));
    }
  }

  @Test void obsoleteStoredProfilesAreRejectedWithoutTranslation() throws Exception {
    try (var store = SnapshotStore.openFile(dir.resolve("legacy.sqlite").toString())) {
      store.initSchema();
      for (String encoded : List.of(
          "{\"sourceSystem\":\"epa\",\"links\":{\"class\":[{\"rel\":\"landing\",\"href\":\"{termIriRaw}\",\"label\":\"CompTox\",\"versionScope\":\"current\"}]}}",
          "{\"sourceSystem\":\"epa\",\"links\":{\"class\":{\"href\":\"{termIriRaw}\"}}}",
          "{\"sourceSystem\":\"epa\",\"links\":{},\"capabilities\":{}}")) {
        store.setMeta(SourcePresentation.META_KEY, encoded);
        assertThrows(java.sql.SQLException.class, () -> ResourceDescriptionService.presentation(store));
        assertEquals(encoded, store.getMeta(SourcePresentation.META_KEY).orElseThrow());
      }
    }
  }

  @Test void obsoleteResourceTypeAliasesAreNotResolved() {
    var pattern = new SourcePresentation.LinkPattern("https://example.org/term", null);
    var profile = new SourcePresentation("future", "Future",
        Map.of("term", pattern, "vocabulary", pattern), Map.of());
    assertNull(ResourceDescriptionService.landingPage(profile, "class", "F", "urn:term:a", "v1"));
    assertNull(ResourceDescriptionService.landingPage(profile, "ontology", "F", "urn:source", "v1"));
  }

  @Test void identityUsesOnlyCedarConstraintNamesAndItsVersionObject() throws Exception {
    for (String type : List.of("class", "branch", "ontology", "valueSet", "property")) {
      ObjectNode reference = JSON.createObjectNode().put("type", type).put("sourceAcronym", "EX");
      if (Set.of("class", "property").contains(type)) reference.put("termIri", "urn:term:a");
      if (Set.of("branch", "valueSet").contains(type)) reference.put("termBaseIri", "urn:set:a");
      reference.putObject("version").put("id", "hash-v1");
      assertEquals("hash-v1", TerminologyLinksContract.readReference(reference).version().id());
      reference.put("version", "latest");
      assertNull(TerminologyLinksContract.readReference(reference).version());
      for (String extra : List.of("kind", "resourceIri", "versionId", "epa")) {
        ObjectNode extended = reference.deepCopy().put(extra, "custom");
        assertThrows(IllegalArgumentException.class, () -> TerminologyLinksContract.readReference(extended));
      }
    }
    var blankPin = JSON.createObjectNode().put("type", "ontology").put("sourceAcronym", "EX");
    blankPin.putObject("version").put("id", "  ");
    assertThrows(IllegalArgumentException.class, () -> TerminologyLinksContract.readReference(blankPin),
        "A blank pin must not be interpreted as latest");
    assertThrows(IllegalArgumentException.class, () -> TerminologyLinksContract.readReference(null));
    assertThrows(IllegalArgumentException.class, () -> TerminologyLinksContract.readReference(
        JSON.readTree("{\"type\":\"term\",\"sourceAcronym\":\"EX\",\"termIri\":\"urn:a\"}")));
  }

  @Test void referencesKeepInternationalIdentifiersWithoutRewritingThem() {
    for (String iri : List.of("urn:term:猫", "https://example.org/café")) {
      var reference = JSON.createObjectNode().put("type", "class").put("sourceAcronym", "EX")
          .put("sourceIri", "urn:source:例").put("termIri", iri);
      assertEquals(iri, TerminologyLinksContract.readReference(reference).termIri());
    }
    for (String invalid : List.of("/relative", "urn:broken%zz", "https://example.org/a b")) {
      var reference = JSON.createObjectNode().put("type", "class").put("sourceAcronym", "EX").put("termIri", invalid);
      assertThrows(IllegalArgumentException.class, () -> TerminologyLinksContract.readReference(reference));
    }
  }

  private static void assertValid(String type, Object value) {
    var errors = TerminologyLinksContract.schema(type).validate(JSON.valueToTree(value));
    assertTrue(errors.isEmpty(), errors.toString());
  }

  private static ResourceDescription.Reference ref(String type, String iri, String version) {
    boolean set = Set.of("branch", "valueSet").contains(type);
    return new ResourceDescription.Reference(type, "future-registry", "FUTURE", null,
        set ? null : iri, set ? iri : null, version == null ? null : new SearchResponse.VersionInfo(version, null, null));
  }
}
