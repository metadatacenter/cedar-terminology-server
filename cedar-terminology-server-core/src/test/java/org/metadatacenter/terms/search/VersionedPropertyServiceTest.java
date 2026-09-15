package org.metadatacenter.terms.search;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.store.*;

class VersionedPropertyServiceTest {
  @TempDir Path dir;

  private SnapshotProperties.Property property(String label) {
    return new SnapshotProperties.Property("urn:p", "object", label, false, List.of(), List.of());
  }

  private String snapshot(CatalogStore catalog, String name, String label, boolean extracted)
      throws Exception {
    Path file = dir.resolve(name + ".sqlite");
    String version;
    try (SnapshotStore s = SnapshotStore.openFile(file.toString())) {
      s.initSchema();
      s.addConcept("urn:class", "Class", false, null);
      if (extracted) s.properties().write(List.of(property(label)));
      version = s.normalizedContentHash(true);
    }
    catalog.addSnapshot(
        new CatalogStore.SnapshotInfo(
            version,
            "EX",
            name,
            "2026-01-01",
            "2026-01-01",
            "OWL",
            "subsumption",
            1,
            0,
            file.toString(),
            name,
            "public"));
    return version;
  }

  @Test
  void classAndPropertyVersionResolutionIsSharedAndSearchDoesNotUseLiveBioPortal()
      throws Exception {
    try (CatalogStore c = CatalogStore.openFile(dir.resolve("catalog.sqlite").toString());
        SearchIndexStore i =
            SearchIndexStore.openFile(dir.resolve("search-index.sqlite").toString())) {
      c.initSchema();
      i.initSchema();
      c.upsertOntology(new CatalogStore.OntologyInfo("EX", "Example", null, "OWL"));
      String old = snapshot(c, "old", "Before", true),
          latest = snapshot(c, "new", "After", true),
          legacy = snapshot(c, "legacy", "", false);
      c.setTag("EX", "latest", latest);
      i.properties().replace("EX", latest, List.of(property("After")));
      try (CatalogSnapshotProvider provider = new CatalogSnapshotProvider(c, Set.of("EX"))) {
        var service = new VersionedPropertyService(provider, i);
        assertEquals(old, service.find("EX", old, "urn:p", "object").versionId());
        assertEquals("Before", service.find("EX", old, "urn:p", "object").property().label());
        assertEquals(latest, service.find("EX", null, "urn:p", "object").versionId());
        assertEquals(
            1,
            service
                .search(new VersionedPropertyService.Request("After", null, null, null, null))
                .total());
        assertEquals(
            0,
            service
                .search(new VersionedPropertyService.Request("Before", null, null, null, null))
                .total());
        assertEquals(
            1,
            service
                .search(
                    new VersionedPropertyService.Request(
                        "Before",
                        List.of(new VersionedPropertyService.Source("EX", old)),
                        null,
                        null,
                        null))
                .total());
        assertThrows(
            IllegalStateException.class, () -> service.find("EX", legacy, "urn:p", "object"));
        assertThrows(
            VersionedPropertyService.NotHeld.class,
            () -> service.find("EX", "missing", "urn:p", "object"));
        assertThrows(
            VersionedPropertyService.NotHeld.class,
            () -> service.find("OTHER", null, "urn:p", "object"));
        assertEquals(3, service.versions("EX").size());
      }
    }
  }
}
