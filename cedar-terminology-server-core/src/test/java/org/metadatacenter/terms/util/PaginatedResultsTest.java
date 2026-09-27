package org.metadatacenter.terms.util;

import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.customObjects.PagedResults;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginatedResultsTest {

  private static final List<Integer> TWENTY = IntStream.range(0, 20).boxed().toList();

  @Test
  void aMiddlePageIsItsSlice() {
    PagedResults<Integer> page = Util.generatePaginatedResults(TWENTY, 2, 6, Optional.empty());

    assertEquals(List.of(6, 7, 8, 9, 10, 11), page.getCollection());
    assertEquals(2, page.getPage());
    assertEquals(1, page.getPrevPage());
    assertEquals(3, page.getNextPage());
    assertEquals(4, page.getPageCount());
    assertEquals(20, page.getTotalCount());
  }

  @Test
  void aPagePastTheEndIsEmptyRatherThanEverything() {
    PagedResults<Integer> page = Util.generatePaginatedResults(TWENTY, 9, 6, Optional.empty());

    assertTrue(page.getCollection().isEmpty());
    assertEquals(9, page.getPage());
    assertNull(page.getNextPage());
  }

  @Test
  void aLastPageFilledExactlyHasNoNextPage() {
    PagedResults<Integer> last = Util.generatePaginatedResults(TWENTY, 4, 5, Optional.empty());

    assertEquals(List.of(15, 16, 17, 18, 19), last.getCollection());
    assertNull(last.getNextPage());
  }

  @Test
  void aShortLastPageHasNoNextPage() {
    PagedResults<Integer> last = Util.generatePaginatedResults(TWENTY, 4, 6, Optional.empty());

    assertEquals(List.of(18, 19), last.getCollection());
    assertNull(last.getNextPage());
  }
}
