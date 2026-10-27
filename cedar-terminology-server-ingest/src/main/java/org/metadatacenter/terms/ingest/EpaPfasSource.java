package org.metadatacenter.terms.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * EPA's two drinking-water method lists, resolved to CompTox substance identities. The archive
 * contains the normalized collection AND every original API response; it can be re-extracted
 * offline without asking either EPA service for a historical state it may no longer serve.
 */
public final class EpaPfasSource implements SubmissionSource {
  public static final String ACRONYM = "EPA-PFAS";
  public static final String FORMAT = "EPA-PFAS-JSON";
  public static final String COLLECTION_IRI = "https://metadatacenter.org/terminology/value-sets/epa-pfas";
  public static final String COMBINED_IRI = COLLECTION_IRI + "/drinking-water-533-537-1";
  public static final String CHEMICAL_IRI = "https://comptox.epa.gov/dashboard/chemical/details/";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final List<MethodList> METHODS = List.of(
      new MethodList(477, "PFAS Method 533", "PFAS — EPA Method 533"),
      new MethodList(475, "PFAS Method 537.1", "PFAS — EPA Method 537.1"));
  private record MethodList(int id, String acronym, String label) {}

  private final String apiKey;
  private final URI srs;
  private final URI ctx;
  private final HttpClient http;

  public EpaPfasSource(String apiKey) {
    this(apiKey, URI.create("https://cdxapps.epa.gov/oms-substance-registry-services/"),
        URI.create("https://comptox.epa.gov/ctx-api/"));
  }

