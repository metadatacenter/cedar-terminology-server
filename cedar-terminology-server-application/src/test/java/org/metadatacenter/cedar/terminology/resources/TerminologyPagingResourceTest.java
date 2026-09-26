package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.JsonNode;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.TestAuthUtil;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The terminology server's paged routes read by limit and offset, answered in CEDAR's body envelope,
 * over HTTP against a local store.
 *
 * <p>The fixture's one ontology has a root with 23 children whose labels are written out of order, so
 * paging has something to walk and an offset something to land inside. Each route family is checked
 * for what a client relies on: that an offset lands where it says, that the links carry the filters
 * and never a page number, that the page-number parameters still answer as they did, and that asking
 * both ways at once is refused.
 */
public class TerminologyPagingResourceTest {

  private static final String ONT = "PAGETEST";
  private static final String BASE = "http://pagetest/";
  private static final String ROOT = BASE + "root";
  private static final int CHILDREN = 23;
  private static String version;

  static {
    Map<String, String> environment =
        new HashMap<>(org.metadatacenter.config.environment.CedarEnvironmentSource.getAll());
    environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_STOP_PORT", "0");
    org.metadatacenter.config.environment.CedarEnvironmentSource.setOverride(environment);
    try {
      Path dir = Files.createTempDirectory("terminology-paging-it");
      Path snapshot = dir.resolve("snap.sqlite");
      try (SnapshotStore s = SnapshotStore.openFile(snapshot.toString())) {
        s.initSchema();
        s.addConcept(ROOT, "Root");
        for (int i = 0; i < CHILDREN; i++) {
          int k = (int) (((long) i * 1_000_003 + 7) % CHILDREN);
          s.addConcept(BASE + "c" + k, String.format("Child %03d", k));
          s.addEdge(BASE + "c" + k, ROOT, "rdfs:subClassOf");
        }
        s.materialize();
        version = s.normalizedContentHash(true);
      }
      Path catalog = dir.resolve("catalog.sqlite");
      try (CatalogStore c = CatalogStore.openFile(catalog.toString())) {
        c.initSchema();
        c.upsertOntology(new CatalogStore.OntologyInfo(ONT, "Paging Test", null, "OWL"));
        c.addSnapshot(new CatalogStore.SnapshotInfo(version, ONT, "1.0", "2026-01-01", "2026-01-01T00:00:00Z",
            "OWL", "subsumption", CHILDREN + 1, CHILDREN, snapshot.toString(), "p1", "open"));
        c.setTag(ONT, CatalogStore.TAG_LATEST, version);
        c.setOntologyIri(ONT, BASE, BASE);
      }
      System.setProperty("terminologyStore.catalogPath", catalog.toString());
      System.setProperty("terminologyStore.localOntologies", ONT);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static final DropwizardTestSupport<TerminologyServerConfiguration> SERVER =
      new DropwizardTestSupport<>(TerminologyServerApplication.class, ResourceHelpers.resourceFilePath("test-config.yml"));

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static String authHeader;

  @BeforeAll
  public static void setUp() throws Exception {
    SERVER.before();
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_TERMINOLOGY);
    CedarConfig cedarConfig = CedarConfig.getInstance(environment);
    TestAuthUtil.installInMemoryUserService(cedarConfig);
    authHeader = TestAuthUtil.getTestUser1AuthHeader(cedarConfig);
  }

  @AfterAll
  public static void tearDown() {
    SERVER.after();
  }

  private static String children(String query) {
    return "/bioportal/ontologies/" + ONT + "/classes/" + URLEncoder.encode(ROOT, StandardCharsets.UTF_8)
        + "/children" + query;
  }

  // ---- a GET route that pages by number upstream -------------------------------------------------

  @Test
  public void anOffsetLandsWhereItSaysAndTheEnvelopeSaysSo() throws Exception {
    List<String> whole = labels(get(children("?page=1&pageSize=100")));

    JsonNode page = get(children("?limit=5&offset=7"));

    assertEquals(whole.subList(7, 12), labels(page));
    assertEquals(CHILDREN, page.get("totalCount").asInt());
    assertEquals(7, page.get("currentOffset").asLong());
    assertEquals(5, page.get("request").get("limit").asInt());
    assertEquals(7, page.get("request").get("offset").asInt());
    assertEquals("12", param(page.get("paging").get("next").asText(), "offset"));
    assertEquals("20", param(page.get("paging").get("last").asText(), "offset"));
  }

  @Test
  public void followingNextVisitsEveryChildOnceInTheSourcesOrder() throws Exception {
    List<String> whole = labels(get(children("?page=1&pageSize=100")));

    List<String> walked = new ArrayList<>();
    String next = children("?limit=6");
    while (next != null) {
      JsonNode page = get(next);
      walked.addAll(labels(page));
      next = page.get("paging").has("next") ? pathOf(page.get("paging").get("next").asText()) : null;
    }

    assertEquals(CHILDREN, whole.size());
    assertEquals(whole, walked);
  }

  @Test
  public void aPageNumberStillAnswersAndItsLinksCarryOnlyTheOffset() throws Exception {
    JsonNode page = get(children("?page=2&pageSize=5"));

    assertEquals(2, page.get("page").asInt());
    assertEquals(3, page.get("nextPage").asInt());
    assertEquals(5, page.get("currentOffset").asLong());
    String next = page.get("paging").get("next").asText();
    assertEquals("10", param(next, "offset"));
    assertNull(param(next, "page"), "a link must not mix the two kinds of paging");
    assertNull(param(next, "pageSize"));
    assertEquals(200, status(pathOf(next)), "a link can be followed");
  }

  @Test
  public void bothKindsOfPagingAndOutOfRangeLimitsAreRefused() throws Exception {
    assertEquals(400, status(children("?page=2&limit=5")));
    assertEquals(400, status(children("?pageSize=5&offset=5")));
    assertEquals(400, status(children("?limit=0")));
    assertEquals(400, status(children("?limit=1001")));
    assertEquals(400, status(children("?offset=-1")));
  }

  // ---- integrated search, a POST -----------------------------------------------------------------

  @Test
  public void integratedSearchTakesAnOffsetInItsBodyAndAnswersWithoutLinks() throws Exception {
    String constraint = "{\"ontologies\":[{\"uri\":\"https://data.bioontology.org/ontologies/" + ONT
        + "\",\"acronym\":\"" + ONT + "\",\"name\":\"" + ONT + "\"}],\"branches\":[],\"valueSets\":[],"
        + "\"classes\":[]}";
    JsonNode whole = post("/bioportal/integrated-search", "{\"parameterObject\":{\"valueConstraints\":" + constraint
        + ",\"inputText\":\"child\"},\"page\":1,\"pageSize\":100}");

    JsonNode page = post("/bioportal/integrated-search", "{\"parameterObject\":{\"valueConstraints\":" + constraint
        + ",\"inputText\":\"child\"},\"limit\":4,\"offset\":9}");

    assertEquals(labels(whole).subList(9, 13), labels(page));
    assertEquals(9, page.get("currentOffset").asLong());
    assertEquals(4, page.get("request").get("limit").asInt());
    assertFalse(page.has("paging"), "a POST has no URL to link to");
    assertEquals(400, postStatus("/bioportal/integrated-search", "{\"parameterObject\":{\"valueConstraints\":"
        + constraint + ",\"inputText\":\"child\"},\"page\":2,\"limit\":4}"));
  }

  // ---- the versioned search and its hierarchy ----------------------------------------------------

  @Test
  public void theVersionedSearchPagesEachBlockByOffset() throws Exception {
    String body = "{\"query\":\"child\",\"types\":[\"class\"],\"sources\":[{\"sourceAcronym\":\"" + ONT + "\"}]";
    JsonNode whole = post("/search", body + ",\"pageSize\":100}").get("results").get("class");

    JsonNode block = post("/search", body + ",\"limit\":3,\"offset\":4}").get("results").get("class");

    assertEquals(labels(whole, "termLabel").subList(4, 7), labels(block, "termLabel"));
    assertEquals(4, block.get("currentOffset").asLong());
    assertEquals(3, block.get("request").get("limit").asInt());
    assertEquals(400, postStatus("/search", body + ",\"page\":1,\"limit\":3}"));
  }

  @Test
  public void aHierarchyPagesItsChildrenAndLinksToTheRest() throws Exception {
    String path = "/search/hierarchy?sourceAcronym=" + ONT + "&termIri=" + URLEncoder.encode(ROOT, StandardCharsets.UTF_8)
        + "&versionId=" + version;

    JsonNode page = get(path + "&limit=10&offset=20");

    assertEquals(3, page.get("children").size());
    assertEquals(CHILDREN, page.get("totalCount").asInt());
    assertEquals(20, page.get("currentOffset").asLong());
    assertFalse(page.get("paging").has("next"));
    assertEquals("10", param(page.get("paging").get("prev").asText(), "offset"));
    assertEquals(50, get(path).get("request").get("limit").asInt(), "without a limit, fifty as before");
    assertEquals(400, status(path + "&limit=501"));
  }

  // ---- helpers -----------------------------------------------------------------------------------

  private static List<String> labels(JsonNode page) {
    return labels(page.has("collection") ? page.get("collection") : page, "prefLabel");
  }

  private static List<String> labels(JsonNode node, String field) {
    JsonNode rows = node.has("collection") ? node.get("collection") : node;
    List<String> out = new ArrayList<>();
    rows.forEach(r -> {
      String label = r.path(field).asText("");
      // A comparison of blank labels would pass whatever the server did, so a missing one fails here.
      assertFalse(label.isBlank(), "a row without " + field + ": " + r);
      out.add(label);
    });
    assertEquals(out.size(), new java.util.HashSet<>(out).size(), "the fixture's labels are distinct: " + out);
    return out;
  }

  private static JsonNode get(String pathAndQuery) throws Exception {
    HttpResponse<String> r = send(HttpRequest.newBuilder(uri(pathAndQuery)).GET());
    assertEquals(200, r.statusCode(), pathAndQuery + " -> " + r.body());
    return JsonMapper.STRICT_MAPPER.readTree(r.body());
  }

  private static int status(String pathAndQuery) throws Exception {
    return send(HttpRequest.newBuilder(uri(pathAndQuery)).GET()).statusCode();
  }

  private static JsonNode post(String path, String body) throws Exception {
    HttpResponse<String> r = send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)));
    assertEquals(200, r.statusCode(), path + " " + body + " -> " + r.body());
    return JsonMapper.STRICT_MAPPER.readTree(r.body());
  }

  private static int postStatus(String path, String body) throws Exception {
    return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))).statusCode();
  }

  private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
    return CLIENT.send(request.header("Authorization", authHeader).build(), HttpResponse.BodyHandlers.ofString());
  }

  private static URI uri(String pathAndQuery) {
    return URI.create("http://localhost:" + SERVER.getLocalPort() + pathAndQuery);
  }

  private static String pathOf(String link) {
    URI uri = URI.create(link);
    return uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
  }

  private static String param(String link, String name) {
    String raw = URI.create(link).getRawQuery();
    if (raw == null) {
      return null;
    }
    for (String pair : raw.split("&")) {
      String[] kv = pair.split("=", 2);
      if (kv[0].equals(name)) {
        return URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8);
      }
    }
    return null;
  }
}
