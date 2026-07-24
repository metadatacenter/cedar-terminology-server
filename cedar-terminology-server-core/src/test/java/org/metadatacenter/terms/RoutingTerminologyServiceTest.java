package org.metadatacenter.terms;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.store.SnapshotStore;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Verifies the router's dispatch: local when available and implemented, remote otherwise —
 * including fallback when the local backend answers a routed call with UnsupportedOperationException.
 */
public class RoutingTerminologyServiceTest {

  private static final String EX = "EX";
  private static final String REMOTE = "REMOTE";

  private SnapshotStore store;
  private RoutingTerminologyService router;

  private static String iri(String s) {
    return "http://ex/" + s;
  }

  /**
   * A remote backend that returns a recognizable sentinel for the two operations exercised here
   * and throws for everything else. Built with a dynamic proxy to avoid stubbing ~45 methods.
   */
  private static ITerminologyService sentinelRemote() {
    return (ITerminologyService) Proxy.newProxyInstance(
        RoutingTerminologyServiceTest.class.getClassLoader(),
        new Class[]{ITerminologyService.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getClassTree" -> List.of(new TreeNode(null, null, null, null, REMOTE, null, false, null, false));
          case "findClass", "findRegularClass" ->
              new OntologyClass(REMOTE, iri("remote"), REMOTE, null, REMOTE, null, null, null, null, false, null, false);
          case "search", "integratedSearch" -> new PagedResults<>(1, 1, 1, 1, null, null,
              List.of(new SearchResult(REMOTE, iri("remote"), null, "OntologyClass", REMOTE, null, null, REMOTE, null,
                  null)));
          default -> throw new UnsupportedOperationException("remote stub does not implement " + method.getName());
        });
  }

  @Before
  public void setUp() throws Exception {
    store = SnapshotStore.openInMemory();
    store.initSchema();
    store.addConcept(iri("mammal"), "Mammal");
    store.addConcept(iri("dog"), "Dog");
    store.addConcept(iri("cat"), "Cat");
    store.addEdge(iri("dog"), iri("mammal"), "rdfs:subClassOf");
    store.addEdge(iri("cat"), iri("mammal"), "rdfs:subClassOf");
    store.materialize();

    SqliteTerminologyService local = new SqliteTerminologyService(
        ontology -> EX.equals(ontology) ? Optional.of(store) : Optional.empty());
    router = new RoutingTerminologyService(sentinelRemote(), local, local::isAvailable);
  }

  @After
  public void tearDown() throws Exception {
    store.close();
  }

  @Test
  public void availableAndImplemented_servedByLocal() throws Exception {
    OntologyClass dog = router.findClass(iri("dog"), EX, null);
    assertEquals("Dog", dog.getPrefLabel()); // local data, not the REMOTE sentinel
  }

  @Test
  public void availableButUnimplemented_fallsBackToRemote() throws Exception {
    // local throws UnsupportedOperationException for getClassTree -> router falls back to remote
    List<TreeNode> tree = router.getClassTree(iri("dog"), EX, false, null);
    assertEquals(REMOTE, tree.get(0).getPrefLabel());
  }

  @Test
  public void unavailableOntology_servedByRemote() throws Exception {
    OntologyClass c = router.findClass(iri("dog"), "OTHER", null);
    assertEquals(REMOTE, c.getPrefLabel());
  }

  @Test
  public void localChildren_servedByLocal() throws Exception {
    assertEquals(Integer.valueOf(2), router.getClassChildren(iri("mammal"), EX, 1, 50, null).getTotalCount());
  }

  /* search routing — the seam the equivalence harness relies on */

  private static List<String> ldIds(PagedResults<SearchResult> r) {
    return r.getCollection().stream().map(SearchResult::getLdId).collect(Collectors.toList());
  }

  @Test
  public void searchScopedToOneLocalOntology_servedByLocal() throws Exception {
    // Labels containing "a" in EX: Cat, Mammal — from the local store, not the REMOTE sentinel.
    PagedResults<SearchResult> r = router.search("a", List.of("classes"), List.of(EX), false, null, null, 0, 1, 50,
        false, false, null, null);
    assertEquals(List.of(iri("cat"), iri("mammal")), ldIds(r));
  }

