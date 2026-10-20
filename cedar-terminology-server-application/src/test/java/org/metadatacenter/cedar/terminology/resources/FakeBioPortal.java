package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * A BioPortal holding one small world, answering the REST calls the terminology server makes as BioPortal does.
 * <p>
 * TESTONT is an ontology: Root has the children Organ and Tissue, and Organ has the child Heart, whose synonym is
 * Cor. It has the properties hasPart and its child hasChamber, and one provisional class, filed under Root, with one
 * provisional relation. CADSR-VS is a value-set collection holding the value set Yes No, with the values Yes and No;
 * the two other collections the server reads exist and are empty.
 */
public final class FakeBioPortal implements AutoCloseable {

  public static final String ONTOLOGY = "TESTONT";
  public static final String VS_COLLECTION = "CADSR-VS";
  public static final String NS = "http://example.org/test#";
  public static final String ROOT = NS + "Root";
  public static final String ORGAN = NS + "Organ";
  public static final String TISSUE = NS + "Tissue";
  public static final String HEART = NS + "Heart";
  public static final String HAS_PART = NS + "hasPart";
  public static final String HAS_CHAMBER = NS + "hasChamber";
  public static final String VALUE_SET = "https://cadsr.nci.nih.gov/metadata/CADSR-VS/VD100v1";
  public static final String YES = "https://cadsr.nci.nih.gov/metadata/CADSR-VS/VM1v1";
  public static final String NO = "https://cadsr.nci.nih.gov/metadata/CADSR-VS/VM2v1";
  public static final String YES_CONCEPT = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C49488";
  public static final String PROVISIONAL_CLASS =
      "https://data.bioontology.org/provisional_classes/0b9e1f8a-1111-2222-3333-444455556666";
  public static final String PROVISIONAL_RELATION =
      "https://data.bioontology.org/provisional_relations/7c2d1e3f-1111-2222-3333-444455556666";
  private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";

  private record Term(String iri, String label, String ontology, String parent, List<String> synonyms,
                      Map<String, List<String>> properties) {
  }

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final List<Term> CLASSES = List.of(
      new Term(ROOT, "Root", ONTOLOGY, null, List.of(), Map.of()),
      new Term(ORGAN, "Organ", ONTOLOGY, ROOT, List.of(), Map.of()),
      new Term(TISSUE, "Tissue", ONTOLOGY, ROOT, List.of(), Map.of()),
      new Term(HEART, "Heart", ONTOLOGY, ORGAN, List.of("Cor"), Map.of()),
      new Term(VALUE_SET, "Yes No", VS_COLLECTION, null, List.of(), Map.of()),
      new Term(YES, "Yes", VS_COLLECTION, VALUE_SET, List.of(),
          Map.of(SKOS + "notation", List.of("Y"), SKOS + "relatedMatch", List.of(YES_CONCEPT))),
      new Term(NO, "No", VS_COLLECTION, VALUE_SET, List.of(), Map.of(SKOS + "notation", List.of("N"))));
  private static final List<Term> PROPERTIES = List.of(
      new Term(HAS_PART, "has part", ONTOLOGY, null, List.of(), Map.of()),
      new Term(HAS_CHAMBER, "has chamber", ONTOLOGY, HAS_PART, List.of(), Map.of()));

  private final HttpServer server;
  private final List<String> requests = new CopyOnWriteArrayList<>();

