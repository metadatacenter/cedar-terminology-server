package org.metadatacenter.terms.ingest;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.store.*;

class PropertyBackfillJobTest {
  @TempDir Path dir;

  @Test
  void verifiesTheSourceBytesInsideArchiveCompression() throws Exception {
    Path plain = dir.resolve("source.owl");
    Files.writeString(plain, "original ontology source");
    String hash = PropertyBackfillJob.sha256(plain);
    Path compressed = dir.resolve(hash + ".gz");
    try (var out = new java.util.zip.GZIPOutputStream(Files.newOutputStream(compressed))) {
      Files.copy(plain, out);
    }
    assertEquals(hash, PropertyBackfillJob.sha256(compressed));
    assertEquals(compressed, PropertyBackfillJob.raw(dir, hash));
  }

  @Test
  void enrichmentPreservesPinnedFileAndRegistersCombinedSnapshot() throws Exception {
    Path raw = dir.resolve("raw.owl");
    Files.writeString(
        raw,
        """
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:owl="http://www.w3.org/2002/07/owl#">
          <owl:ObjectProperty rdf:about="urn:property"/>
        </rdf:RDF>
        """);
    Path original = dir.resolve("old.sqlite");
    String oldHash;
    try (SnapshotStore s = SnapshotStore.openFile(original.toString())) {
      s.initSchema();
      s.addConcept("urn:class", "Class", false, null);
      oldHash = s.normalizedContentHash(true);
    }
    byte[] originalBytes = Files.readAllBytes(original);
    try (CatalogStore c = CatalogStore.openFile(dir.resolve("catalog.sqlite").toString())) {
      c.initSchema();
      c.upsertOntology(new CatalogStore.OntologyInfo("EX", "Example", null, "OWL"));
      var old =
          new CatalogStore.SnapshotInfo(
              oldHash,
              "EX",
              "v1",
              "2026-01-01",
              "2026-02-01",
              "OWL",
              "subsumption",
              1,
              0,
              "old.sqlite",
              PropertyBackfillJob.sha256(raw),
              "public");
      c.addSnapshot(old);
      c.setTag("EX", "latest", oldHash);
      String combined = PropertyBackfillJob.enrich(c, old, raw);
      assertNotEquals(oldHash, combined);
      assertArrayEquals(originalBytes, Files.readAllBytes(original));
      assertEquals(combined, c.resolveLatest("EX").orElseThrow().versionId());
      assertTrue(c.resolveVersion("EX", oldHash).isPresent());
      assertEquals(combined, c.propertyEnrichment("EX", oldHash).orElseThrow());
      try (SnapshotStore s =
          SnapshotStore.openForRead(
              dir.resolve(c.resolveVersion("EX", combined).orElseThrow().filePath()).toString())) {
        assertEquals("Class", s.allConceptsDetailed().get(0).prefLabel());
        assertTrue(s.properties().property("urn:property", "object").isPresent());
        assertEquals(combined, s.normalizedContentHash(true));
      }
      Files.writeString(raw, "different bytes");
      assertThrows(IllegalArgumentException.class, () -> PropertyBackfillJob.enrich(c, old, raw));
    }
  }
}
