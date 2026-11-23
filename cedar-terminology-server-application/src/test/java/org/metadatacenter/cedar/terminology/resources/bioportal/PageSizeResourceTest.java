package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.metadatacenter.cedar.terminology.resources.FakeBioPortal.*;

/**
 * Each family of paged GET routes answers the limit asked for, defaults to 100, refuses a limit above
 * 500, and refuses the withdrawn page-number parameters with a message naming {@code limit} and
 * {@code offset}. A limit of one is smaller than any of these listings, so the default could not pass
 * for it.
 */
public class PageSizeResourceTest extends AbstractTerminologyServerResourceTest {

  /** One route of each family, relative to {@code /bioportal}, with the query it needs. */
  private static final List<String> ROUTES = List.of(
      "ontologies/" + ONTOLOGY + "/classes/" + encode(ROOT) + "/children?",
      "search?q=coronavirus&scope=classes&sources=" + VIRUSES + "&",
      "property_search?q=has&sources=" + VIRUSES + "&",
      "vs-collections/" + CEDARVS + "/value-sets?",
      "vs-collections/" + CEDARVS + "/value-sets/" + encode(STUDY_FILE_TYPE) + "/values?");

  static Stream<Arguments> withdrawn() {
    List<Arguments> cases = new ArrayList<>();
    for (String route : ROUTES) {
      for (String parameter : List.of("page=1", "page=2", "pageSize=1", "page_size=1")) {
        cases.add(Arguments.of(route, parameter));
      }
    }
    return cases.stream();
  }

  static Stream<String> routes() {
    return ROUTES.stream();
  }

  @ParameterizedTest(name = "{0}limit=1")
  @MethodSource("routes")
  public void theRouteAnswersTheLimitAskedFor(String route) {
    JsonNode page = read(baseUrlBp + "/" + route + "limit=1", JsonNode.class);
    Assertions.assertEquals(1, page.path("collection").size(), page.toString());
    Assertions.assertEquals(1, page.path("request").path("limit").asInt(), page.toString());
    Assertions.assertEquals(0, page.path("currentOffset").asInt(), page.toString());
    Assertions.assertTrue(page.path("totalCount").asInt() > 1, "the listing must be longer than the page: " + page);
    Assertions.assertTrue(page.path("paging").has("next"), page.toString());
    for (String dropped : List.of("page", "pageCount", "pageSize", "prevPage", "nextPage")) {
      Assertions.assertFalse(page.has(dropped), dropped + " in " + page);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("routes")
  public void withoutALimitThePageHoldsAHundredAndFiveHundredIsTheMost(String route) {
    Assertions.assertEquals(100, read(baseUrlBp + "/" + route, JsonNode.class).path("request").path("limit").asInt());
    Assertions.assertEquals(500,
        read(baseUrlBp + "/" + route + "limit=500", JsonNode.class).path("request").path("limit").asInt());
    Assertions.assertEquals(400, statusOf(baseUrlBp + "/" + route + "limit=501"));
  }

  @ParameterizedTest(name = "{0}{1}")
  @MethodSource("withdrawn")
  public void aPageNumberParameterIsRefusedNamingLimitAndOffset(String route, String parameter) {
    try (Response response = request(baseUrlBp + "/" + route + parameter).get()) {
      Assertions.assertEquals(400, response.getStatus());
      String body = response.readEntity(String.class);
      Assertions.assertTrue(body.contains("limit") && body.contains("offset"), body);
    }
  }
}
