package org.metadatacenter.terms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.util.Util;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A field's result actions, applied when the local store answers integrated search.
 *
 * <p>The local store knows nothing of actions, so the router applies them the way the BioPortal path
 * does. Each test serves a locally held ontology from a scripted local store and walks the pages a
 * client would, with a remote that fails the test if anything reaches it.
 */
class LocalResultActionsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ONT = "LOCALONT";
  private static final String BASE = "http://local/";

  /** The ontology's terms, in the order the local store answers them. */
  private final List<SearchResult> terms = new ArrayList<>();
  /** Terms the local store holds but that the constraint does not admit, for a move to reach. */
  private final Map<String, String> elsewhere = new java.util.HashMap<>();
  private final List<String> localCalls = new ArrayList<>();

  private RoutingTerminologyService router() {
    ITerminologyService local = (ITerminologyService) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[]{ITerminologyService.class}, (proxy, method, args) -> switch (method.getName()) {
          case "integratedSearch" -> {
            localCalls.add("integratedSearch:" + args[2] + ":" + args[3]);
            yield Util.generatePaginatedResults(terms, (int) args[2], (int) args[3], Optional.empty());
          }
          case "findClass" -> {
            String iri = (String) args[0];
            localCalls.add("findClass:" + iri + ":" + args[1]);
            String label = elsewhere.get(iri);
            yield label == null ? null
                : new OntologyClass(iri, iri, label, null, ONT, List.of(), List.of(), null, null, false, null, false);
          }
          case "isAvailable" -> true;
          default -> throw new UnsupportedOperationException(method.getName());
        });
    ITerminologyService remote = (ITerminologyService) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[]{ITerminologyService.class}, (proxy, method, args) -> {
          throw new AssertionError("the remote was asked: " + method.getName());
        });
    return new RoutingTerminologyService(remote, local, ontology -> true, true);
  }

  private void terms(int n) {
    for (int i = 0; i < n; i++) {
      String iri = BASE + i;
      terms.add(new SearchResult(iri, iri, null, "OntologyClass", String.format("Term %03d", i), null, null, ONT,
          null, List.of()));
    }
  }

  private static ValueConstraints constraints(String actions) throws Exception {
    return MAPPER.readValue("{\"ontologies\":[{\"uri\":\"https://data.bioontology.org/ontologies/" + ONT
        + "\",\"acronym\":\"" + ONT + "\",\"name\":\"" + ONT + "\"}],\"branches\":[],\"valueSets\":[],"
        + "\"classes\":[],\"actions\":" + actions + "}", ValueConstraints.class);
  }

  private static String move(String iri, int to) {
    return "{\"to\":" + to + ",\"action\":\"move\",\"termUri\":\"" + iri + "\",\"type\":\"OntologyClass\","
        + "\"source\":\"" + ONT + "\"}";
  }

  private static String delete(String iri) {
    return "{\"action\":\"delete\",\"termUri\":\"" + iri + "\",\"type\":\"OntologyClass\",\"source\":\"" + ONT + "\"}";
  }

  private static List<SearchResult> walk(RoutingTerminologyService router, ValueConstraints vc, int size)
      throws Exception {
    List<SearchResult> all = new ArrayList<>();
    Integer page = 1;
    while (page != null) {
      PagedResults<SearchResult> answer = router.integratedSearch(Optional.empty(), vc, page, size, "key", null);
      all.addAll(answer.getCollection());
      page = answer.getNextPage();
      assertTrue(all.size() < 10_000, "the walk did not end");
    }
    return all;
  }

  private static List<String> labels(List<SearchResult> results) {
    return results.stream().map(SearchResult::getPrefLabel).toList();
  }

  @Test
  void aDeletedTermIsGoneFromEveryPageAndThePagesStayFull() throws Exception {
    terms(25);
    ValueConstraints vc = constraints("[" + delete(BASE + 3) + "," + delete(BASE + 17) + "]");

    List<SearchResult> walked = walk(router(), vc, 10);
    PagedResults<SearchResult> first = router().integratedSearch(Optional.empty(), vc, 1, 10, "key", null);

    assertEquals(23, walked.size());
    assertFalse(labels(walked).contains("Term 003"));
    assertFalse(labels(walked).contains("Term 017"));
    assertEquals(10, first.getCollection().size());
    assertEquals(23, first.getTotalCount());
    assertEquals(2, first.getNextPage());
    Set<String> ids = new HashSet<>();
    walked.forEach(r -> assertTrue(ids.add(r.getLdId()), "seen twice: " + r.getPrefLabel()));
  }

  @Test
  void aMoveLandsAtItsPositionOnWhicheverPageThatIs() throws Exception {
    terms(25);
    ValueConstraints vc = constraints("[" + move(BASE + 0, 12) + "]");

    List<SearchResult> walked = walk(router(), vc, 10);

    assertEquals(25, walked.size());
    assertEquals("Term 000", walked.get(12).getPrefLabel());
    assertEquals("Term 001", walked.get(0).getPrefLabel());
  }

  @Test
  void aMovedTermTheConstraintDoesNotAdmitIsLookedUpInTheLocalStore() throws Exception {
    terms(5);
    elsewhere.put(BASE + "extra", "Extra term");
    ValueConstraints vc = constraints("[" + move(BASE + "extra", 1) + "]");

    List<SearchResult> walked = walk(router(), vc, 10);

    assertEquals(List.of("Term 000", "Extra term", "Term 001", "Term 002", "Term 003", "Term 004"), labels(walked));
    assertTrue(localCalls.contains("findClass:" + BASE + "extra:" + ONT));
  }

  @Test
  void aMovedTermTheStoreDoesNotHoldCostsOnlyItsOwnMove() throws Exception {
    terms(5);
    ValueConstraints vc = constraints("[" + move(BASE + "gone", 0) + "," + delete(BASE + 2) + "]");

    List<SearchResult> walked = walk(router(), vc, 10);

    assertEquals(List.of("Term 000", "Term 001", "Term 003", "Term 004"), labels(walked));
  }

  @Test
  void aFieldWithoutActionsIsStillPagedByTheLocalStoreDirectly() throws Exception {
    terms(25);
    ValueConstraints vc = constraints("[]");

    PagedResults<SearchResult> second = router().integratedSearch(Optional.empty(), vc, 2, 10, "key", null);

    assertEquals(List.of("integratedSearch:2:10"), localCalls, "one local page, the one asked for");
    assertEquals("Term 010", second.getCollection().get(0).getPrefLabel());
    assertNull(second.getCountCapped());
  }

  @Test
  void aLocalListBeyondTheWindowIsCapped() throws Exception {
    terms(1_203);
    ValueConstraints vc = constraints("[" + delete(BASE + 0) + "]");

    PagedResults<SearchResult> first = router().integratedSearch(Optional.empty(), vc, 1, 50, "key", null);

    assertTrue(first.getCountCapped());
    assertEquals(IntegratedSearchWindow.WINDOW - 1, first.getTotalCount());
  }
}
