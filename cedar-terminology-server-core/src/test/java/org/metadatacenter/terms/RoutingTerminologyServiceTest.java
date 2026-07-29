package org.metadatacenter.terms;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.store.SnapshotStore;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

  @BeforeEach
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

  @AfterEach
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
    // The branch is the root's descendants (cat, dog); the root mammal is excluded. "a" matches Cat.
    PagedResults<SearchResult> r = router.search("a", List.of("classes"), null, false, EX, iri("mammal"), 0, 1, 50,
        false, false, null, null);
    assertEquals(List.of(iri("cat")), ldIds(r));
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

  /* findAllOntologies — the ontology-list endpoint the picker and health check read */

  private static final String BP = "https://data.bioontology.org/ontologies/";

  /** A remote whose findAllOntologies returns a fixed registry; throws for anything else. */
  private static ITerminologyService remoteListing(List<Ontology> registry) {
    return (ITerminologyService) Proxy.newProxyInstance(
        RoutingTerminologyServiceTest.class.getClassLoader(),
        new Class[]{ITerminologyService.class},
        (proxy, method, args) -> {
          if ("findAllOntologies".equals(method.getName())) {
            return registry;
          }
          throw new UnsupportedOperationException(method.getName());
        });
  }

  /** A local backend that reports {@code served} as the ontologies it versions. */
  private static SqliteTerminologyService localListing(List<Ontology> served) {
    return new SqliteTerminologyService(new SqliteTerminologyService.SnapshotProvider() {
      @Override
      public Optional<SnapshotStore> forOntology(String ontology) {
        return Optional.empty();
      }

      @Override
      public List<Ontology> ontologies() {
        return served;
      }
    });
  }

  @Test
  public void findAllOntologies_partialCutover_unionsRemoteRegistryWithLocal() throws Exception {
    // localOnly=false is an incremental cutover: BioPortal is still the source for everything not
    // migrated, so the list must be the full remote registry, plus any locally-served ontology
    // BioPortal omits — not just the allowlist. For an ontology present in both, BioPortal's metadata
    // must win: its display name ("Human Disease Ontology") is what the picker shows; the catalog's
    // bare-acronym name would degrade the label.
    Ontology remoteDoid = new Ontology("DOID", BP + "DOID", "Human Disease Ontology", false, null);
    Ontology remoteGo = new Ontology("GO", BP + "GO", "Gene Ontology", false, null);
    Ontology localDoid = new Ontology("DOID", BP + "DOID", "DOID", false, null);      // catalog name = acronym
    Ontology localOnly = new Ontology("LOCAL", BP + "LOCAL", "A Local-only Ontology", false, null);
    RoutingTerminologyService r = new RoutingTerminologyService(
        remoteListing(List.of(remoteDoid, remoteGo)), localListing(List.of(localDoid, localOnly)),
        acr -> "DOID".equals(acr) || "LOCAL".equals(acr), false);

    List<Ontology> all = r.findAllOntologies(false, null);
    // Full remote registry, plus the local-only ontology appended.
    assertEquals(List.of("DOID", "GO", "LOCAL"), all.stream().map(Ontology::getId).collect(Collectors.toList()));
    // BioPortal's richer name wins for the ontology present in both — not the catalog's bare acronym.
    assertEquals("Human Disease Ontology",
        all.stream().filter(o -> "DOID".equals(o.getId())).findFirst().orElseThrow().getName());
  }

  @Test
  public void perEndpoint_rootsBrowseFromRemoteWhileSearchStaysLocal() throws Exception {
    // EX is served locally (search/children), but its roots are NOT browse-local: getRootClasses must
    // come from the remote sentinel, while getClassChildren still comes from the local store. This is
    // the per-endpoint cutover for an ontology whose integrated-search is equivalent but whose local
    // roots still diverge.
    SqliteTerminologyService local = new SqliteTerminologyService(
        ontology -> EX.equals(ontology) ? Optional.of(store) : Optional.empty());
    ITerminologyService remote = (ITerminologyService) Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class[]{ITerminologyService.class},
        (p, m, a) -> {
          if ("getRootClasses".equals(m.getName())) {
            return List.of(new OntologyClass(REMOTE, iri("remote"), REMOTE, null, REMOTE, null, null, null, null,
                false, null, false));
          }
          throw new UnsupportedOperationException(m.getName());
        });
    RoutingTerminologyService r = new RoutingTerminologyService(
        remote, local, local::isAvailable, ontology -> false, false); // nothing is browse-local

    assertEquals(REMOTE, r.getRootClasses(EX, false, null).get(0).getPrefLabel()); // roots -> remote
    assertEquals(Integer.valueOf(2),
        r.getClassChildren(iri("mammal"), EX, 1, 50, null).getTotalCount());        // children -> local
  }

  @Test
  public void findAllOntologies_localOnly_returnsOnlyTheServedSet() throws Exception {
    // A fully offline deployment reports only what it versions; it must not call BioPortal at all
    // (the remote stub throws for findAllOntologies, proving it is not consulted).
    Ontology localDoid = new Ontology("DOID", BP + "DOID", "Human Disease Ontology", false, null);
    RoutingTerminologyService r = new RoutingTerminologyService(
        remoteListing(List.of()), localListing(List.of(localDoid)), acr -> true, true);
    assertEquals(List.of("DOID"), r.findAllOntologies(false, null).stream()
        .map(Ontology::getId).collect(Collectors.toList()));
  }
}
