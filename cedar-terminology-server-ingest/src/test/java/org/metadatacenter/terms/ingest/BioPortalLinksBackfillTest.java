package org.metadatacenter.terms.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;
import org.metadatacenter.terms.store.SourcePresentation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BioPortalLinksBackfillTest {
  @TempDir Path dir;
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test void plansThenBackfillsOnlyMissingBioPortalProfilesWithoutChangingContentOrPins() throws Exception {
    Path catalogPath = dir.resolve("catalog.sqlite");
    Path journal = dir.resolve("changes.jsonl");
    Map<String, String> hashes = new HashMap<>();
    try (var catalog = CatalogStore.openFile(catalogPath.toString())) {
      catalog.initSchema();
      for (String name : List.of("BP", "EXISTING", "OTHER")) {
        hashes.put(name, add(catalog, name, name.equals("OTHER") ? "ols" : "bioportal"));
      }
      try (var store = SnapshotStore.openFile(dir.resolve("EXISTING.sqlite").toString())) {
        store.setMeta(SourcePresentation.META_KEY, "keep-exactly-even-if-invalid");
      }
    }
    byte[] catalogBefore = Files.readAllBytes(catalogPath);
    byte[] before = Files.readAllBytes(dir.resolve("BP.sqlite"));
    byte[] other = Files.readAllBytes(dir.resolve("OTHER.sqlite"));
    var expected = new BioPortalLinksBackfill.Result(1, 1, 1);
    assertEquals(expected, BioPortalLinksBackfill.run(catalogPath, null));
    assertArrayEquals(before, Files.readAllBytes(dir.resolve("BP.sqlite")));
    assertFalse(Files.exists(journal));
    assertEquals(expected, BioPortalLinksBackfill.run(catalogPath, journal));
    assertArrayEquals(catalogBefore, Files.readAllBytes(catalogPath), "catalog versions and tags stay byte-identical");
    assertArrayEquals(other, Files.readAllBytes(dir.resolve("OTHER.sqlite")));
    for (var entry : hashes.entrySet()) {
      try (var store = SnapshotStore.openForRead(dir.resolve(entry.getKey() + ".sqlite").toString())) {
        assertEquals(entry.getValue(), store.normalizedContentHash(true));
        assertEquals("retained", store.getMeta("unrelated").orElseThrow());
        switch (entry.getKey()) {
          case "BP" -> assertEquals(BioPortalDownloader.publicLinks(),
              JSON.readValue(store.getMeta(SourcePresentation.META_KEY).orElseThrow(), SourcePresentation.class));
          case "EXISTING" -> assertEquals("keep-exactly-even-if-invalid", store.getMeta(SourcePresentation.META_KEY).orElseThrow());
          case "OTHER" -> assertTrue(store.getMeta(SourcePresentation.META_KEY).isEmpty());
        }
      }
    }
    var records = Files.readAllLines(journal);
    assertEquals(2, records.size());
    assertEquals("planned", JSON.readTree(records.get(0)).path("status").asText());
    assertTrue(JSON.readTree(records.get(0)).path("previousValue").isNull());
    assertEquals("committed", JSON.readTree(records.get(1)).path("status").asText());
    assertEquals(new BioPortalLinksBackfill.Result(0, 2, 1), BioPortalLinksBackfill.run(catalogPath, journal));
    assertEquals(records, Files.readAllLines(journal), "reruns leave completed work alone");
  }

  @Test void refusesMissingOrSharedFilesBeforeMakingChanges() throws Exception {
    Path catalogPath = dir.resolve("catalog.sqlite");
    try (var catalog = CatalogStore.openFile(catalogPath.toString())) {
      catalog.initSchema();
      add(catalog, "BP", "bioportal");
      add(catalog, "MISSING", "bioportal");
    }
    Files.delete(dir.resolve("MISSING.sqlite"));
    assertThrows(java.io.IOException.class, () -> BioPortalLinksBackfill.run(catalogPath, dir.resolve("changes.jsonl")));
    assertFalse(Files.exists(dir.resolve("MISSING.sqlite")));
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + catalogPath);
         var statement = connection.createStatement()) {
      statement.execute("UPDATE snapshot SET file_path='BP.sqlite' WHERE acronym='MISSING'");
    }
    assertThrows(java.io.IOException.class, () -> BioPortalLinksBackfill.run(catalogPath, dir.resolve("changes.jsonl")));
    try (var store = SnapshotStore.openForRead(dir.resolve("BP.sqlite").toString())) {
      assertTrue(store.getMeta(SourcePresentation.META_KEY).isEmpty());
    }
  }

  @Test void refusesTriggersAndDoesNotWriteWhenTheUndoJournalCannotBeOpened() throws Exception {
    Path catalogPath = dir.resolve("catalog.sqlite");
    try (var catalog = CatalogStore.openFile(catalogPath.toString())) {
      catalog.initSchema();
      add(catalog, "BP", "bioportal");
    }
    assertThrows(java.io.IOException.class, () -> BioPortalLinksBackfill.run(catalogPath, dir));
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("BP.sqlite"));
         var statement = connection.createStatement()) {
      statement.execute("CREATE TRIGGER dangerous AFTER INSERT ON meta BEGIN UPDATE concept SET pref_label='changed'; END");
    }
    assertThrows(java.sql.SQLException.class, () -> BioPortalLinksBackfill.run(catalogPath, dir.resolve("changes.jsonl")));
    try (var store = SnapshotStore.openForRead(dir.resolve("BP.sqlite").toString())) {
      assertTrue(store.getMeta(SourcePresentation.META_KEY).isEmpty());
      assertEquals("Term", store.get("urn:term").orElseThrow().prefLabel());
    }
  }

  private String add(CatalogStore catalog, String acronym, String backend) throws Exception {
    Path file = dir.resolve(acronym + ".sqlite");
    String hash;
    try (var store = SnapshotStore.openFile(file.toString())) {
      store.initSchema();
      store.addConcept("urn:term", "Term");
      store.addConcept("urn:root", "Root");
      store.addEdge("urn:term", "urn:root", "rdfs:subClassOf");
      store.materialize();
      store.setMeta("unrelated", "retained");
      hash = store.normalizedContentHash(true);
    }
    catalog.upsertOntology(new CatalogStore.OntologyInfo(acronym, acronym, null, "OWL"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo(hash, acronym, "v1", "2026-01-01", "2026-01-02",
        "OWL", "subsumption", 2, 1, file.getFileName().toString(), "raw", "public"));
    catalog.setSnapshotBackend(hash, acronym, backend);
    catalog.setTag(acronym, CatalogStore.TAG_LATEST, hash);
    return hash;
  }
}
