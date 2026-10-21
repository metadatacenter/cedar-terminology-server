package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.test.TestAuthUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metadatacenter.cedar.terminology.resources.FakeBioPortal.*;

/**
 * Each route the terminology server answers from BioPortal, asked of a BioPortal that answers as it does when all
 * goes well. {@link BioPortalAnswerMatrixTest} covers the ways BioPortal can fail; this covers what the server makes
 * of its answers: an ontology with its details, a class with its tree, children, descendants and parents, the same
 * for properties, provisional classes and relations, searches, and value sets with their values, each translated
 * into the server's own objects. The integrated search and retrieve, and term verification, draw on several of these.
 */
@Tag("integration")
public class BioPortalSuccessPathsTest {

  private static final FakeBioPortal BIOPORTAL = startBioPortal();

  static {
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_STOP_PORT", "0");
    environment.put("CEDAR_BIOPORTAL_REST_BASE", BIOPORTAL.base());
    CedarEnvironmentSource.setOverride(environment);
  }

  private static final DropwizardTestSupport<TerminologyServerConfiguration> SERVER =
      new DropwizardTestSupport<>(TerminologyServerApplication.class, ResourceHelpers.resourceFilePath("test-config.yml"));
  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper JSON = new ObjectMapper();
  private static String authHeader;