  // Alternate endpoints are available only to the embedded HTTP tests, never as CLI key targets.
  EpaPfasSource(String apiKey, URI srs, URI ctx) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("CEDAR_COMP_TOX_API_KEY is required for EPA PFAS ingestion");
    }
    this.apiKey = apiKey;
    this.srs = srs;
    this.ctx = ctx;
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NEVER).build();
  }

  @Override public String backendId() { return "epa"; }

  @Override public org.metadatacenter.terms.store.SourcePresentation presentation(String acronym) {
    requireCollection(acronym);
    return presentation();
  }

  static org.metadatacenter.terms.store.SourcePresentation presentation() {
    var pages = new LinkedHashMap<String, List<org.metadatacenter.terms.store.SourcePresentation.LinkPattern>>();
    for (MethodList method : METHODS) {
      String page = "https://cdxapps.epa.gov/oms-substance-registry-services/substance-list-details/" + method.id();
      pages.put(page, List.of(new org.metadatacenter.terms.store.SourcePresentation.LinkPattern(
          "landing", page, "EPA Substance Registry Services", "current")));
    }
    // A locally composed set has no upstream page. Its constituent selections retain their own links.
    pages.put(COMBINED_IRI, List.of());
    return new org.metadatacenter.terms.store.SourcePresentation("epa", "EPA",
        Map.of("term", List.of(new org.metadatacenter.terms.store.SourcePresentation.LinkPattern(
            "landing", "{resourceIriRaw}", "CompTox", "current"))), pages,
        Map.of("vocabulary", List.of("search", "versions"), "valueSet", List.of("search", "members", "versions"),
            "term", List.of("versions")));
  }

  @Override public OntologyAccess accessInfo(String acronym) {
    requireCollection(acronym);
    return new OntologyAccess("public", null, "EPA PFAS drinking-water methods");
  }

  @Override public List<Submission> listSubmissions(String acronym) {
    requireCollection(acronym);
    // EPA offers live data, not an immutable release download. The retrieval time is provenance;
    // the normalized content hash, including names, is the version that a template pins.
    return List.of(new Submission(1, null, Instant.now().toString(), FORMAT));
  }

  @Override public Submission latestSubmission(String acronym) { return listSubmissions(acronym).get(0); }

  private static void requireCollection(String acronym) {
    if (!ACRONYM.equals(acronym)) throw new IllegalArgumentException("EPA PFAS source serves " + ACRONYM);
  }

  @Override public Path download(String acronym, int submissionId, Path targetDir)
      throws IOException, InterruptedException {
    requireCollection(acronym);
    if (submissionId != 1) throw new IllegalArgumentException("EPA has no historical submission API");
    ObjectNode archive = JSON.createObjectNode();
    archive.put("format", FORMAT).put("schemaVersion", 1).put("collectionIri", COLLECTION_IRI)
        .put("retrievedAt", Instant.now().toString());
    ArrayNode responses = archive.putArray("responses");
    ArrayNode sets = archive.putArray("sets");
    Map<String, List<JsonNode>> srsByCas = new TreeMap<>();
    Map<String, Set<String>> membership = new LinkedHashMap<>();
    for (MethodList method : METHODS) {
      JsonNode metadata = get(srs.resolve("api/substance-lists/" + method.id()), false, null, responses);
      if (!method.acronym().equals(text(metadata, "subsListAcronym"))) {
        throw new IOException("SRS list identity does not match " + method.acronym());
      }
      JsonNode substances = get(srs.resolve("rest-api/substances/list_acronym/" + encode(method.acronym())),
          false, null, responses);
      requireArray(substances, "SRS " + method.acronym());
      int expected = metadata.path("synonymCount").asInt(-1);
      if (expected <= 0 || substances.size() != expected) {
        throw new IOException("Incomplete SRS " + method.acronym() + ": expected " + expected
            + " records, received " + substances.size());
      }
      Set<String> members = new TreeSet<>();
      for (JsonNode substance : substances) {
        String cas = text(substance, "currentCasNumber");
        if (!cas.matches("[0-9]{2,7}-[0-9]{2}-[0-9]")) throw new IOException("SRS member has no current CAS number");
        if (!members.add(cas)) throw new IOException("Duplicate CAS in SRS " + method.acronym() + ": " + cas);
        srsByCas.computeIfAbsent(cas, ignored -> new ArrayList<>()).add(substance);
      }
      String iri = "https://cdxapps.epa.gov/oms-substance-registry-services/substance-list-details/" + method.id();
      sets.addObject().put("iri", iri).put("label", method.label()).put("sourceList", method.acronym());
      membership.put(iri, members);
    }

    Map<String, String> dtxsidByCas = new TreeMap<>();
    for (String cas : srsByCas.keySet()) {
      JsonNode matches = get(ctx.resolve("chemical/search/equal/" + encode(cas)), true, null, responses);
      requireArray(matches, "CompTox CAS lookup");
      Set<String> ids = new TreeSet<>();
      for (JsonNode match : matches) {
        if (cas.equals(text(match, "casrn"))) ids.add(dtxsid(match));
      }
      if (ids.size() != 1) throw new IOException("CAS " + cas + " has " + ids.size() + " exact CompTox identities");
      String id = ids.iterator().next();
      for (JsonNode record : srsByCas.get(cas)) {
        String crossReference = text(record, "dtxsid");
        if (!crossReference.isEmpty() && !crossReference.equals(id)) {
          throw new IOException("SRS and CompTox disagree on the DTXSID for CAS " + cas);
        }
      }
      dtxsidByCas.put(cas, id);
    }
    if (new HashSet<>(dtxsidByCas.values()).size() != dtxsidByCas.size()) {
      throw new IOException("Different current CAS numbers resolve to the same CompTox substance");
    }
    List<String> ids = dtxsidByCas.values().stream().sorted().toList();
    Map<String, JsonNode> details = byDtxsid(get(ctx.resolve("chemical/detail/search/by-dtxsid/"),
        true, ids, responses), ids, "details", true);
    Map<String, JsonNode> synonyms = byDtxsid(get(ctx.resolve("chemical/synonym/search/by-dtxsid/"),
        true, ids, responses), ids, "synonyms", false);
    ArrayNode chemicals = archive.putArray("chemicals");
    for (var entry : dtxsidByCas.entrySet()) {
      String cas = entry.getKey(), id = entry.getValue();
      JsonNode detail = details.get(id);
      String label = text(detail, "preferredName");
      if (!cas.equals(text(detail, "casrn")) || label.isEmpty()) {
        throw new IOException("CompTox details do not identify CAS " + cas);
      }
      Set<String> names = new TreeSet<>(List.of(cas, id, label));
      for (JsonNode record : srsByCas.get(cas)) {
        addName(names, text(record, "epaName"));
        addName(names, text(record, "systematicName"));
        for (JsonNode synonym : record.path("synonyms")) {
          if (!"false".equalsIgnoreCase(synonym.path("activeStatus").asText())) {
            addName(names, text(synonym, "synonymName"));
          }
        }
      }
      JsonNode synonym = synonyms.get(id);
      if (synonym != null) {
        // Keep EPA's preferred-quality groups; never index deleted or low-quality "other" names.
        for (String group : List.of("valid", "good", "beilstein")) {
          for (JsonNode name : synonym.path(group)) addName(names, name.asText());
        }
      }
      ObjectNode chemical = chemicals.addObject().put("dtxsid", id).put("casrn", cas).put("label", label);
      names.forEach(chemical.putArray("names")::add);
    }
    for (JsonNode set : sets) {
      ArrayNode members = ((ObjectNode) set).putArray("members");
      membership.get(set.path("iri").asText()).stream().map(dtxsidByCas::get).sorted().forEach(members::add);
    }
    ObjectNode combined = sets.addObject().put("iri", COMBINED_IRI)
        .put("label", "PFAS — Drinking water, combined methods 533 and 537.1")
        .put("derivation", "Union of the two EPA SRS method lists by exact DTXSID; salts are not substituted");
    ids.forEach(combined.putArray("members")::add);
    Files.createDirectories(targetDir);
    Path file = Files.createTempFile(targetDir, "epa-pfas-", ".json");
    JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), archive);
    return file;
  }

  private JsonNode get(URI uri, boolean authenticated, Object payload, ArrayNode responses)
      throws IOException, InterruptedException {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
        .header("Accept", "application/json");
    if (authenticated) request.header("x-api-key", apiKey);
    if (payload != null) request.header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)));
    HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      // Never include upstream bodies or headers: a proxy may echo the credential.
      throw new IOException("EPA " + uri.getPath() + " returned HTTP " + response.statusCode());
    }
    JsonNode parsed;
    try { parsed = JSON.readTree(response.body()); }
    catch (IOException badJson) { throw new IOException("EPA returned invalid JSON at " + uri.getPath()); }
    if (parsed == null) throw new IOException("EPA returned an empty body at " + uri.getPath());
    ObjectNode evidence = responses.addObject().put("url", uri.toString())
        .put("method", payload == null ? "GET" : "POST").put("body", response.body());
    if (payload != null) evidence.set("requestBody", JSON.valueToTree(payload));
    return parsed;
  }

  private static Map<String, JsonNode> byDtxsid(JsonNode records, List<String> ids, String kind, boolean complete)
      throws IOException {
    requireArray(records, "CompTox " + kind);
    Map<String, JsonNode> result = new TreeMap<>();
    for (JsonNode record : records) {
      String id = dtxsid(record);
      if (!ids.contains(id) || result.put(id, record) != null) throw new IOException("Unexpected CompTox " + kind + " identity");
    }
    if (complete && result.size() != ids.size()) throw new IOException("Incomplete CompTox " + kind);
    return result;
  }

  static String dtxsid(JsonNode node) throws IOException {
    String id = text(node, "dtxsid");
    if (!id.matches("DTXSID[0-9]+")) throw new IOException("Invalid CompTox substance identifier");
    return id;
  }

  static String text(JsonNode node, String key) {
    return node.path(key).isTextual() ? node.path(key).asText().trim() : "";
  }
  static void requireArray(JsonNode node, String context) throws IOException {
    if (!node.isArray()) throw new IOException(context + " must return an array");
  }
  private static void addName(Set<String> names, String name) { if (!name.isBlank()) names.add(name.trim()); }
  private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
}
