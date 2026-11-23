package org.metadatacenter.terms.util;

import org.junit.jupiter.api.Test;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.paging.PageLimits;

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
      return new PagedResults<>(total, page * size < total, collection);
    };
  }

  @Test
  void nothingSentIsTheFirstPageAtTheDefaultLimit() throws Exception {
    OffsetPaging.Request request = OffsetPaging.resolve(null, null);

    assertEquals(0, request.offset());
    assertEquals(PageLimits.DEFAULT_LIMIT, request.limit());
    assertEquals(100, request.limit());
  }

  @Test
  void anAlignedOffsetIsOneUpstreamPage() throws Exception {
    List<String> calls = new ArrayList<>();

    PagedResults<Integer> page = OffsetPaging.fetch(OffsetPaging.resolve(10, 20), numbers(45, calls));

    assertEquals(List.of("3:10"), calls);
    assertEquals(20, page.getCollection().get(0));
    assertTrue(page.hasNextPage());
  }

  @Test
  void anOffsetInsideAPageIsServedFromTheTwoPagesItStraddles() throws Exception {
    List<String> calls = new ArrayList<>();

    PagedResults<Integer> page = OffsetPaging.fetch(OffsetPaging.resolve(10, 17),
        numbers(45, calls));

    assertEquals(List.of("2:10", "3:10"), calls);
    assertEquals(List.of(17, 18, 19, 20, 21, 22, 23, 24, 25, 26), page.getCollection());
    assertEquals(45, page.getTotalCount());
  }

  @Test
  void theLastPageEndsWithoutAskingForAPageBeyondIt() throws Exception {
    List<String> calls = new ArrayList<>();

    PagedResults<Integer> page = OffsetPaging.fetch(OffsetPaging.resolve(10, 42), numbers(45, calls));

    assertEquals(List.of("5:10"), calls);
    assertEquals(List.of(42, 43, 44), page.getCollection());
    assertFalse(page.hasNextPage());
  }

  @Test
  void walkingByOffsetVisitsEveryResultOnce() throws Exception {
    List<Integer> seen = new ArrayList<>();
    for (int offset = 0; offset < 45; offset += 7) {
      seen.addAll(OffsetPaging.fetch(OffsetPaging.resolve(7, offset),
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
      PagedResults<Integer> r = new PagedResults<>(1, false, List.of(1));
      r.setCountCapped(true);
      return r;
    };

    assertTrue(OffsetPaging.fetch(OffsetPaging.resolve(10, 0), capped).getCountCapped());
  }

  @Test
  void aLimitOfTheMaximumIsServedAndOneMoreIsRefused() throws Exception {
    assertEquals(500, PageLimits.MAX_LIMIT);
    assertEquals(PageLimits.MAX_LIMIT, OffsetPaging.resolve(PageLimits.MAX_LIMIT, null).limit());
    assertThrows(CedarAssertionException.class, () -> OffsetPaging.resolve(PageLimits.MAX_LIMIT + 1, null));
  }

  @Test
  void aLimitOutOfRangeIsRefused() {
    assertThrows(CedarAssertionException.class, () -> OffsetPaging.resolve(0, null));
    assertThrows(CedarAssertionException.class, () -> OffsetPaging.resolve(10, -1));
  }

  @Test
  void aBodyAtPageOneOrWithoutAPageIsServedByItsLimitAndOffset() throws Exception {
    assertEquals(new OffsetPaging.Request(0, PageLimits.DEFAULT_LIMIT), OffsetPaging.resolveBody(1, null, null));
    assertEquals(new OffsetPaging.Request(0, PageLimits.DEFAULT_LIMIT), OffsetPaging.resolveBody(null, null, null));
    assertEquals(new OffsetPaging.Request(30, 10), OffsetPaging.resolveBody(1, 10, 30));
  }

  @Test
  void aBodyAtAnyOtherPageIsRefused() {
    CedarAssertionException refused =
        assertThrows(CedarAssertionException.class, () -> OffsetPaging.resolveBody(2, null, null));
    assertTrue(refused.getMessage().contains("limit"));
    assertTrue(refused.getMessage().contains("offset"));
    assertThrows(CedarAssertionException.class, () -> OffsetPaging.resolveBody(0, 10, 0));
  }

  @Test
  void theEnvelopeStatesThePageAndLinksWithoutAPageNumber() {
    PagedResults<Integer> page = new PagedResults<>(45, true, List.of());

    page.envelope(10, 10, "http://t/x?q=a&offset=10&limit=10");

    assertEquals(10L, page.getCurrentOffset());
    assertEquals(10, page.getRequest().limit());
    assertTrue(page.getPaging().get("next").contains("offset=20"));
    assertTrue(page.getPaging().get("last").contains("offset=40"));
    assertTrue(page.getPaging().get("next").contains("q=a"));
  }

  @Test
  void aCappedPageHasNoLastLinkAndAPostHasNoLinksAtAll() {
    PagedResults<Integer> capped = new PagedResults<>(1000, true, List.of());
    capped.setCountCapped(true);
    capped.envelope(10, 0, "http://t/x");
    PagedResults<Integer> posted = new PagedResults<Integer>(50, true, List.of()).envelope(10, 0, null);

    assertFalse(capped.getPaging().containsKey("last"));
    assertNull(posted.getPaging());
    assertEquals(0L, posted.getCurrentOffset());
  }
}
