package org.metadatacenter.terms;

import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.SearchResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegratedSearchWindowTest {

  /** A source holding {@code total} results named after it, answered a page at a time. */
  private static IntegratedSearchWindow.Source source(String name, int total, List<String> calls) {
    return (page, size) -> {
      calls.add(name + ":" + page + ":" + size);
      List<SearchResult> collection = new ArrayList<>();
      for (int i = (page - 1) * size; i < Math.min(page * size, total); i++) {
        collection.add(result(name + i));
      }
      Integer next = page * size < total ? page + 1 : null;
      return new PagedResults<>(page, null, collection.size(), total, page > 1 ? page - 1 : null, next, collection);
    };
  }

  private static SearchResult result(String label) {
    return new SearchResult(label, label, null, null, label, null, null, null, "prefLabel", List.of());
  }

  @Test
  void aSourceSmallerThanTheWindowIsReadWhole() throws Exception {
    List<String> calls = new ArrayList<>();

    IntegratedSearchWindow.Window window = IntegratedSearchWindow.collect(List.of(source("a", 7, calls)), 10, 3);

    assertEquals(7, window.results().size());
    assertFalse(window.truncated());
    assertEquals(List.of("a:1:3", "a:2:3", "a:3:3"), calls);
  }

  @Test
  void aSourceLargerThanTheWindowIsCutAndSaysSo() throws Exception {
    List<String> calls = new ArrayList<>();

    IntegratedSearchWindow.Window window = IntegratedSearchWindow.collect(List.of(source("a", 25, calls)), 10, 4);

    assertEquals(10, window.results().size());
    assertTrue(window.truncated());
    assertEquals(List.of("a:1:4", "a:2:4", "a:3:4"), calls, "stops once the window is full");
    assertEquals("a9", window.results().get(9).getPrefLabel());
  }

  @Test
  void aSourceExactlyTheWindowIsNotTruncated() throws Exception {
    IntegratedSearchWindow.Window window =
        IntegratedSearchWindow.collect(List.of(source("a", 10, new ArrayList<>())), 10, 5);

    assertEquals(10, window.results().size());
    assertFalse(window.truncated());
  }

  @Test
  void everySourceGetsItsOwnWindowInOrder() throws Exception {
    IntegratedSearchWindow.Window window = IntegratedSearchWindow.collect(
        List.of(source("a", 3, new ArrayList<>()), source("b", 30, new ArrayList<>()), source("c", 2, new ArrayList<>())),
        10, 10);

    assertEquals(15, window.results().size());
    assertEquals("a0", window.results().get(0).getPrefLabel());
    assertEquals("b0", window.results().get(3).getPrefLabel());
    assertEquals("c0", window.results().get(13).getPrefLabel());
    assertTrue(window.truncated(), "b was cut");
  }

  @Test
  void anEmptyPageEndsASourceEvenIfItClaimsANextPage() throws Exception {
    IntegratedSearchWindow.Source liar = (page, size) ->
        new PagedResults<>(page, null, 0, 100, null, page + 1, List.of());

    IntegratedSearchWindow.Window window = IntegratedSearchWindow.collect(List.of(liar), 10, 5);

    assertEquals(0, window.results().size());
    assertFalse(window.truncated());
  }
}
