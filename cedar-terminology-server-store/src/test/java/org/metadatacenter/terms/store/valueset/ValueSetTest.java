package org.metadatacenter.terms.store.valueset;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Value sets over a synthetic snapshot: an is_a tree (disease > cancer > {melanoma, carcinoma})
 * plus a relation (aspirinTablet has_ingredient aspirin). Exercises the three definition kinds and
 * the store round-trip (persist definition, reload, expand).
 */
public class ValueSetTest {

  private static final String B = "http://ex/";
  private static final String HAS_INGREDIENT = "http://ex/has_ingredient";
  private static final String V1 = "v1";

  private Path tempDir;
  private CatalogStore catalog;
  private ValueSetStore valueSets;

  @Before
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("valueset-test");

    Path snapshot = tempDir.resolve("snap.sqlite");
    try (SnapshotStore s = SnapshotStore.openFile(snapshot.toString())) {
      s.initSchema();
      for (String c : new String[]{"disease", "cancer", "melanoma", "carcinoma", "aspirinTablet", "aspirin"}) {
        s.addConcept(B + c, c);
      }
      s.addEdge(B + "cancer", B + "disease", "isa");
      s.addEdge(B + "melanoma", B + "cancer", "isa");
      s.addEdge(B + "carcinoma", B + "cancer", "isa");
      s.addRelation(B + "aspirinTablet", HAS_INGREDIENT, B + "aspirin");
      s.materialize();
    }

    catalog = CatalogStore.openInMemory();
    catalog.initSchema();
    catalog.upsertOntology(new CatalogStore.OntologyInfo("EX", "Example", null, "OWL"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo(V1, "EX", "1.0", "2025-01-01", "2025-01-01T00:00:00Z",
        "OWL", "subsumption", 6, 3, snapshot.toString(), V1, "public"));

    valueSets = ValueSetStore.openInMemory();
    valueSets.initSchema();
  }

  @After
  public void tearDown() throws Exception {
    valueSets.close();
    catalog.close();
    if (tempDir != null) {
      try (var paths = Files.walk(tempDir)) {
        paths.sorted(Comparator.reverseOrder()).forEach(p -> {
          try {
            Files.deleteIfExists(p);
          } catch (IOException ignored) {
          }
        });
      }
    }
  }

  private List<String> expandStored(String id, String version) throws Exception {
    ValueSetDefinition def = valueSets.getDefinition(id, version).orElseThrow();
    try (ValueSetExpander expander = new ValueSetExpander(catalog)) {
      return expander.expand(def);
    }
  }

  @Test
  public void descendantsValueSet() throws Exception {
    valueSets.upsertValueSet("vs:cancers", "Cancers", "Kinds of cancer");
    valueSets.putVersion("vs:cancers", "1", ValueSetDefinition.descendants(V1, B + "cancer", false));
    assertEquals(List.of(B + "carcinoma", B + "melanoma"), expandStored("vs:cancers", "1"));
  }

  @Test
  public void descendantsIncludingRoot() throws Exception {
    valueSets.upsertValueSet("vs:cancers", "Cancers", null);
    valueSets.putVersion("vs:cancers", "1", ValueSetDefinition.descendants(V1, B + "cancer", true));
    assertEquals(List.of(B + "cancer", B + "carcinoma", B + "melanoma"), expandStored("vs:cancers", "1"));
  }

  @Test
  public void relationValueSet() throws Exception {
    valueSets.upsertValueSet("vs:aspirin-products", "Aspirin products", null);
    valueSets.putVersion("vs:aspirin-products", "1", ValueSetDefinition.relation(V1, HAS_INGREDIENT, B + "aspirin"));
    assertEquals(List.of(B + "aspirinTablet"), expandStored("vs:aspirin-products", "1"));
  }

  @Test
  public void extensionalValueSetDropsMissingMembers() throws Exception {
    valueSets.upsertValueSet("vs:picked", "Picked", null);
    valueSets.putVersion("vs:picked", "1", ValueSetDefinition.extensional(List.of(
        new PinnedConcept(V1, B + "melanoma"),
        new PinnedConcept(V1, B + "unicorn")))); // not in the snapshot -> dropped
    assertEquals(List.of(B + "melanoma"), expandStored("vs:picked", "1"));
  }

