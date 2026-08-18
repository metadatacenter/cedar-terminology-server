package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds a small OWL ontology in memory and verifies extraction:
 *
 * <pre>
 *   animal (root)        pet (root)
 *      |                   |
 *   mammal                 |
 *    /   \                 |
 *  cat   dog --------------+   (dog subClassOf mammal AND pet)
 *
 *  plus:  dog subClassOf (part_of some animal)   <-- anonymous, must be ignored
 * </pre>
 */
public class OwlHierarchyExtractorTest {

  private static final String BASE = "http://ex/";

  private SnapshotStore store;
  private OWLOntology ont;

  private static IRI iri(String s) {
    return IRI.create(BASE + s);
  }

  @BeforeEach
  public void setUp() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    ont = m.createOntology(IRI.create(BASE + "test"));

    OWLClass animal = df.getOWLClass(iri("animal"));
    OWLClass mammal = df.getOWLClass(iri("mammal"));
    OWLClass cat = df.getOWLClass(iri("cat"));
    OWLClass dog = df.getOWLClass(iri("dog"));
    OWLClass pet = df.getOWLClass(iri("pet"));
    OWLObjectProperty partOf = df.getOWLObjectProperty(iri("part_of"));

    m.addAxiom(ont, df.getOWLSubClassOfAxiom(mammal, animal));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(cat, mammal));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(dog, mammal));
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(dog, pet));
    // Anonymous superclass — must NOT become a hierarchy edge:
    m.addAxiom(ont, df.getOWLSubClassOfAxiom(dog, df.getOWLObjectSomeValuesFrom(partOf, animal)));
    // Labels:
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), dog.getIRI(), df.getOWLLiteral("Dog")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), cat.getIRI(), df.getOWLLiteral("Cat")));
    // UMLS/TTL style: animal is labeled only with skos:prefLabel (fallback); mammal carries both,
    // and rdfs:label must win.
    OWLAnnotationProperty skosPref =
        df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel"));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(skosPref, animal.getIRI(), df.getOWLLiteral("Animal")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), mammal.getIRI(), df.getOWLLiteral("Mammal")));
    m.addAxiom(ont, df.getOWLAnnotationAssertionAxiom(skosPref, mammal.getIRI(), df.getOWLLiteral("Mammal SKOS")));

    store = SnapshotStore.openInMemory();
    store.initSchema();
  }

  @AfterEach
  public void tearDown() throws Exception {
    store.close();
  }

  @Test
  public void extract_countsClassesAndNamedEdgesOnly() throws Exception {
    OwlHierarchyExtractor.Result r = new OwlHierarchyExtractor().extract(ont, store);
    assertEquals(5, r.classCount());  // animal, mammal, cat, dog, pet (part_of is a property, not a class)
    assertEquals(4, r.edgeCount());   // the anonymous part_of restriction is excluded
  }

  @Test
  public void hierarchyIsCorrectAfterExtraction() throws Exception {
    new OwlHierarchyExtractor().extract(ont, store);

    assertEquals(List.of(BASE + "animal", BASE + "pet"), store.roots());
    assertEquals(List.of(BASE + "mammal"), store.children(BASE + "animal"));
    assertEquals(List.of(BASE + "cat", BASE + "dog"), store.children(BASE + "mammal"));
    assertEquals(List.of(BASE + "mammal", BASE + "pet"), store.parents(BASE + "dog"));
    assertEquals(List.of(BASE + "cat", BASE + "dog", BASE + "mammal"), store.descendants(BASE + "animal"));
  }

  @Test
  public void anonymousSuperclassDoesNotCreateAConcept() throws Exception {
    new OwlHierarchyExtractor().extract(ont, store);
    // dog's only parents are the two named classes; no synthetic restriction node
    assertEquals(2, store.parents(BASE + "dog").size());
    assertTrue(store.contains(BASE + "dog"));
  }

  @Test
  public void labelsAreCaptured() throws Exception {
    new OwlHierarchyExtractor().extract(ont, store);
    assertEquals("Dog", store.prefLabel(BASE + "dog").orElseThrow());
    assertEquals("Cat", store.prefLabel(BASE + "cat").orElseThrow());
    // skos:prefLabel is used when there is no rdfs:label (UMLS/TTL ontologies label this way);
    assertEquals("Animal", store.prefLabel(BASE + "animal").orElseThrow());
    // but rdfs:label wins when a concept carries both.
    assertEquals("Mammal", store.prefLabel(BASE + "mammal").orElseThrow());
  }

  @Test
  public void englishLabelPreferredWhenAClassIsLabelledInSeveralLanguages() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "multilang"));
    OWLClass disease = df.getOWLClass(iri("disease"));
    m.addAxiom(o, df.getOWLDeclarationAxiom(disease));
    // Same term labelled Japanese-first then English (as NANDO does). BioPortal serves the English
    // label; the extractor must too, regardless of assertion order.
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), disease.getIRI(),
        df.getOWLLiteral("せんてんせいこつずいふぜんしょうこうぐん", "ja")));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), disease.getIRI(),
        df.getOWLLiteral("Congenital bone marrow failure syndrome", "en")));
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s);
      assertEquals("Congenital bone marrow failure syndrome", s.prefLabel(BASE + "disease").orElseThrow());
    }
  }

  @Test
  public void everyLanguageVariantAndSynonymIsCapturedWhilePrefLabelIsUnchanged() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "names"));
    OWLClass water = df.getOWLClass(iri("water"));
    m.addAxiom(o, df.getOWLDeclarationAxiom(water));
    OWLAnnotationProperty altLabel =
        df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#altLabel"));
    OWLAnnotationProperty exactSynonym = df.getOWLAnnotationProperty(
        IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"));
    // French label first, then English (English must still win the single pref_label pick).
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), water.getIRI(), df.getOWLLiteral("eau", "fr")));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), water.getIRI(), df.getOWLLiteral("water", "en")));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(altLabel, water.getIRI(), df.getOWLLiteral("Wasser", "de")));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(exactSynonym, water.getIRI(), df.getOWLLiteral("H2O", "en")));

    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s);
      // The single served label is unchanged by capture: English rdfs:label still wins.
      assertEquals("water", s.prefLabel(BASE + "water").orElseThrow());
      // Every variant is preserved, each with its language tag and source property.
      List<SnapshotStore.LabelEntry> labels = s.labels(BASE + "water");
      assertEquals(4, labels.size());
      assertTrue(labels.contains(new SnapshotStore.LabelEntry("rdfs:label", "en", "water")));
      assertTrue(labels.contains(new SnapshotStore.LabelEntry("rdfs:label", "fr", "eau")));
      assertTrue(labels.contains(new SnapshotStore.LabelEntry("skos:altLabel", "de", "Wasser")));
      assertTrue(labels.contains(new SnapshotStore.LabelEntry("oboInOwl:hasExactSynonym", "en", "H2O")));
    }
  }

  @Test
  public void aListPackedIntoOneLiteralDoesNotBecomeTheName() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "lists"));
    OWLClass meningitis = df.getOWLClass(iri("meningitis"));
    OWLClass lcm = df.getOWLClass(iri("lcm"));
    m.addAxiom(o, df.getOWLDeclarationAxiom(meningitis));
    m.addAxiom(o, df.getOWLDeclarationAxiom(lcm));
    // ABD's shape: one class asserts its name and, as a second rdfs:label, a list of the kinds of
    // it — separated by line breaks, which a display collapses into one run-on line. The list was
    // winning the pick, and the padding on the real label was being served as part of the name.
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), meningitis.getIRI(),
        df.getOWLLiteral("Bacterial meningitis \nViral meningitis\nFungal meningitis")));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), meningitis.getIRI(),
        df.getOWLLiteral("Meningitis ")));
    // The other shape: no plain label to fall back on, so the first line is the name and the rest
    // become names of their own rather than being lost or run together.
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), lcm.getIRI(),
        df.getOWLLiteral("Lymphocytic choriomeningitis\nLCM \nArmstrong's disease")));

    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s);
      assertEquals("Meningitis", s.prefLabel(BASE + "meningitis").orElseThrow());
      assertEquals("Lymphocytic choriomeningitis", s.prefLabel(BASE + "lcm").orElseThrow());

      List<SnapshotStore.LabelEntry> names = s.labels(BASE + "lcm");
      assertEquals(3, names.size());
      assertTrue(names.contains(new SnapshotStore.LabelEntry("rdfs:label", "", "Lymphocytic choriomeningitis")));
      assertTrue(names.contains(new SnapshotStore.LabelEntry("rdfs:label", "", "LCM")));
      assertTrue(names.contains(new SnapshotStore.LabelEntry("rdfs:label", "", "Armstrong's disease")));
      // Each entry stands on its own, so none of them is stored as the run-on line.
      assertFalse(names.stream().anyMatch(n -> n.value().contains("\n")));
    }
  }

  @Test
  public void anEnglishListStillBeatsAPlainLabelInAnotherLanguage() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "langfirst"));
    OWLClass disease = df.getOWLClass(iri("disease"));
    m.addAxiom(o, df.getOWLDeclarationAxiom(disease));
    // Language decides before shape: BioPortal serves the English label, and diverging on which
    // language names a term is the larger error. The English list still reduces to its first line.
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), disease.getIRI(),
        df.getOWLLiteral("grippe", "fr")));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), disease.getIRI(),
        df.getOWLLiteral("Influenza\nFlu", "en")));
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s);
      assertEquals("Influenza", s.prefLabel(BASE + "disease").orElseThrow());
    }
  }

  @Test
  public void definitionsAreCapturedAndLeaveContentIdentityAlone() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "defs"));
    OWLClass disease = df.getOWLClass(iri("disease"));
    OWLClass plain = df.getOWLClass(iri("plain"));
    m.addAxiom(o, df.getOWLDeclarationAxiom(disease));
    m.addAxiom(o, df.getOWLDeclarationAxiom(plain));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), disease.getIRI(),
        df.getOWLLiteral("disease")));
    OWLAnnotationProperty iao = df.getOWLAnnotationProperty(
        IRI.create("http://purl.obolibrary.org/obo/IAO_0000115"));
    OWLAnnotationProperty skosDef = df.getOWLAnnotationProperty(
        IRI.create("http://www.w3.org/2004/02/skos/core#definition"));
    OWLAnnotationProperty comment = df.getOWLAnnotationProperty(df.getRDFSComment().getIRI());
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(iao, disease.getIRI(),
        df.getOWLLiteral("A disposition to undergo pathological processes.", "en")));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(skosDef, disease.getIRI(),
        df.getOWLLiteral("Une maladie.", "fr")));
    // An editorial note, not a definition — far commoner than either of the above, and addressed to
    // curators rather than to an author choosing a term.
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(comment, disease.getIRI(),
        df.getOWLLiteral("TODO: check with the editors.")));

    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s);
      s.materialize();

      List<SnapshotStore.DefinitionEntry> held = s.definitions(BASE + "disease");
      assertEquals(2, held.size());
      assertTrue(held.contains(new SnapshotStore.DefinitionEntry(
          "IAO:0000115", "en", "A disposition to undergo pathological processes.")));
      assertTrue(held.contains(new SnapshotStore.DefinitionEntry(
          "skos:definition", "fr", "Une maladie.")));
      // English first, and the OBO definition ahead of the rest, so a row shows one and the right one.
      assertEquals("A disposition to undergo pathological processes.",
          SnapshotStore.servedDefinition(held));
      // A concept that asserts none has none, rather than borrowing a neighbour's.
      assertTrue(s.definitions(BASE + "plain").isEmpty());
    }

    // The point of the whole design: a snapshot with definitions and one without hash identically,
    // so capturing them cannot change the identity of anything already in the store.
    try (SnapshotStore withDefs = SnapshotStore.openInMemory();
         SnapshotStore without = SnapshotStore.openInMemory()) {
      withDefs.initSchema();
      new OwlHierarchyExtractor().extract(o, withDefs);
      withDefs.materialize();

      OWLOntologyManager m2 = OWLManager.createOWLOntologyManager();
      OWLDataFactory df2 = m2.getOWLDataFactory();
      OWLOntology bare = m2.createOntology(IRI.create(BASE + "defs"));
      OWLClass d2 = df2.getOWLClass(iri("disease"));
      OWLClass p2 = df2.getOWLClass(iri("plain"));
      m2.addAxiom(bare, df2.getOWLDeclarationAxiom(d2));
      m2.addAxiom(bare, df2.getOWLDeclarationAxiom(p2));
      m2.addAxiom(bare, df2.getOWLAnnotationAssertionAxiom(df2.getRDFSLabel(), d2.getIRI(),
          df2.getOWLLiteral("disease")));
      without.initSchema();
      new OwlHierarchyExtractor().extract(bare, without);
      without.materialize();

      assertEquals(without.normalizedContentHash(true), withDefs.normalizedContentHash(true));
    }
  }

  @Test
  public void oboRelationshipIsAIsAlwaysAHierarchyEdge() throws Exception {
    // Some OBO ontologies (BSAO) write subsumption as `relationship: is_a X`, which obo2owl renders as
    // `is_a some X` on its TEMP# namespace rather than rdfs:subClassOf. The default extractor must still
    // treat it as a parent edge — no per-ontology config.
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "obois_a"));
    OWLClass child = df.getOWLClass(iri("child"));
    OWLClass parent = df.getOWLClass(iri("parent"));
    OWLObjectProperty isAtemp =
        df.getOWLObjectProperty(IRI.create("http://purl.obolibrary.org/obo/TEMP#is_a"));
    m.addAxiom(o, df.getOWLSubClassOfAxiom(child, df.getOWLObjectSomeValuesFrom(isAtemp, parent)));
    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s); // default extractor, no config
      assertEquals(List.of(BASE + "parent"), s.parents(BASE + "child"));
      assertEquals(List.of(BASE + "child"), s.children(BASE + "parent"));
    }
  }

  @Test
  public void configuredRelationRestrictionBecomesHierarchyEdge() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "partonomy"));
    OWLClass finger = df.getOWLClass(iri("finger"));
    OWLClass hand = df.getOWLClass(iri("hand"));
    IRI partOf = IRI.create(BASE + "part_of");
    // finger part_of hand  ->  SubClassOf(finger, part_of some hand)
    m.addAxiom(o, df.getOWLSubClassOfAxiom(finger,
        df.getOWLObjectSomeValuesFrom(df.getOWLObjectProperty(partOf), hand)));

    // Default extractor: the relation restriction is NOT a hierarchy edge.
    try (SnapshotStore plain = SnapshotStore.openInMemory()) {
      plain.initSchema();
      new OwlHierarchyExtractor().extract(o, plain);
      assertEquals(List.of(), plain.children(BASE + "hand"));
    }
    // Configured extractor: part_of makes hand a parent of finger.
    try (SnapshotStore configured = SnapshotStore.openInMemory()) {
      configured.initSchema();
      new OwlHierarchyExtractor(Set.of(partOf)).extract(o, configured);
      assertEquals(List.of(BASE + "finger"), configured.children(BASE + "hand"));
      assertEquals(List.of(BASE + "hand"), configured.parents(BASE + "finger"));
    }
  }

  @Test
  public void genusOfDefinitionBecomesParent() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "defs"));
    OWLClass assay = df.getOWLClass(iri("assay"));
    OWLClass input = df.getOWLClass(iri("input"));
    OWLObjectProperty hasInput = df.getOWLObjectProperty(iri("has_input"));

    // Defined class: mhcAssay ≡ assay AND (has_input some input)  — genus 'assay'.
    OWLClass mhcAssay = df.getOWLClass(iri("mhcAssay"));
    m.addAxiom(o, df.getOWLEquivalentClassesAxiom(mhcAssay,
        df.getOWLObjectIntersectionOf(assay, df.getOWLObjectSomeValuesFrom(hasInput, input))));
    // Genus-differentia via subClassOf-of-intersection: fancyAssay ⊑ assay AND (has_input some input).
    OWLClass fancyAssay = df.getOWLClass(iri("fancyAssay"));
    m.addAxiom(o, df.getOWLSubClassOfAxiom(fancyAssay,
        df.getOWLObjectIntersectionOf(assay, df.getOWLObjectSomeValuesFrom(hasInput, input))));
    // Plain equivalence between two named classes must NOT create a subsumption edge.
    OWLClass synonymA = df.getOWLClass(iri("synonymA"));
    OWLClass synonymB = df.getOWLClass(iri("synonymB"));
    m.addAxiom(o, df.getOWLEquivalentClassesAxiom(synonymA, synonymB));

    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s);
      assertEquals(List.of(BASE + "assay"), s.parents(BASE + "mhcAssay"));
      assertEquals(List.of(BASE + "assay"), s.parents(BASE + "fancyAssay"));
      // The restriction filler 'input' is not a parent, and the class is a descendant of the genus.
      assertFalse(s.parents(BASE + "mhcAssay").contains(BASE + "input"));
      assertTrue(s.descendants(BASE + "assay").contains(BASE + "mhcAssay"));
      // Named-to-named equivalence is not a subsumption edge.
      assertTrue(s.parents(BASE + "synonymA").isEmpty());
      assertTrue(s.parents(BASE + "synonymB").isEmpty());
    }
  }

  @Test
  public void subClassOfOwlThingBecomesARootNotAnEdge() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "thing"));
    OWLClass top = df.getOWLClass(iri("top"));
    OWLClass sub = df.getOWLClass(iri("sub"));
    m.addAxiom(o, df.getOWLSubClassOfAxiom(top, df.getOWLThing())); // explicit top -> root
    m.addAxiom(o, df.getOWLSubClassOfAxiom(sub, top));             // sub under top

    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s);
      // owl:Thing is neither a concept nor an edge; top is the sole root, sub is a descendant.
      assertFalse(s.contains("http://www.w3.org/2002/07/owl#Thing"));
      assertEquals(List.of(BASE + "top"), s.roots());
      assertTrue(s.descendants(BASE + "top").contains(BASE + "sub"));
    }
  }

  @Test
  public void capturesDeprecationAndReplacedBy() throws Exception {
    OWLOntologyManager m = OWLManager.createOWLOntologyManager();
    OWLDataFactory df = m.getOWLDataFactory();
    OWLOntology o = m.createOntology(IRI.create(BASE + "obs"));
    OWLClass oldTerm = df.getOWLClass(iri("oldterm"));
    OWLClass newTerm = df.getOWLClass(iri("newterm"));
    m.addAxiom(o, df.getOWLDeclarationAxiom(oldTerm));
    m.addAxiom(o, df.getOWLDeclarationAxiom(newTerm));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getOWLDeprecated(), oldTerm.getIRI(), df.getOWLLiteral(true)));
    m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(
        df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0100001")),
        oldTerm.getIRI(), iri("newterm")));

    try (SnapshotStore s = SnapshotStore.openInMemory()) {
      s.initSchema();
      new OwlHierarchyExtractor().extract(o, s);
      SnapshotStore.ConceptMeta old = s.allConceptMeta().stream()
          .filter(c -> c.iri().equals(BASE + "oldterm")).findFirst().orElseThrow();
      assertTrue(old.obsolete());
      assertEquals(BASE + "newterm", old.replacedBy());
      SnapshotStore.ConceptMeta neu = s.allConceptMeta().stream()
          .filter(c -> c.iri().equals(BASE + "newterm")).findFirst().orElseThrow();
      assertFalse(neu.obsolete());
    }
  }
}
