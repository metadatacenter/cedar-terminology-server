package org.metadatacenter.terms.search;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.search.SearchRequest.SourceSelector;
import org.metadatacenter.terms.search.SearchRequest.VersionSelector;
import org.metadatacenter.terms.search.SearchResponse.BranchHit;
import org.metadatacenter.terms.search.SearchResponse.ClassHit;
import org.metadatacenter.terms.search.SearchResponse.OntologyHit;
import org.metadatacenter.terms.search.SearchResponse.SourceBlock;
import org.metadatacenter.terms.search.SearchResponse.TypeResults;
import org.metadatacenter.terms.search.SearchResponse.ValueSetHit;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Version-aware search over a catalog holding two versions of an ontology and one value-set
 * collection.
 *
 * The fixture is deliberately small enough to assert exact results: what a source block says about
 * the version that answered is as much the subject of these tests as the hits are.
 */
public class VersionAwareSearchServiceTest {

  private static final String BASE = "http://ex/";
  private static final String VS = "http://vs/";

  private Path tempDir;
  private CatalogStore catalog;
  private CatalogSnapshotProvider provider;
  private VersionAwareSearchService service;

  @BeforeEach
  public void setUp() throws Exception {
    tempDir = Files.createTempDirectory("version-aware-search-test");

    // v1: a small mammal tree. "Cat" carries a French label and a synonym, so a search can be shown
    // reaching a concept through a name that is not the one displayed.
    Path v1 = tempDir.resolve("EX_v1.sqlite");
    try (SnapshotStore store = SnapshotStore.openFile(v1.toString())) {
      store.initSchema();
      store.addConcept(BASE + "thing", "Thing");
      store.addConcept(BASE + "animal", "Animal");
      store.addConcept(BASE + "mammal", "Mammal");
      store.addConcept(BASE + "cat", "Cat");
      store.addConcept(BASE + "dog", "Dog");
      store.addConcept(BASE + "hound", "Hound", true, BASE + "dog");
      store.addEdge(BASE + "animal", BASE + "thing", "rdfs:subClassOf");
      store.addEdge(BASE + "mammal", BASE + "animal", "rdfs:subClassOf");
      store.addEdge(BASE + "cat", BASE + "mammal", "rdfs:subClassOf");
      store.addEdge(BASE + "dog", BASE + "mammal", "rdfs:subClassOf");
      store.addLabels(List.of(
          new SnapshotStore.LabelRow(BASE + "cat", "rdfs:label", "fr", "chat domestique"),
          new SnapshotStore.LabelRow(BASE + "cat", "oboInOwl:hasExactSynonym", "en", "housecat")));
      store.materialize();
    }

    // v2 adds a wolf under mammal, so the two versions answer the same query differently.
    Path v2 = tempDir.resolve("EX_v2.sqlite");
    try (SnapshotStore store = SnapshotStore.openFile(v2.toString())) {
      store.initSchema();
      store.addConcept(BASE + "thing", "Thing");
      store.addConcept(BASE + "animal", "Animal");
      store.addConcept(BASE + "mammal", "Mammal");
      store.addConcept(BASE + "cat", "Cat");
      store.addConcept(BASE + "dog", "Dog");
      store.addConcept(BASE + "wolf", "Wolf");
      store.addEdge(BASE + "animal", BASE + "thing", "rdfs:subClassOf");
      store.addEdge(BASE + "mammal", BASE + "animal", "rdfs:subClassOf");
      store.addEdge(BASE + "cat", BASE + "mammal", "rdfs:subClassOf");
      store.addEdge(BASE + "dog", BASE + "mammal", "rdfs:subClassOf");
      store.addEdge(BASE + "wolf", BASE + "mammal", "rdfs:subClassOf");
      store.materialize();
    }

    // A value-set collection: one value set with three values beneath it.
    Path vs = tempDir.resolve("VSC_v1.sqlite");
    try (SnapshotStore store = SnapshotStore.openFile(vs.toString())) {
      store.initSchema();
      store.addConcept(VS + "analyte-class", "Analyte class");
      store.addConcept(VS + "protein", "Protein");
      store.addConcept(VS + "lipid", "Lipid");
      store.addConcept(VS + "nucleic-acid", "Nucleic acid");
      store.addEdge(VS + "protein", VS + "analyte-class", "skos:broader");
      store.addEdge(VS + "lipid", VS + "analyte-class", "skos:broader");
      store.addEdge(VS + "nucleic-acid", VS + "analyte-class", "skos:broader");
      store.materialize();
    }

    catalog = CatalogStore.openFile(tempDir.resolve("catalog.sqlite").toString());
    catalog.initSchema();
    catalog.upsertOntology(new CatalogStore.OntologyInfo("EX", "Example Mammal Ontology", null, "OWL"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("hash-v1", "EX", "1.0", "2025-01-01",
        "2025-01-02T00:00:00Z", "OWL", "subsumption", 6, 4, v1.toString(), "f1", "open"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("hash-v2", "EX", "2.0", "2025-06-01",
        "2025-06-02T00:00:00Z", "OWL", "subsumption", 6, 5, v2.toString(), "f2", "open"));
    catalog.setTag("EX", CatalogStore.TAG_LATEST, "hash-v2");
    catalog.setOntologyIri("EX", "http://ex", BASE);

    catalog.upsertOntology(new CatalogStore.OntologyInfo("VSC", "Example Value Sets", null, "OWL"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo("hash-vs1", "VSC", "1.0", "2025-03-01",
        "2025-03-02T00:00:00Z", "OWL", "subsumption", 4, 3, vs.toString(), "f3", "open"));
    catalog.setTag("VSC", CatalogStore.TAG_LATEST, "hash-vs1");
    catalog.setOntologyKind("VSC", CatalogStore.KIND_VALUE_SET_COLLECTION);

    // NOTSERVED is in the catalog but not the allowlist; UNKNOWN is in neither.
    catalog.upsertOntology(new CatalogStore.OntologyInfo("NOTSERVED", "Absent Ontology", null, "OWL"));

    provider = new CatalogSnapshotProvider(catalog, Set.of("EX", "VSC"));
    service = new VersionAwareSearchService(provider);
  }

  @AfterEach
  public void tearDown() throws Exception {
    provider.close();
    if (tempDir != null) {
      try (var paths = Files.walk(tempDir)) {
        paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      }
    }
  }

  private static SearchRequest request(String query, List<String> types, List<SourceSelector> sources) {
    return new SearchRequest(query, types, sources, null, 1, 20);
  }

  private static SourceSelector ex(String versionId) {
    return new SourceSelector(null, "EX", versionId == null ? null : new VersionSelector(versionId));
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> hits(SearchResponse response, String type) {
    return (List<T>) response.results().get(type).collection();
  }

  @Test
  public void latestAnswersUnlessAVersionIsPinned() throws Exception {
    SearchResponse latest = service.search(request("wolf", List.of("class"), List.of(ex(null))));
    assertEquals(1, latest.results().get("class").totalCount(), "v2 is latest and holds the wolf");

    SearchResponse pinned = service.search(request("wolf", List.of("class"), List.of(ex("hash-v1"))));
    assertEquals(0, pinned.results().get("class").totalCount(), "v1 predates the wolf");

    // The point of the endpoint: searching latest while pinning v1 would offer a term the pinned
    // version does not contain.
    assertEquals("hash-v1", pinned.sources().get(0).version().id());
    assertEquals("hash-v2", latest.sources().get(0).version().id());
  }

  @Test
  public void aServedSourceReportsTheSnapshotThatAnswered() throws Exception {
    SearchResponse response = service.search(request("cat", List.of("class"), List.of(ex(null))));
    SourceBlock block = response.sources().get(0);
    assertEquals("bioportal", block.sourceSystem(), "an absent sourceSystem means BioPortal");
    assertEquals("EX", block.sourceAcronym());
    assertEquals("Example Mammal Ontology", block.sourceName());
    assertEquals("http://ex", block.sourceIri());
    assertEquals(SourceBlock.SERVED_LOCAL, block.served());
    assertTrue(block.pinnable());
    assertEquals("hash-v2", block.version().id());
    assertEquals("2.0", block.version().declaredVersion());
    assertNull(block.reason());
  }

  @Test
  public void aPinThatTheStoreDoesNotHoldIsReportedWithoutFailingTheSearch() throws Exception {
    SearchResponse response = service.search(request("cat", List.of("class"),
        List.of(ex("hash-v1"), new SourceSelector(null, "VSC", new VersionSelector("no-such-hash")))));

    SourceBlock missing = response.sources().get(1);
    assertEquals(SourceBlock.SERVED_UNAVAILABLE, missing.served());
    assertFalse(missing.pinnable());
    assertEquals(SourceBlock.REASON_VERSION_NOT_HELD, missing.reason());
    assertEquals("no-such-hash", missing.requestedVersion().id());
    // The other source still answered: a search across sources has a partial answer worth returning.
    assertEquals(1, response.results().get("class").totalCount());
  }

  @Test
  public void aSourceMayBeNamedOnlyOnce() {
    // Two versions of one acronym would leave every hit from it unable to say which it came from,
    // because a hit carries the addressing pair and no version of its own.
    assertThrows(VersionAwareSearchService.BadSearchRequestException.class,
        () -> service.search(request("cat", List.of("class"),
            List.of(ex("hash-v1"), ex("hash-v2")))));
  }

  @Test
  public void anUnservedSourceIsReportedRatherThanSilentlyOmitted() throws Exception {
    SearchResponse response = service.search(request("cat", List.of("class"),
        List.of(ex(null), new SourceSelector(null, "NOTSERVED", null),
            new SourceSelector("agroportal", "UNKNOWN", null))));

    assertEquals(SourceBlock.REASON_SOURCE_NOT_SERVED, response.sources().get(1).reason());
    assertEquals(SourceBlock.REASON_SOURCE_UNKNOWN, response.sources().get(2).reason());
    // Never proxied to BioPortal, whatever the system: the results say nothing about these two, which
    // is exactly why they appear.
    assertEquals(SourceBlock.SERVED_UNAVAILABLE, response.sources().get(2).served());
    assertEquals("agroportal", response.sources().get(2).sourceSystem());
  }

  @Test
  public void aHitFoundThroughAnotherNameSaysWhatMatched() throws Exception {
    SearchResponse response = service.search(new SearchRequest("chat", List.of("class"),
        List.of(ex("hash-v1")), null, 1, 20));

    List<ClassHit> hits = hits(response, "class");
    assertEquals(1, hits.size());
    ClassHit cat = hits.get(0);
    assertEquals("Cat", cat.termLabel(), "the served label, not the one that matched");
    assertEquals(SearchResponse.MATCH_SYNONYM, cat.matchType());
    assertEquals("chat domestique", cat.matchedLabels().get(0).label());
    assertEquals("fr", cat.matchedLabels().get(0).language());
  }

  @Test
  public void aHitOnTheLabelItselfNeedsNoExplanation() throws Exception {
    List<ClassHit> hits = hits(service.search(request("Cat", List.of("class"), List.of(ex("hash-v1")))), "class");
    assertEquals(SearchResponse.MATCH_TERM_LABEL, hits.get(0).matchType());
    assertNull(hits.get(0).matchedLabels());
  }

  @Test
  public void langChoosesTheLabelShown() throws Exception {
    SearchResponse response = service.search(new SearchRequest("cat", List.of("class"),
        List.of(ex("hash-v1")), "fr", 1, 20));
    assertEquals("chat domestique", ((ClassHit) response.results().get("class").collection().get(0)).termLabel());
  }

  @Test
  public void anObsoleteTermIsShownMarkedAndDemoted() throws Exception {
    // "hound" is obsolete and replaced by "dog"; both match "ound"/"og" poorly, so query them together.
    List<ClassHit> hits = hits(service.search(request("o", List.of("class"), List.of(ex("hash-v1")))), "class");
    ClassHit hound = hits.stream().filter(h -> h.termIri().endsWith("hound")).findFirst().orElseThrow();
    assertTrue(hound.obsolete());
    assertEquals(BASE + "dog", hound.replacedBy().termIri());
    assertEquals("Dog", hound.replacedBy().termLabel());
    // Demoted: every live hit precedes it.
    assertEquals(hits.size() - 1, hits.indexOf(hound));
  }

  @Test
  public void branchesAreTheHitsThatHaveDescendants() throws Exception {
    SearchResponse response = service.search(request("a", List.of("class", "branch"), List.of(ex("hash-v1"))));
    List<BranchHit> branches = hits(response, "branch");

    assertTrue(response.results().get("class").totalCount() > branches.size(),
        "a branch is a class hit with something beneath it, so there are fewer");
    assertTrue(branches.stream().allMatch(b -> b.descendantCount() > 0));

    BranchHit mammal = branches.stream().filter(b -> b.termBaseIri().endsWith("mammal")).findFirst().orElseThrow();
    assertEquals(2, mammal.descendantCount(), "Cat and Dog");
    // The path is what separates one "mammal" from another, and runs root-first.
    assertEquals(List.of(BASE + "thing", BASE + "animal"),
        mammal.path().stream().map(SearchResponse.TermRef::termIri).toList());
    assertEquals(2, mammal.examples().size());
  }

  @Test
  public void ontologiesMatchOnNameAndAcronymRatherThanOnTheirContent() throws Exception {
    // Named for what it is: no ontology is called "cat", so the tab is empty even though terms match.
    assertEquals(0, service.search(request("cat", List.of("ontology"), List.of())).results()
        .get("ontology").totalCount());

    List<OntologyHit> byName = hits(service.search(request("mammal", List.of("ontology"), List.of())), "ontology");
    assertEquals(1, byName.size());
    assertEquals("EX", byName.get(0).sourceAcronym());
    assertEquals(SearchResponse.MATCH_SOURCE_NAME, byName.get(0).matchType());

    List<OntologyHit> byAcronym = hits(service.search(request("ex", List.of("ontology"), List.of())), "ontology");
    assertEquals(SearchResponse.MATCH_SOURCE_ACRONYM, byAcronym.get(0).matchType());
  }

  @Test
  public void anOntologyHitBringsItsSourceBlockWithIt() throws Exception {
    // The hit carries only the addressing pair, so without a block for it a caller that named no
    // sources is handed an acronym and no way to learn the ontology's name, IRI or version.
    SearchResponse response = service.search(request("mammal", List.of("ontology"), List.of()));
    assertEquals(1, response.results().get("ontology").totalCount());
    assertEquals(1, response.sources().size());
    SourceBlock block = response.sources().get(0);
    assertEquals("EX", block.sourceAcronym());
    assertEquals("Example Mammal Ontology", block.sourceName());
    assertEquals("hash-v2", block.version().id());
  }

  @Test
  public void ontologySearchNeedsNoSourceButEveryOtherTypeDoes() throws Exception {
    assertNotNull(service.search(request("mammal", List.of("ontology"), List.of())));
    assertThrows(VersionAwareSearchService.BadSearchRequestException.class,
        () -> service.search(request("cat", List.of("class"), List.of())));
  }

  @Test
  public void aValueSetIsFoundByItsOwnNameOrByOneOfItsValues() throws Exception {
    SourceSelector vsc = new SourceSelector(null, "VSC", null);

    List<ValueSetHit> byName = hits(service.search(request("analyte", List.of("valueSet"), List.of(vsc))), "valueSet");
    assertEquals(1, byName.size());
    assertEquals("Analyte class", byName.get(0).termBaseLabel());
    assertEquals(3, byName.get(0).termCount());
    assertEquals(SearchResponse.MATCH_TERM_BASE_LABEL, byName.get(0).matchType());

    List<ValueSetHit> byValue = hits(service.search(request("protein", List.of("valueSet"), List.of(vsc))), "valueSet");
    assertEquals(1, byValue.size(), "a matched value surfaces the value set that holds it");
    assertEquals(VS + "analyte-class", byValue.get(0).termBaseIri());
    assertEquals(SearchResponse.MATCH_MEMBER, byValue.get(0).matchType());
    assertEquals(VS + "protein", byValue.get(0).matchedTerms().get(0).termIri());
  }

  @Test
  public void everyRequestedTypeIsAnsweredAndTheOthersAreAbsent() throws Exception {
    SearchResponse response = service.search(request("a", List.of("class", "branch"), List.of(ex(null))));
    assertEquals(Set.of("branch", "class"), response.results().keySet());
    // Reported in the order the constraint spec lists the types, not the order they were asked for.
    assertEquals(List.of("branch", "class"), List.copyOf(response.results().keySet()));
  }

  @Test
  public void anUnknownTypeIsRefusedRatherThanIgnored() {
    assertThrows(VersionAwareSearchService.BadSearchRequestException.class,
        () -> service.search(request("cat", List.of("nonsense"), List.of(ex(null)))));
  }

  @Test
  public void pagingSlicesTheResultsAndReportsTheWholeCount() throws Exception {
    SearchRequest first = new SearchRequest("a", List.of("class"), List.of(ex(null)), null, 1, 2);
    TypeResults page1 = service.search(first).results().get("class");
    TypeResults page2 = service.search(new SearchRequest("a", List.of("class"), List.of(ex(null)), null, 2, 2))
        .results().get("class");

    assertEquals(2, page1.collection().size());
    assertEquals(page1.totalCount(), page2.totalCount());
    assertFalse(page1.countCapped(), "a small result set is counted, not capped");
    assertTrue(page1.totalCount() > 2);
  }

  @Test
  public void versionLatestMeansUnpinned() {
    assertNull(VersionSelector.of(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree("latest")));
    assertNull(VersionSelector.of(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree("")));
    assertEquals("abc", VersionSelector.of(
        new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(java.util.Map.of("id", "abc"))).id());
  }
}
