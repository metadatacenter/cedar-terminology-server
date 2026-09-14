package org.metadatacenter.terms.store;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class SnapshotPropertiesTest {
  private SnapshotProperties.Property p(String iri, String label, String... parents) {
    return new SnapshotProperties.Property(
        iri, "object", label, false, List.of(), List.of(parents));
  }

  @Test
  void propertyChangesAffectTheSameOntologyHashAndLegacyHashIsStable() throws Exception {
    try (SnapshotStore old = SnapshotStore.openInMemory();
        SnapshotStore first = SnapshotStore.openInMemory();
        SnapshotStore second = SnapshotStore.openInMemory()) {
      for (var s : List.of(old, first, second)) {
        s.initSchema();
        s.addConcept("urn:class", "Class", false, null);
      }
      String legacy = old.normalizedContentHash(true);
      first.properties().write(List.of(p("urn:p", "First")));
      second.properties().write(List.of(p("urn:p", "Second")));
      assertEquals(legacy, old.normalizedContentHash(true));
      assertFalse(old.properties().available());
      assertNotEquals(legacy, first.normalizedContentHash(true));
      assertNotEquals(first.normalizedContentHash(true), second.normalizedContentHash(true));
      assertThrows(IllegalStateException.class, () -> old.properties().property("urn:p", "object"));
      assertEquals("Class", first.allConceptsDetailed().get(0).prefLabel());
    }
  }

  @Test
  void multipleParentsAndCyclesAreRetainedAndQueriesTerminate() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.properties()
          .write(
              List.of(
                  p("urn:a", "A", "urn:b", "urn:c"), p("urn:b", "B", "urn:a"), p("urn:c", "C")));
      var properties = s.properties();
      assertEquals(
          List.of("urn:b", "urn:c"),
          properties.property("urn:a", "object").orElseThrow().parents());
      assertEquals(
          Set.of("urn:b", "urn:c"),
          new HashSet<>(
              properties.ancestors("urn:a", "object").stream()
                  .map(SnapshotProperties.Summary::iri)
                  .toList()));
      assertEquals("urn:a", properties.children("urn:c", "object", 0, 50).get(0).iri());
      assertEquals("urn:c", properties.roots("object", 0, 50).get(0).iri());
      assertTrue(properties.roots("object", 0, 50).get(0).hasChildren());
    }
  }

  @Test
  void contentHashIgnoresInsertionOrderButIncludesParentAndLiteralChanges() throws Exception {
    try (SnapshotStore a = SnapshotStore.openInMemory();
        SnapshotStore b = SnapshotStore.openInMemory()) {
      a.initSchema();
      b.initSchema();
      a.properties().write(List.of(p("urn:a", "A", "urn:b", "urn:c"), p("urn:b", "B")));
      b.properties().write(List.of(p("urn:b", "B"), p("urn:a", "A", "urn:c", "urn:b")));
      assertEquals(a.normalizedContentHash(true), b.normalizedContentHash(true));
    }
  }

  @Test
  void searchIsPagedAndAcceptsUrisAndLiteralQueryPunctuation() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      s.properties()
          .write(List.of(p("urn:a", "has part"), p("urn:b", "has part"), p("urn:c", "has part")));
      assertEquals(3, s.properties().search("has", List.of(), 1, 1).total());
      assertEquals("urn:b", s.properties().search("has", List.of(), 1, 1).items().get(0).iri());
      assertEquals(1, s.properties().search("urn:b", List.of(), 0, 20).total());
      assertEquals(3, s.properties().search("\"has\"*", List.of(), 0, 20).total());
      assertThrows(
          IllegalArgumentException.class, () -> s.properties().search("***", List.of(), 0, 20));
    }
  }

  @Test
  void extractionIsAtomicAndEmptyIsDistinctFromNotExtracted() throws Exception {
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      String before = s.normalizedContentHash(true);
      assertThrows(
          java.sql.SQLException.class,
          () -> s.properties().write(List.of(p("urn:a", "A"), p("urn:a", "Duplicate"))));
      assertFalse(s.properties().available());
      assertEquals(before, s.normalizedContentHash(true));
      s.properties().write(List.of());
      assertTrue(s.properties().available());
      assertEquals(0, s.properties().count());
      assertNotEquals(before, s.normalizedContentHash(true));
    }
  }
}
