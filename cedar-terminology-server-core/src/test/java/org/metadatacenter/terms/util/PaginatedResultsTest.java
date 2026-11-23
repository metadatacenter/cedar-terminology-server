package org.metadatacenter.terms.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.terms.customObjects.PagedResults;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginatedResultsTest {

  private static final List<Integer> TWENTY = IntStream.range(0, 20).boxed().toList();

  @Test
  void aMiddlePageIsItsSlice() {
    PagedResults<Integer> page = Util.generatePaginatedResults(TWENTY, 2, 6, Optional.empty());

    assertEquals(List.of(6, 7, 8, 9, 10, 11), page.getCollection());
    assertTrue(page.hasNextPage());
    assertEquals(20, page.getTotalCount());
  }

  @Test
  void aPagePastTheEndIsEmptyRatherThanEverything() {
    PagedResults<Integer> page = Util.generatePaginatedResults(TWENTY, 9, 6, Optional.empty());

    assertTrue(page.getCollection().isEmpty());
    assertFalse(page.hasNextPage());
  }

  @Test
  void aLastPageFilledExactlyHasNoNextPage() {
    PagedResults<Integer> last = Util.generatePaginatedResults(TWENTY, 4, 5, Optional.empty());

    assertEquals(List.of(15, 16, 17, 18, 19), last.getCollection());
    assertFalse(last.hasNextPage());
  }

  @Test
  void aShortLastPageHasNoNextPage() {
    PagedResults<Integer> last = Util.generatePaginatedResults(TWENTY, 4, 6, Optional.empty());

    assertEquals(List.of(18, 19), last.getCollection());
    assertFalse(last.hasNextPage());
  }

  @Test
  void aSerializedPageCarriesTheEnvelopeAndNoPageNumberFields() {
    PagedResults<Integer> page = Util.generatePaginatedResults(TWENTY, 2, 6, Optional.empty())
        .envelope(6, 6, "http://t/x?limit=6&offset=6");

    JsonNode json = JsonMapper.STRICT_MAPPER.valueToTree(page);

    assertEquals(6, json.get("currentOffset").asInt());
    assertEquals(6, json.get("request").get("limit").asInt());
    assertEquals(6, json.get("request").get("offset").asInt());
    assertEquals(20, json.get("totalCount").asInt());
    assertTrue(json.has("paging"));
    for (String dropped : List.of("page", "pageCount", "pageSize", "prevPage", "nextPage", "hasNextPage")) {
      assertFalse(json.has(dropped), dropped);
    }
  }
}
