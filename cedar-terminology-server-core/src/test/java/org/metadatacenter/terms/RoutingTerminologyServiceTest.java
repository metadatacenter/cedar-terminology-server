package org.metadatacenter.terms;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.store.SnapshotStore;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

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
}