  public FakeBioPortal() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  /** The base address, as CEDAR_BIOPORTAL_REST_BASE takes it. */
  public String base() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
  }

  /** The paths and queries asked so far. */
  public List<String> requests() {
    return List.copyOf(requests);
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    String rawPath = exchange.getRequestURI().getRawPath();
    String rawQuery = exchange.getRequestURI().getRawQuery();
    requests.add(rawPath + (rawQuery == null ? "" : "?" + rawQuery));
    Map<String, String> query = query(rawQuery);
    List<String> path = new ArrayList<>();
    for (String segment : rawPath.substring(1).split("/")) {
      path.add(URLDecoder.decode(segment, StandardCharsets.UTF_8));
    }
    Object answer;
    try {
      answer = answer(path, query);
    } catch (IllegalArgumentException notFound) {
      answer = null;
    }
    byte[] body = (answer == null ? "{\"errors\": [\"Not found\"]}" : JSON.writeValueAsString(answer))
        .getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(answer == null ? 404 : 200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private Object answer(List<String> path, Map<String, String> query) {
    String first = path.get(0);
    if (first.equals("search")) {
      return search(query);
    }
    if (first.equals("property_search")) {
      return paged(matching(PROPERTIES, query.get("q"), query.get("ontologies")).stream().map(this::property).toList(), query);
    }
    if (first.equals("provisional_classes")) {
      return path.size() == 1 ? paged(List.of(provisionalClass()), query) : provisionalClassFor(path.get(1));
    }
    if (first.equals("provisional_relations") && path.size() == 2) {
      return path.get(1).equals(shortId(PROVISIONAL_RELATION)) || path.get(1).equals(PROVISIONAL_RELATION)
          ? provisionalRelation() : null;
    }
    if (!first.equals("ontologies")) {
      return null;
    }
    if (path.size() == 1) {
      ArrayNode ontologies = JSON.createArrayNode();
      for (String acronym : List.of(ONTOLOGY, "CEDARVS", "NLMVS", VS_COLLECTION)) {
        ontologies.add(ontology(acronym));
      }
      // The server refuses a listing shorter than BioPortal's real one as a failed load.
      for (int i = 1; i <= 100; i++) {
        ontologies.add(ontology(String.format("FILLER%03d", i)));
      }
      return ontologies;
    }
    String acronym = path.get(1);
    if (!List.of(ONTOLOGY, "CEDARVS", "NLMVS", VS_COLLECTION).contains(acronym)) {
      return null;
    }
    if (path.size() == 2) {
      return ontology(acronym);
    }
    switch (path.get(2)) {
      case "latest_submission":
        return submission();
      case "metrics":
        return metrics(acronym);
      case "categories":
        return JSON.createArrayNode().add(JSON.createObjectNode().put("@id", base() + "categories/ANAT")
            .put("acronym", "ANAT").put("name", "Anatomy").put("description", "Anatomy ontologies"));
      case "provisional_classes":
        // Within an ontology BioPortal answers a plain list rather than a page.
        return array(acronym.equals(ONTOLOGY) ? List.of(provisionalClass()) : List.of());
      case "classes":
        return classes(acronym, path.subList(3, path.size()), query);
      case "properties":
        return properties(path.subList(3, path.size()));
      default:
        return null;
    }
  }

  private Object classes(String acronym, List<String> rest, Map<String, String> query) {
    List<Term> inOntology = CLASSES.stream().filter(t -> t.ontology().equals(acronym)).toList();
    if (rest.isEmpty()) {
      return paged(inOntology.stream().map(this::bpClass).toList(), query);
    }
    if (rest.get(0).equals("roots")) {
      return array(inOntology.stream().filter(t -> t.parent() == null).map(this::bpClass).toList());
    }
    Term term = find(CLASSES, rest.get(0));
    if (rest.size() == 1) {
      return bpClass(term);
    }
    return switch (rest.get(1)) {
      case "children" -> paged(childrenOf(CLASSES, term.iri()).stream().map(this::bpClass).toList(), query);
      case "descendants" -> paged(descendantsOf(CLASSES, term.iri()).stream().map(this::bpClass).toList(), query);
      case "parents" -> array(term.parent() == null ? List.of() : List.of(bpClass(find(CLASSES, term.parent()))));
      case "tree" -> tree(CLASSES, term);
      default -> null;
    };
  }

  private Object properties(List<String> rest) {
    if (rest.isEmpty()) {
      return array(PROPERTIES.stream().map(this::property).toList());
    }
    if (rest.get(0).equals("roots")) {
      return array(PROPERTIES.stream().filter(p -> p.parent() == null).map(this::property).toList());
    }
    Term property = find(PROPERTIES, rest.get(0));
    if (rest.size() == 1) {
      return property(property);
    }
    return switch (rest.get(1)) {
      case "children" -> array(childrenOf(PROPERTIES, property.iri()).stream().map(this::property).toList());
      case "descendants" -> array(descendantsOf(PROPERTIES, property.iri()).stream().map(this::property).toList());
      case "parents" -> array(property.parent() == null ? List.of() : List.of(property(find(PROPERTIES, property.parent()))));
      case "tree" -> tree(PROPERTIES, property);
      default -> null;
    };
  }

  /** A search by text, within a subtree, or for a collection's value sets. */
  private Object search(Map<String, String> query) {
    List<Term> found;
    if ("true".equals(query.get("valueset_roots_only")) && query.get("q") == null) {
      String collection = query.get("ontologies");
      found = CLASSES.stream().filter(t -> t.ontology().equals(collection) && t.parent() == null).toList();
    } else {
      String ontologies = query.containsKey("ontology") ? query.get("ontology") : query.get("ontologies");
      found = matching(CLASSES, query.get("q"), ontologies);
      if (query.containsKey("subtree_root_id")) {
        List<Term> subtree = descendantsOf(CLASSES, query.get("subtree_root_id"));
        found = found.stream().filter(subtree::contains).toList();
      }
    }
    String q = query.get("q");
    List<ObjectNode> results = new ArrayList<>();
    for (Term term : found) {
      ObjectNode result = bpClass(term);
      boolean byLabel = q == null || term.label().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));
      result.put("matchType", byLabel ? "prefLabel" : "synonym");
      results.add(result);
    }
    return paged(results, query);
  }

  private List<Term> matching(List<Term> terms, String q, String ontologies) {
    List<String> sources = ontologies == null ? null : Arrays.asList(ontologies.split(","));
    Predicate<Term> inSources = t -> sources == null || sources.contains(t.ontology());
    Predicate<Term> matches = t -> q == null || q.isEmpty() || t.label().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))
        || t.synonyms().stream().anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)));
    return terms.stream().filter(inSources.and(matches)).toList();
  }

  private ObjectNode ontology(String acronym) {
    boolean collection = List.of("CEDARVS", "NLMVS", VS_COLLECTION).contains(acronym);
    return JSON.createObjectNode().put("@id", base() + "ontologies/" + acronym)
        .put("@type", "http://data.bioontology.org/metadata/Ontology").put("acronym", acronym)
        .put("name", acronym.equals(ONTOLOGY) ? "Test Ontology" : acronym + (collection ? " value sets" : ""))
        .put("ontologyType", base() + "ontology_types/" + (collection ? "VALUE_SET_COLLECTION" : "ONTOLOGY"))
        .put("flat", false).put("summaryOnly", false);
  }

  private ObjectNode submission() {
    ObjectNode submission = JSON.createObjectNode().put("hasOntologyLanguage", "OWL").put("released", "2026-10-01T00:00:00Z")
        .put("creationDate", "2026-10-02T00:00:00Z").put("homepage", "https://example.org/test")
        .put("documentation", "https://example.org/test/docs").put("version", "2026-10").put("description", "A test ontology");
    submission.putArray("publication").add("https://example.org/test/paper");
    return submission;
  }

  private ObjectNode metrics(String acronym) {
    long classes = CLASSES.stream().filter(t -> t.ontology().equals(acronym)).count();
    return JSON.createObjectNode().put("id", base() + "ontologies/" + acronym + "/submissions/1/metrics")
        .put("created", "2026-10-02T00:00:00Z")
        .put("classes", String.valueOf(classes)).put("individuals", "0").put("properties", String.valueOf(PROPERTIES.size()))
        .put("maxDepth", "3").put("maxChildCount", "2").put("averageChildCount", "1").put("classesWithOneChild", "1")
        .put("classesWithMoreThan25Children", "0").put("classesWithNoDefinition", "0");
  }

  private ObjectNode bpClass(Term term) {
    ObjectNode node = JSON.createObjectNode().put("@id", term.iri()).put("@type", "http://www.w3.org/2002/07/owl#Class")
        .put("prefLabel", term.label()).put("hasChildren", !childrenOf(CLASSES, term.iri()).isEmpty())
        .put("provisional", false);
    ArrayNode synonyms = node.putArray("synonym");
    term.synonyms().forEach(synonyms::add);
    node.putArray("definition").add("The " + term.label().toLowerCase(Locale.ROOT) + ".");
    ObjectNode properties = node.putObject("properties");
    term.properties().forEach((key, values) -> {
      ArrayNode list = properties.putArray(key);
      values.forEach(list::add);
    });
    node.set("links", links(term, "classes"));
    return node;
  }

  private ObjectNode property(Term term) {
    ObjectNode node = JSON.createObjectNode().put("@id", term.iri()).put("@type", "http://www.w3.org/2002/07/owl#ObjectProperty")
        .put("propertyType", "object").put("ontologyType", "ONTOLOGY")
        .put("hasChildren", !childrenOf(PROPERTIES, term.iri()).isEmpty());
    node.putArray("label").add(term.label());
    node.putArray("definition").add("Relates a whole to its " + term.label().replace("has ", "") + ".");
    node.set("links", links(term, "properties"));
    return node;
  }

  private ObjectNode links(Term term, String kind) {
    String self = base() + "ontologies/" + term.ontology() + "/" + kind + "/" + URLEncoder.encode(term.iri(), StandardCharsets.UTF_8);
    return JSON.createObjectNode().put("self", self).put("ontology", base() + "ontologies/" + term.ontology())
        .put("children", self + "/children").put("parents", self + "/parents").put("descendants", self + "/descendants")
        .put("tree", self + "/tree").put("ui", "https://bioportal.example.org/" + term.ontology());
  }

  private ObjectNode provisionalClass() {
    ObjectNode node = JSON.createObjectNode().put("@id", PROVISIONAL_CLASS).put("@type", "http://www.w3.org/2002/07/owl#Class")
        .put("label", "Provisional Heart").put("creator", "https://data.bioontology.org/users/cedar")
        .put("ontology", base() + "ontologies/" + ONTOLOGY).put("subclassOf", ROOT).put("provisional", true)
        .put("created", "2026-10-19T00:00:00Z");
    node.putArray("definition").add("A heart proposed by a CEDAR user.");
    node.putArray("synonym").add("Proposed heart");
    node.putArray("relations").add(provisionalRelation());
    return node;
  }

  private ObjectNode provisionalClassFor(String id) {
    return id.equals(shortId(PROVISIONAL_CLASS)) || id.equals(PROVISIONAL_CLASS) ? provisionalClass() : null;
  }

  private ObjectNode provisionalRelation() {
    return JSON.createObjectNode().put("@id", PROVISIONAL_RELATION).put("source", PROVISIONAL_CLASS)
        .put("relationType", SKOS + "closeMatch").put("targetClassId", HEART)
        .put("targetClassOntology", base() + "ontologies/" + ONTOLOGY).put("created", "2026-10-19T00:00:00Z")
        .put("creator", "https://data.bioontology.org/users/cedar");
  }

  /** The tree BioPortal returns for a term: the roots, with the branch down to the term expanded. */
  private ArrayNode tree(List<Term> terms, Term term) {
    List<String> branch = new ArrayList<>();
    for (Term t = term; t != null; t = t.parent() == null ? null : find(terms, t.parent())) {
      branch.add(0, t.iri());
    }
    ArrayNode roots = JSON.createArrayNode();
    for (Term root : terms.stream().filter(t -> t.parent() == null && t.ontology().equals(term.ontology())).toList()) {
      roots.add(treeNode(terms, root, branch));
    }
    return roots;
  }

  private ObjectNode treeNode(List<Term> terms, Term term, List<String> branch) {
    ObjectNode node = JSON.createObjectNode().put("@id", term.iri()).put("@type", "http://www.w3.org/2002/07/owl#Class")
        .put("prefLabel", term.label()).put("hasChildren", !childrenOf(terms, term.iri()).isEmpty()).put("obsolete", false);
    ArrayNode children = node.putArray("children");
    if (branch.contains(term.iri())) {
      for (Term child : childrenOf(terms, term.iri())) {
        children.add(treeNode(terms, child, branch));
      }
    }
    node.set("links", links(term, terms == PROPERTIES ? "properties" : "classes"));
    return node;
  }

  private ObjectNode paged(List<ObjectNode> all, Map<String, String> query) {
    int page = Integer.parseInt(query.getOrDefault("page", "1"));
    int size = Integer.parseInt(query.getOrDefault("pagesize", query.getOrDefault("pageSize", "50")));
    int pageCount = Math.max(1, (all.size() + size - 1) / size);
    ObjectNode paged = JSON.createObjectNode().put("page", page).put("pageCount", pageCount).put("totalCount", all.size());
    if (page > 1) {
      paged.put("prevPage", page - 1);
    } else {
      paged.putNull("prevPage");
    }
    if (page < pageCount) {
      paged.put("nextPage", page + 1);
    } else {
      paged.putNull("nextPage");
    }
    ArrayNode collection = paged.putArray("collection");
    all.stream().skip((long) (page - 1) * size).limit(size).forEach(collection::add);
    return paged;
  }

  private static ArrayNode array(List<ObjectNode> nodes) {
    ArrayNode array = JSON.createArrayNode();
    nodes.forEach(array::add);
    return array;
  }

  private static List<Term> childrenOf(List<Term> terms, String iri) {
    return terms.stream().filter(t -> iri.equals(t.parent())).toList();
  }

  private static List<Term> descendantsOf(List<Term> terms, String iri) {
    List<Term> descendants = new ArrayList<>();
    for (Term child : childrenOf(terms, iri)) {
      descendants.add(child);
      descendants.addAll(descendantsOf(terms, child.iri()));
    }
    return descendants;
  }

  private static Term find(List<Term> terms, String id) {
    for (Term term : terms) {
      if (term.iri().equals(id) || shortId(term.iri()).equals(id)) {
        return term;
      }
    }
    throw new IllegalArgumentException("no term " + id);
  }

  private static String shortId(String iri) {
    return iri.substring(Math.max(iri.lastIndexOf('/'), iri.lastIndexOf('#')) + 1);
  }

  private static Map<String, String> query(String rawQuery) {
    Map<String, String> query = new LinkedHashMap<>();
    if (rawQuery != null) {
      for (String pair : rawQuery.split("&")) {
        int equals = pair.indexOf('=');
        if (equals > 0) {
          query.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
              URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
        }
      }
    }
    return query;
  }
}
