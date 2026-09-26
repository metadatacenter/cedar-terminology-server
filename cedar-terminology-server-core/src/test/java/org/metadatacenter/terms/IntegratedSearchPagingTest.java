package org.metadatacenter.terms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.terms.bioportal.IBioPortalService;
import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.domainObjects.BpClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpLinks;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.SearchResult;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integrated search's paging, against a BioPortal scripted here.
 *
 * <p>The scripted BioPortal serves each source in an order that is deliberately not alphabetical,
 * as the real one does, so a result sorted one upstream page at a time could never page correctly.
 * Every test walks the pages as a client does and checks the walk visits every term exactly once, in
 * the order integrated search promises.
 */
class IntegratedSearchPagingTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ONTOLOGY_BASE = "http://data.bioontology.org/ontologies/";

  /** Terms per source, in the order the scripted BioPortal serves them. */
  private final Map<String, List<BpClass>> ontologies = new LinkedHashMap<>();
  private final Map<String, List<BpClass>> branches = new LinkedHashMap<>();
  private final List<String> upstreamCalls = new ArrayList<>();

  private TerminologyService service() {
    IBioPortalService bioPortal = (IBioPortalService) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[]{IBioPortalService.class}, (proxy, method, args) -> switch (method.getName()) {
          case "findAllClassesInOntology" -> {
            upstreamCalls.add("classes:" + args[0] + ":" + args[1]);
            yield page(ontologies.get((String) args[0]), (int) args[1], (int) args[2]);
          }
          case "getClassDescendants" -> {
            upstreamCalls.add("descendants:" + args[0] + ":" + args[2]);
            yield page(branches.get((String) args[0]), (int) args[2], (int) args[3]);
          }
          case "search" -> {
            String q = ((String) args[0]).toLowerCase();
            @SuppressWarnings("unchecked") List<String> sources = (List<String>) args[2];
            List<BpClass> matches = new ArrayList<>();
            if (args[5] != null) {
              matches.addAll(branches.get((String) args[5]));
            } else {
              for (String acronym : sources) {
                matches.addAll(ontologies.get(acronym));
              }
            }
            matches.removeIf(c -> !c.getPrefLabel().toLowerCase().contains(q));
            upstreamCalls.add("search:" + args[7]);
            yield page(matches, (int) args[7], (int) args[8]);
          }
          default -> throw new UnsupportedOperationException(method.getName());
        });
    return new TerminologyService(bioPortal);
  }

  private static BpPagedResults<BpClass> page(List<BpClass> all, int page, int size) {
    int from = Math.min((page - 1) * size, all.size());
    int to = Math.min(page * size, all.size());
    Integer next = to < all.size() ? page + 1 : null;
    return new BpPagedResults<>(page, (all.size() + size - 1) / size, all.size(), page > 1 ? page - 1 : null, next,
        new ArrayList<>(all.subList(from, to)));
  }

  /** {@code n} terms whose labels are served out of alphabetical order. */
  private static List<BpClass> terms(String acronym, String prefix, int n) {
    List<BpClass> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      // A prime stride is coprime with any n here, so it visits every number once, far from ascending.
      int k = (int) (((long) i * 1_000_003 + 3) % n);
      String label = String.format("%s %04d", prefix, k);
      BpLinks links = new BpLinks();
      links.setOntology(ONTOLOGY_BASE + acronym);
      out.add(new BpClass("http://example.org/" + acronym + "/" + k, "http://www.w3.org/2002/07/owl#Class", label,
          null, List.of(), false, links, false, null, "prefLabel"));
    }
    return out;
  }

  private static ValueConstraints constraints(String json) throws Exception {
    String full = "{\"ontologies\":[],\"branches\":[],\"valueSets\":[],\"classes\":[],\"actions\":[],"
        + json.substring(1);
    return MAPPER.readValue(full.replace(",}", "}"), ValueConstraints.class);
  }

  private static String ontology(String acronym) {
    return "{\"uri\":\"" + ONTOLOGY_BASE + acronym + "\",\"acronym\":\"" + acronym + "\",\"name\":\"" + acronym + "\"}";
  }

  /** Walks every page from 1 the way a client does, following nextPage. */
  private static List<SearchResult> walk(TerminologyService service, Optional<String> q, ValueConstraints vc,
                                         int pageSize) throws Exception {
    List<SearchResult> all = new ArrayList<>();
    Integer page = 1;
    int guard = 0;
    while (page != null) {
      PagedResults<SearchResult> answer = service.integratedSearch(q, vc, page, pageSize, "key", null);
      assertEquals(page, answer.getPage(), "the server answers the page it was asked for");
      all.addAll(answer.getCollection());
      page = answer.getNextPage();
      assertTrue(++guard < 1_000, "the walk did not end");
    }
    return all;
  }

  private static List<String> labels(List<SearchResult> results) {
    return results.stream().map(SearchResult::getPrefLabel).toList();
  }

  private static void assertEveryTermOnceInOrder(List<SearchResult> walked, int expected) {
    assertEquals(expected, walked.size());
    Set<String> ids = new HashSet<>();
    walked.forEach(r -> assertTrue(ids.add(r.getLdId()), "seen twice: " + r.getPrefLabel()));
    List<String> labels = labels(walked);
    List<String> sorted = new ArrayList<>(labels);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    assertEquals(sorted, labels, "the pages are one sorted list");
  }

  // ---- several sources ---------------------------------------------------------------------------

  @Test
  void severalOntologiesWithoutAQueryPageThroughOneSortedList() throws Exception {
    ontologies.put("ONTA", terms("ONTA", "alpha", 37));
    ontologies.put("ONTB", terms("ONTB", "beta", 23));
    ValueConstraints vc = constraints("{\"ontologies\":[" + ontology("ONTA") + "," + ontology("ONTB") + "]}");

    List<SearchResult> walked = walk(service(), Optional.empty(), vc, 10);

    assertEveryTermOnceInOrder(walked, 60);
  }

  @Test
  void aSecondPageIsNotTheFirstAgain() throws Exception {
    ontologies.put("ONTA", terms("ONTA", "alpha", 30));
    ontologies.put("ONTB", terms("ONTB", "beta", 30));
    ValueConstraints vc = constraints("{\"ontologies\":[" + ontology("ONTA") + "," + ontology("ONTB") + "]}");

    PagedResults<SearchResult> first = service().integratedSearch(Optional.empty(), vc, 1, 10, "key", null);
    PagedResults<SearchResult> second = service().integratedSearch(Optional.empty(), vc, 2, 10, "key", null);

    assertEquals(2, second.getPage());
    assertEquals(3, second.getNextPage());
    assertEquals(1, second.getPrevPage());
    assertEquals(60, second.getTotalCount());
    assertEquals(6, second.getPageCount());
    assertFalse(labels(second.getCollection()).contains(labels(first.getCollection()).get(0)));
    assertNull(second.getCountCapped());
  }

  @Test
  void anOntologyAndABranchSearchedByNamePageThroughTheBestMatches() throws Exception {
    ontologies.put("ONTA", terms("ONTA", "heart", 18));
    branches.put("http://example.org/root", terms("ONTB", "heart valve", 14));
    ValueConstraints vc = constraints("{\"ontologies\":[" + ontology("ONTA") + "],\"branches\":[{\"uri\":"
        + "\"http://example.org/root\",\"acronym\":\"ONTB\",\"source\":\"ONTB\",\"name\":\"root\",\"maxDepth\":0}]}");

    List<SearchResult> walked = walk(service(), Optional.of("heart"), vc, 5);

    assertEquals(32, walked.size());
    Set<String> ids = new HashSet<>();
    walked.forEach(r -> assertTrue(ids.add(r.getLdId()), "seen twice: " + r.getPrefLabel()));
    // Closest match first: the shorter labels, all 'heart NNNN', before any 'heart valve NNNN'.
    assertTrue(labels(walked).subList(0, 18).stream().allMatch(l -> !l.contains("valve")), labels(walked).toString());
  }

  // ---- one source listed without a query ---------------------------------------------------------

  @Test
  void oneOntologyWithoutAQueryAdvancesThroughItsPages() throws Exception {
    ontologies.put("ONTA", terms("ONTA", "alpha", 45));
    ValueConstraints vc = constraints("{\"ontologies\":[" + ontology("ONTA") + "]}");

    List<SearchResult> walked = walk(service(), Optional.empty(), vc, 20);

    assertEveryTermOnceInOrder(walked, 45);
  }

  @Test
  void oneBranchWithoutAQueryAdvancesThroughItsPages() throws Exception {
    branches.put("http://example.org/root", terms("ONTB", "branch", 33));
    ValueConstraints vc = constraints("{\"branches\":[{\"uri\":\"http://example.org/root\",\"acronym\":\"ONTB\","
        + "\"source\":\"ONTB\",\"name\":\"root\",\"maxDepth\":0}]}");

    List<SearchResult> walked = walk(service(), Optional.empty(), vc, 10);

    assertEveryTermOnceInOrder(walked, 33);
  }

  // ---- the window --------------------------------------------------------------------------------

  @Test
  void aSourceBeyondTheWindowIsCappedAndTheWalkEndsAtTheWindow() throws Exception {
    ontologies.put("ONTA", terms("ONTA", "alpha", 1_203));
    ontologies.put("ONTB", terms("ONTB", "beta", 5));
    ValueConstraints vc = constraints("{\"ontologies\":[" + ontology("ONTA") + "," + ontology("ONTB") + "]}");

    PagedResults<SearchResult> first = service().integratedSearch(Optional.empty(), vc, 1, 50, "key", null);
    List<SearchResult> walked = walk(service(), Optional.empty(), vc, 200);

    assertTrue(first.getCountCapped());
    assertEquals(IntegratedSearchWindow.WINDOW + 5, first.getTotalCount(), "the count is of what the window reached");
    assertEquals(IntegratedSearchWindow.WINDOW + 5, walked.size());
  }

  @Test
  void theWindowIsReadInUpstreamPagesAndNoFurther() throws Exception {
    ontologies.put("ONTA", terms("ONTA", "alpha", 1_203));
    ValueConstraints vc = constraints("{\"ontologies\":[" + ontology("ONTA") + "]}");

    service().integratedSearch(Optional.empty(), vc, 1, 50, "key", null);

    assertEquals(List.of("classes:ONTA:1", "classes:ONTA:2"), upstreamCalls);
  }

  // ---- actions -----------------------------------------------------------------------------------

  @Test
  void aDeletedTermIsGoneFromEveryPageAndThePagesStayFull() throws Exception {
    ontologies.put("ONTA", terms("ONTA", "alpha", 20));
    ontologies.put("ONTB", terms("ONTB", "beta", 20));
    ValueConstraints vc = constraints("{\"ontologies\":[" + ontology("ONTA") + "," + ontology("ONTB") + "],"
        + "\"actions\":[{\"action\":\"delete\",\"termUri\":\"http://example.org/ONTB/7\",\"type\":\"OntologyClass\","
        + "\"source\":\"ONTB\"}]}");

    List<SearchResult> walked = walk(service(), Optional.empty(), vc, 10);
    PagedResults<SearchResult> first = service().integratedSearch(Optional.empty(), vc, 1, 10, "key", null);

    assertEquals(39, walked.size());
    assertFalse(labels(walked).contains("beta 0007"));
    assertEquals(39, first.getTotalCount());
    assertEquals(2, first.getNextPage(), "actions no longer leave the paging blank");
    assertEquals(10, first.getCollection().size());
  }

  @Test
  void aMoveReachesItsPositionOnWhicheverPageThatIs() throws Exception {
    ontologies.put("ONTA", terms("ONTA", "alpha", 30));
    ValueConstraints vc = constraints("{\"ontologies\":[" + ontology("ONTA") + "],\"actions\":[{\"to\":25,"
        + "\"action\":\"move\",\"termUri\":\"http://example.org/ONTA/0\",\"type\":\"OntologyClass\",\"source\":\"ONTA\"}]}");

    List<SearchResult> walked = walk(service(), Optional.empty(), vc, 10);

    assertEquals(30, walked.size());
    assertEquals("alpha 0000", walked.get(25).getPrefLabel());
    assertEquals("alpha 0001", walked.get(0).getPrefLabel());
  }

  // ---- what is not reordered keeps its source's paging -------------------------------------------

  @Test
  void oneOntologySearchedByNameUsesItsSourcesOwnPages() throws Exception {
    ontologies.put("ONTA", terms("ONTA", "gene", 25));
    ValueConstraints vc = constraints("{\"ontologies\":[" + ontology("ONTA") + "]}");

    PagedResults<SearchResult> third = service().integratedSearch(Optional.of("gene"), vc, 3, 10, "key", null);

    assertEquals(List.of("search:3"), upstreamCalls, "one upstream page, the one asked for");
    assertEquals(5, third.getCollection().size());
    assertNull(third.getNextPage());
  }
}
