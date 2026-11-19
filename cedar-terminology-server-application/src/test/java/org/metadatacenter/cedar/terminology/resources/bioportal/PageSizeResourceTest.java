package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.metadatacenter.cedar.terminology.resources.FakeBioPortal.*;

/**
 * Each family of paged routes answers a page of the size asked for, whichever spelling of the parameter carries it.
 * <p>
 * {@code PageSizeDeclarationTest} holds every paged route to declaring both spellings. Declaring one is not reading
 * it: a route that bound {@code page_size} and then ignored it would answer the default page size silently, which is
 * the defect the second spelling was added against. A page size of one is smaller than any of these listings, so the
 * default could not pass for it.
 */
public class PageSizeResourceTest extends AbstractTerminologyServerResourceTest {

  /** One route of each family, relative to {@code /bioportal}, with the query it needs. */
  private static final List<String> ROUTES = List.of(
      "ontologies/" + ONTOLOGY + "/classes/" + encode(ROOT) + "/children?",
      "search?q=coronavirus&scope=classes&sources=" + VIRUSES + "&",
      "property_search?q=has&sources=" + VIRUSES + "&",
      "vs-collections/" + CEDARVS + "/value-sets?",
      "vs-collections/" + CEDARVS + "/value-sets/" + encode(STUDY_FILE_TYPE) + "/values?");

  static Stream<Arguments> cases() {
    List<Arguments> cases = new ArrayList<>();
    for (String route : ROUTES) {
      for (String spelling : List.of("page_size", "pageSize")) {
        cases.add(Arguments.of(route, spelling));
      }
    }
    return cases.stream();
  }

  @ParameterizedTest(name = "{0}{1}=1")
  @MethodSource("cases")
  public void theRouteAnswersThePageSizeAskedFor(String route, String spelling) {
    JsonNode page = read(baseUrlBp + "/" + route + spelling + "=1", JsonNode.class);
    Assertions.assertEquals(1, page.path("pageSize").asInt(), page.toString());
    Assertions.assertEquals(1, page.path("collection").size(), page.toString());
    Assertions.assertTrue(page.path("totalCount").asInt() > 1, "the listing must be longer than the page: " + page);
  }
}