  @BeforeAll
  public static void setUp() throws Exception {
    SERVER.before();
    CedarConfig cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_TERMINOLOGY));
    TestAuthUtil.installInMemoryUserService(cedarConfig);
    authHeader = TestAuthUtil.getTestUser1AuthHeader(cedarConfig);
  }

  @AfterAll
  public static void tearDown() {
    SERVER.after();
    BIOPORTAL.close();
  }

  @Test
  void anOntologyIsListedAndDescribed() {
    JsonNode ontologies = get("/bioportal/ontologies");
    assertTrue(ids(ontologies).contains(ONTOLOGY), ontologies.toString());

    JsonNode ontology = get("/bioportal/ontologies/" + ONTOLOGY);
    assertEquals(ONTOLOGY, ontology.get("id").asText());
    assertEquals("Test Ontology", ontology.get("name").asText());
    JsonNode details = ontology.get("details");
    assertEquals("A test ontology", details.path("description").asText(), ontology.toString());
    assertEquals(4, details.path("numberOfClasses").asInt(), ontology.toString());

    assertEquals(List.of("Root"), labels(get("/bioportal/ontologies/" + ONTOLOGY + "/classes/roots")));
    assertEquals(List.of("has part"), labels(get("/bioportal/ontologies/" + ONTOLOGY + "/properties/roots")));
  }

  @Test
  void aClassIsReadWithItsHierarchy() {
    String classes = "/bioportal/ontologies/" + ONTOLOGY + "/classes/";
    JsonNode heart = get(classes + enc(HEART));
    assertEquals("Heart", heart.get("prefLabel").asText());
    assertEquals(HEART, heart.get("@id").asText());
    assertEquals(List.of("Cor"), strings(heart.get("synonyms")));
    assertEquals(List.of("The heart."), strings(heart.get("definitions")));
    assertFalse(heart.get("hasChildren").asBoolean());

    assertEquals(4, get("/bioportal/ontologies/" + ONTOLOGY + "/classes").get("collection").size());
    assertEquals(List.of("Organ", "Tissue"), labels(get(classes + enc(ROOT) + "/children").get("collection")));
    assertEquals(3, get(classes + enc(ROOT) + "/descendants").get("collection").size());
    assertEquals(List.of("Organ"), labels(get(classes + enc(HEART) + "/parents")));

    JsonNode tree = get(classes + enc(HEART) + "/tree");
    JsonNode root = tree.get(0);
    assertEquals("Root", root.get("prefLabel").asText(), tree.toString());
    JsonNode organ = root.get("children").get(0);
    assertEquals("Organ", organ.get("prefLabel").asText());
    assertEquals("Heart", organ.get("children").get(0).get("prefLabel").asText(), "the branch down to the class is open");
  }

  @Test
  void provisionalClassesAndRelationsAreRead() {
    JsonNode all = get("/bioportal/classes/provisional");
    assertEquals(List.of("Provisional Heart"), labels(all.get("collection")));
    JsonNode inOntology = get("/bioportal/ontologies/" + ONTOLOGY + "/classes/provisional");
    JsonNode provisional = inOntology.get("collection").get(0);
    assertTrue(provisional.get("provisional").asBoolean());
    assertEquals(ROOT, provisional.get("subclassOf").asText());
    assertEquals(HEART, provisional.get("relations").get(0).get("targetClassId").asText());

    // The route takes the relation's short identifier.
    JsonNode relation = get("/bioportal/relations/" + PROVISIONAL_RELATION.substring(PROVISIONAL_RELATION.lastIndexOf('/') + 1));
    assertEquals(HEART, relation.get("targetClassId").asText(), relation.toString());
  }

  @Test
  void aPropertyIsReadWithItsHierarchy() {
    String properties = "/bioportal/ontologies/" + ONTOLOGY + "/properties/";
    JsonNode hasPart = get(properties + enc(HAS_PART));
    assertEquals("has part", hasPart.get("prefLabel").asText());
    assertEquals(List.of("has part"), strings(hasPart.get("labels")));
    assertTrue(hasPart.get("hasChildren").asBoolean());

    assertEquals(2, get("/bioportal/ontologies/" + ONTOLOGY + "/properties").size());
    assertEquals(List.of("has chamber"), labels(get(properties + enc(HAS_PART) + "/children")));
    assertEquals(List.of("has chamber"), labels(get(properties + enc(HAS_PART) + "/descendants")));
    assertEquals(List.of("has part"), labels(get(properties + enc(HAS_CHAMBER) + "/parents")));
    assertEquals("has part", get(properties + enc(HAS_CHAMBER) + "/tree").get(0).get("prefLabel").asText());
  }

  /** A search names each result's kind: a class of an ontology, or a value set or value of a collection. */
  @Test
  void aSearchClassifiesWhatItFinds() {
    JsonNode byLabel = get("/bioportal/search?q=heart&scope=classes&sources=" + ONTOLOGY).get("collection");
    assertEquals(List.of("Heart"), labels(byLabel));
    assertEquals("OntologyClass", byLabel.get(0).get("type").asText());

    JsonNode bySynonym = get("/bioportal/search?q=cor&scope=classes&sources=" + ONTOLOGY).get("collection");
    assertEquals("synonym", bySynonym.get(0).get("matchType").asText(), bySynonym.toString());
    assertEquals(List.of("Cor"), strings(bySynonym.get(0).get("matchedSynonyms")));

    Map<String, String> kinds = new HashMap<>();
    for (JsonNode result : get("/bioportal/search?q=yes&scope=all&sources=" + VS_COLLECTION).get("collection")) {
      kinds.put(result.get("prefLabel").asText(), result.get("type").asText());
    }
    assertEquals(Map.of("Yes No", "ValueSet", "Yes", "Value"), kinds);

    JsonNode properties = get("/bioportal/property_search?q=part&sources=" + ONTOLOGY).get("collection");
    assertEquals(List.of("has part"), labels(properties));
  }

  @Test
  void valueSetsAndTheirValuesAreRead() {
    String collection = "/bioportal/vs-collections/" + VS_COLLECTION;
    assertTrue(ids(get("/bioportal/vs-collections")).contains(VS_COLLECTION));
    assertEquals(List.of("Yes No"), labels(get(collection + "/value-sets").get("collection")));
    assertEquals("Yes No", get(collection + "/value-sets/" + enc(VALUE_SET)).get("prefLabel").asText());

    JsonNode values = get(collection + "/value-sets/" + enc(VALUE_SET) + "/values").get("collection");
    assertEquals(List.of("Yes", "No"), labels(values));
    JsonNode yes = values.get(0);
    assertEquals("Y", yes.get("notation").asText());
    assertEquals(YES_CONCEPT, yes.get("@id").asText(), "a caDSR value is identified by the concept it matches");
    assertEquals("N", values.get(1).get("notation").asText());
    assertEquals(NO, values.get(1).get("@id").asText());

    assertEquals("Yes", get(collection + "/values/" + enc(YES)).get("prefLabel").asText());
    assertEquals("Yes No", get(collection + "/values/" + enc(YES) + "/value-set").get("prefLabel").asText());
    assertEquals(2, get(collection + "/values/" + enc(YES) + "/all-values").get("collection").size());
    JsonNode valueTree = get(collection + "/values/" + enc(YES) + "/tree");
    assertEquals("Yes No", valueTree.get("prefLabel").asText(), valueTree.toString());
    assertEquals(2, valueTree.get("children").size());
    assertEquals(2, get(collection + "/value-sets/" + enc(VALUE_SET) + "/tree").get("children").size());
    assertTrue(labels(get("/bioportal/value-sets")).contains("Yes No"));
  }

  /** The integrated search reads every source a field's constraints name, and the retrieve lists them unsearched. */
  @Test
  void theIntegratedSearchDrawsOnEverySourceAConstraintNames() {
    ObjectNode ontology = JSON.createObjectNode().put("acronym", ONTOLOGY).put("uri", BIOPORTAL.base() + "ontologies/" + ONTOLOGY)
        .put("name", "Test Ontology");
    ObjectNode branch = JSON.createObjectNode().put("acronym", ONTOLOGY).put("uri", ORGAN).put("name", "Organ")
        .put("source", "Test Ontology (" + ONTOLOGY + ")").put("maxDepth", 3);
    ObjectNode valueSet = JSON.createObjectNode().put("vsCollection", VS_COLLECTION).put("uri", VALUE_SET).put("name", "Yes No");

    assertEquals(List.of("Heart"), labels(integrated("integrated-search", "heart", constraints("ontologies", ontology))));
    assertEquals(List.of("Heart"), labels(integrated("integrated-search", "heart", constraints("branches", branch))));
    assertEquals(List.of("Yes"), labels(integrated("integrated-search", "yes", constraints("valueSets", valueSet))));

    ObjectNode both = constraints("ontologies", ontology);
    both.withArray("valueSets").add(valueSet);
    List<String> everything = labels(integrated("integrated-search", null, both));
    assertTrue(everything.containsAll(List.of("Root", "Organ", "Tissue", "Heart", "Yes", "No")), everything.toString());

    assertEquals(List.of("Yes", "No"), labels(integrated("integrated-retrieve", null, constraints("valueSets", valueSet))));
  }

  /** A term is checked against a field's constraints: passed when a constraint holds it, failed when none does. */
  @Test
  void aTermIsVerifiedAgainstTheFieldsConstraints() {
    ObjectNode ontology = JSON.createObjectNode().put("acronym", ONTOLOGY).put("uri", BIOPORTAL.base() + "ontologies/" + ONTOLOGY)
        .put("name", "Test Ontology");
    assertEquals("passed", verify(HEART, constraints("ontologies", ontology)).path("status").asText());
    assertEquals("failed", verify(NS + "Spleen", constraints("ontologies", ontology)).path("status").asText());
  }

  private static ObjectNode constraints(String kind, ObjectNode constraint) {
    ObjectNode constraints = JSON.createObjectNode();
    for (String each : List.of("ontologies", "branches", "valueSets", "classes")) {
      constraints.putArray(each);
    }
    constraints.withArray(kind).add(constraint);
    constraints.put("multipleChoice", false);
    return constraints;
  }

  private static JsonNode integrated(String route, String text, ObjectNode valueConstraints) {
    ObjectNode body = JSON.createObjectNode();
    if (route.equals("integrated-retrieve")) {
      // The retrieve lists a field's terms unsearched, so it takes the constraints alone.
      body.set("valueConstraints", valueConstraints);
    } else {
      ObjectNode parameters = body.putObject("parameterObject");
      if (text != null) {
        parameters.put("inputText", text);
      }
      parameters.set("valueConstraints", valueConstraints);
    }
    body.put("page", 1).put("pageSize", 50);
    return post("/bioportal/" + route, body).get("collection");
  }

  private static JsonNode verify(String iri, ObjectNode valueConstraints) {
    ObjectNode body = JSON.createObjectNode().put("iri", iri);
    body.set("valueConstraints", valueConstraints);
    return post("/bioportal/validate-term", body);
  }

  private static JsonNode get(String route) {
    return send(HttpRequest.newBuilder(URI.create("http://localhost:" + SERVER.getLocalPort() + route))
        .header("Authorization", authHeader).GET().build(), route);
  }

  private static JsonNode post(String route, JsonNode body) {
    return send(HttpRequest.newBuilder(URI.create("http://localhost:" + SERVER.getLocalPort() + route))
        .header("Authorization", authHeader).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), route + " " + body);
  }

  private static JsonNode send(HttpRequest request, String description) {
    try {
      HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode(), description + " answered " + response.body());
      return JSON.readTree(response.body());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static List<String> labels(JsonNode items) {
    List<String> labels = new ArrayList<>();
    items.forEach(item -> labels.add(item.has("prefLabel") ? item.get("prefLabel").asText() : item.path("name").asText()));
    return labels;
  }

  private static List<String> ids(JsonNode items) {
    List<String> ids = new ArrayList<>();
    items.forEach(item -> ids.add(item.get("id").asText()));
    return ids;
  }

  private static List<String> strings(JsonNode array) {
    List<String> strings = new ArrayList<>();
    array.forEach(item -> strings.add(item.asText()));
    return strings;
  }

  private static String enc(String iri) {
    return URLEncoder.encode(iri, StandardCharsets.UTF_8);
  }

  private static FakeBioPortal startBioPortal() {
    try {
      return new FakeBioPortal();
    } catch (IOException e) {
      throw new IllegalStateException("Could not start the fake BioPortal", e);
    }
  }
}
