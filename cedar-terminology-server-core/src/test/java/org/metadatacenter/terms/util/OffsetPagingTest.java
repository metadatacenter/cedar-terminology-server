package org.metadatacenter.terms.util;

import org.junit.jupiter.api.Test;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OffsetPagingTest {

  /** A source of {@code total} numbers that pages by number, remembering what it was asked for. */
  private static OffsetPaging.PageSource<Integer> numbers(int total, List<String> calls) {
    return (page, size) -> {
      calls.add(page + ":" + size);
      List<Integer> collection = new ArrayList<>();
      for (int i = (page - 1) * size; i < Math.min(page * size, total); i++) {
        collection.add(i);
      }
      return new PagedResults<>(page, (total + size - 1) / size, collection.size(), total,
          page > 1 ? page - 1 : null, page * size < total ? page + 1 : null, collection);
    };
  }

  @Test
  void aPageNumberIsPassedThroughWithTheSourcesOwnFields() throws Exception {
    List<String> calls = new ArrayList<>();
    OffsetPaging.Request request = OffsetPaging.resolve(null, null, 3, 10, true, 50);

    PagedResults<Integer> page = OffsetPaging.fetch(request, numbers(45, calls));

    assertFalse(request.byOffset());
    assertEquals(20, request.offset());
    assertEquals(List.of("3:10"), calls);
    assertEquals(List.of(20, 21, 22, 23, 24, 25, 26, 27, 28, 29), page.getCollection());
    assertEquals(4, page.getNextPage());
  }

  @Test
  void nothingSentIsTheFirstPageAtTheDefaultSize() throws Exception {
    OffsetPaging.Request request = OffsetPaging.resolve(null, null, null, 25, false, 25);

    assertEquals(0, request.offset());
    assertEquals(25, request.limit());
  }

  @Test
  void anAlignedOffsetIsOneUpstreamPage() throws Exception {
    List<String> calls = new ArrayList<>();

    PagedResults<Integer> page = OffsetPaging.fetch(OffsetPaging.resolve(10, 20, null, 50, false, 50),
        numbers(45, calls));

    assertEquals(List.of("3:10"), calls);
    assertEquals(20, page.getCollection().get(0));
    assertEquals(3, page.getPage());
    assertEquals(4, page.getNextPage());
    assertEquals(2, page.getPrevPage());
  }

  @Test
  void anOffsetInsideAPageIsServedFromTheTwoPagesItStraddles() throws Exception {
    List<String> calls = new ArrayList<>();

    PagedResults<Integer> page = OffsetPaging.fetch(OffsetPaging.resolve(10, 17, null, 50, false, 50),
        numbers(45, calls));

    assertEquals(List.of("2:10", "3:10"), calls);
    assertEquals(List.of(17, 18, 19, 20, 21, 22, 23, 24, 25, 26), page.getCollection());
    assertEquals(45, page.getTotalCount());
  }

  @Test
  void theLastPageEndsWithoutAskingForAPageBeyondIt() throws Exception {
    List<String> calls = new ArrayList<>();

    PagedResults<Integer> page = OffsetPaging.fetch(OffsetPaging.resolve(10, 42, null, 50, false, 50),
        numbers(45, calls));

    assertEquals(List.of("5:10"), calls);
    assertEquals(List.of(42, 43, 44), page.getCollection());
    assertNull(page.getNextPage());
  }

  @Test
  void walkingByOffsetVisitsEveryResultOnce() throws Exception {
    List<Integer> seen = new ArrayList<>();
    for (int offset = 0; offset < 45; offset += 7) {
      seen.addAll(OffsetPaging.fetch(OffsetPaging.resolve(7, offset, null, 50, false, 50),
          numbers(45, new ArrayList<>())).getCollection());
    }

    List<Integer> expected = new ArrayList<>();
    for (int i = 0; i < 45; i++) {
      expected.add(i);
    }
    assertEquals(expected, seen);
  }

  @Test
  void aCappedSourceStaysCapped() throws Exception {
    OffsetPaging.PageSource<Integer> capped = (page, size) -> {
      PagedResults<Integer> r = new PagedResults<>(page, 1, 1, 1, null, null, List.of(1));
      r.setCountCapped(true);
      return r;
    };

    assertTrue(OffsetPaging.fetch(OffsetPaging.resolve(10, 0, null, 50, false, 50), capped).getCountCapped());
  }

  @Test
  void bothKindsOfPagingTogetherAreRefused() {
    assertThrows(CedarAssertionException.class, () -> OffsetPaging.resolve(10, null, 2, 10, false, 50));
    assertThrows(CedarAssertionException.class, () -> OffsetPaging.resolve(null, 0, null, 10, true, 50));
  }

  @Test
  void aLimitOutOfRangeIsRefused() {
    assertThrows(CedarAssertionException.class, () -> OffsetPaging.resolve(0, null, null, 10, false, 50));
    assertThrows(CedarAssertionException.class,
        () -> OffsetPaging.resolve(OffsetPaging.MAX_LIMIT + 1, null, null, 10, false, 50));
    assertThrows(CedarAssertionException.class, () -> OffsetPaging.resolve(10, -1, null, 10, false, 50));
  }

  @Test
  void theEnvelopeStatesThePageAndLinksWithoutAPageNumber() {
    PagedResults<Integer> page = new PagedResults<>(2, 5, 10, 45, 1, 3, List.of());

    page.envelope(10, 10, "http://t/x?q=a&offset=10&limit=10");

    assertEquals(10L, page.getCurrentOffset());
    assertEquals(10, page.getRequest().limit());
    assertTrue(page.getPaging().get("next").contains("offset=20"));
    assertTrue(page.getPaging().get("last").contains("offset=40"));
    assertTrue(page.getPaging().get("next").contains("q=a"));
  }

  @Test
  void aCappedPageHasNoLastLinkAndAPostHasNoLinksAtAll() {
    PagedResults<Integer> capped = new PagedResults<>(1, 1, 10, 1000, null, 2, List.of());
    capped.setCountCapped(true);
    capped.envelope(10, 0, "http://t/x");
    PagedResults<Integer> posted = new PagedResults<Integer>(1, 1, 10, 50, null, 2, List.of()).envelope(10, 0, null);

    assertFalse(capped.getPaging().containsKey("last"));
    assertNull(posted.getPaging());
    assertEquals(0L, posted.getCurrentOffset());
  }
}