  @Test
  public void branchSearchInLocalOntology_servedByLocal() throws Exception {
    PagedResults<SearchResult> r = router.search("a", List.of("classes"), null, false, EX, iri("mammal"), 0, 1, 50,
        false, false, null, null);
    assertEquals(List.of(iri("cat"), iri("mammal")), ldIds(r));
  }

  @Test
  public void searchAcrossMultipleSources_servedByRemote() throws Exception {
    PagedResults<SearchResult> r = router.search("a", List.of("classes"), List.of(EX, "OTHER"), false, null, null, 0,
        1, 50, false, false, null, null);
    assertEquals(REMOTE, r.getCollection().get(0).getPrefLabel());
  }

  @Test
  public void searchNonClassScopeInLocalOntology_fallsBackToRemote() throws Exception {
    // Single local source, but a value-set scope the local backend cannot serve -> remote sentinel.
    PagedResults<SearchResult> r = router.search("a", List.of("value_sets"), List.of(EX), false, null, null, 0, 1, 50,
        false, false, null, null);
    assertEquals(REMOTE, r.getCollection().get(0).getPrefLabel());
  }

  @Test
  public void searchInNonLocalOntology_servedByRemote() throws Exception {
    PagedResults<SearchResult> r = router.search("a", List.of("classes"), List.of("OTHER"), false, null, null, 0, 1,
        50, false, false, null, null);
    assertEquals(REMOTE, r.getCollection().get(0).getPrefLabel());
  }

  /* integratedSearch routing — the CEE surface */

  private static final ObjectMapper VC = new ObjectMapper()
      .setVisibility(new ObjectMapper().getVisibilityChecker().withFieldVisibility(JsonAutoDetect.Visibility.ANY));

  private ValueConstraints vc(String json) throws Exception {
    return VC.readValue(json, ValueConstraints.class);
  }

  @Test
  public void integratedSearchInLocalOntology_servedByLocal() throws Exception {
    // Empty text enumerates the local ontology (mammal, dog, cat) — not the REMOTE sentinel.
    PagedResults<SearchResult> r = router.integratedSearch(Optional.empty(),
        vc("{\"ontologies\":[{\"acronym\":\"" + EX + "\"}]}"), 1, 50, null);
    assertEquals(Integer.valueOf(3), r.getTotalCount());
  }

  @Test
  public void integratedSearchWithValueSets_servedByRemote() throws Exception {
    PagedResults<SearchResult> r = router.integratedSearch(Optional.of("x"), vc("{\"valueSets\":[{}]}"), 1, 50, null);
    assertEquals(REMOTE, r.getCollection().get(0).getPrefLabel());
  }

  @Test
  public void integratedSearchInNonLocalOntology_servedByRemote() throws Exception {
    PagedResults<SearchResult> r = router.integratedSearch(Optional.of("x"),
        vc("{\"ontologies\":[{\"acronym\":\"OTHER\"}]}"), 1, 50, null);
    assertEquals(REMOTE, r.getCollection().get(0).getPrefLabel());
  }

  /* localOnly — the strict mode the equivalence harness runs under */

  private RoutingTerminologyService strictRouter() {
    SqliteTerminologyService local = new SqliteTerminologyService(
        ontology -> EX.equals(ontology) ? Optional.of(store) : Optional.empty());
    return new RoutingTerminologyService(sentinelRemote(), local, local::isAvailable, true);
  }

  @Test
  public void localOnly_localOntologyDoesNotFallBack() {
    // getClassTree is unimplemented locally; strict mode must propagate, not return the REMOTE tree.
    assertThrows(UnsupportedOperationException.class,
        () -> strictRouter().getClassTree(iri("dog"), EX, false, null));
  }

  @Test
  public void localOnly_nonLocalOntologyStillUsesRemote() throws Exception {
    // localOnly only forbids fallback for locally-served ontologies; others are unaffected.
    assertEquals(REMOTE, strictRouter().findClass(iri("dog"), "OTHER", null).getPrefLabel());
  }
}