  @Test
  public void diffDescendantsAcrossVersions() throws Exception {
    // v2 of the ontology: carcinoma is gone, sarcoma is added under cancer.
    Path v2 = tempDir.resolve("snap2.sqlite");
    try (SnapshotStore s = SnapshotStore.openFile(v2.toString())) {
      s.initSchema();
      for (String c : new String[]{"disease", "cancer", "melanoma", "sarcoma"}) {
        s.addConcept(B + c, c);
      }
      s.addEdge(B + "cancer", B + "disease", "isa");
      s.addEdge(B + "melanoma", B + "cancer", "isa");
      s.addEdge(B + "sarcoma", B + "cancer", "isa");
      s.materialize();
    }
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("v2", "EX", "2.0", "2026-01-01", "2026-01-01T00:00:00Z",
        "OWL", "subsumption", 4, 3, v2.toString(), "v2", "public"));

    try (ValueSetExpander expander = new ValueSetExpander(catalog)) {
      ValueSetDiff d = expander.diff(
          ValueSetDefinition.descendants(V1, B + "cancer", false),
          ValueSetDefinition.descendants("v2", B + "cancer", false));
      assertEquals(2, d.fromCount()); // melanoma, carcinoma
      assertEquals(2, d.toCount());   // melanoma, sarcoma
      assertEquals(List.of(B + "sarcoma"), d.added());
      assertEquals(List.of(B + "carcinoma"), d.removed());
    }
  }

  @Test
  public void validateAgainstNewerVersionFlagsObsoleteAndRemoved() throws Exception {
    // v3: carcinoma is obsolete (replaced by sarcoma); melanoma is gone entirely.
    Path v3 = tempDir.resolve("snap3.sqlite");
    try (SnapshotStore s = SnapshotStore.openFile(v3.toString())) {
      s.initSchema();
      s.addConcept(B + "disease", "disease");
      s.addConcept(B + "cancer", "cancer");
      s.addConcept(B + "sarcoma", "sarcoma");
      s.addConcept(B + "carcinoma", "carcinoma (obsolete)", true, B + "sarcoma");
      s.materialize();
    }
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("v3", "EX", "3.0", "2027-01-01", "2027-01-01T00:00:00Z",
        "OWL", "subsumption", 4, 0, v3.toString(), "v3", "public"));

    try (ValueSetExpander expander = new ValueSetExpander(catalog)) {
      // The value set was defined against v1 (members: melanoma, carcinoma).
      ValueSetValidation v = expander.validateAgainst(ValueSetDefinition.descendants(V1, B + "cancer", false), "v3");
      assertEquals(2, v.total());
      assertEquals(0, v.active());
      assertEquals(List.of(B + "carcinoma => " + B + "sarcoma"), v.obsoleted());
      assertEquals(List.of(B + "melanoma"), v.removed());
      assertFalse(v.isClean());
    }
  }

  @Test
  public void descendantsDefinitionRoundTrips() throws Exception {
    ValueSetDefinition def = ValueSetDefinition.descendants(V1, B + "cancer", true);
    valueSets.upsertValueSet("vs:d", "D", null);
    valueSets.putVersion("vs:d", "1", def);
    assertEquals(def, valueSets.getDefinition("vs:d", "1").orElseThrow());
  }

  @Test
  public void extensionalDefinitionRoundTrips() throws Exception {
    // members given in concept-iri order so the reloaded (ORDER BY concept_iri) list matches
    ValueSetDefinition def = ValueSetDefinition.extensional(List.of(
        new PinnedConcept(V1, B + "aspirin"),
        new PinnedConcept(V1, B + "melanoma")));
    valueSets.upsertValueSet("vs:e", "E", "picked");
    valueSets.putVersion("vs:e", "1", def);
    assertEquals(def, valueSets.getDefinition("vs:e", "1").orElseThrow());
  }

  @Test
  public void definitionRoundTrips() throws Exception {
    valueSets.upsertValueSet("vs:x", "X", null);
    valueSets.putVersion("vs:x", "1", ValueSetDefinition.relation(V1, HAS_INGREDIENT, B + "aspirin"));
    ValueSetDefinition def = valueSets.getDefinition("vs:x", "1").orElseThrow();
    assertEquals(ValueSetDefinition.Kind.RELATION, def.kind());
    assertEquals(HAS_INGREDIENT, def.predicate());
    assertEquals(B + "aspirin", def.objectIri());
    assertEquals(List.of("1"), valueSets.listVersions("vs:x"));
  }
}
