package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
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
 * Cor. It has the properties hasPart and its child hasChamber, and two provisional classes: Provisional Heart, filed
 * under Root with one provisional relation, and Provisional Lung, filed under Organ.
 * <p>
 * VIRONT is a second ontology, with the roots Organism and Vaccine. Organism has the children Virus and Bacterium.
 * Virus has the children Coronavirus, whose synonym is CoV, and Influenza virus. Coronavirus has the children Human
 * coronavirus and Bat coronavirus. Human coronavirus has the strain 229E, a property value only a search of property
 * values finds. Vaccine has the child Coronavirus vaccine. VIRONT has the property relatedTo, its child hasHost and
 * its grandchild hasHumanHost, and the property hasHostRange, the one property in this world without a definition.
 * <p>
 * Three value-set collections hold value sets, each a root with its values as children. CADSR-VS holds Yes No, with
 * the values Yes and No. CEDARVS holds Study File Type, with four values, and Study Type and Role In Study, with one
 * value each. NLMVS holds Coronavirus Vaccines, whose three values come from another vocabulary, as NLMVS values do.
 * <p>
 * A search matches labels and synonyms, and property values when the request asks for them. It honours the ontology
 * types, value-set roots and subtree the request names. A search naming a source this world does not hold answers
 * 404, as BioPortal does, and an answer with no results reports no pages.
 */
public final class FakeBioPortal implements AutoCloseable {

  public static final String ONTOLOGY = "TESTONT";
  public static final String VIRUSES = "VIRONT";
  public static final String VS_COLLECTION = "CADSR-VS";
  public static final String CEDARVS = "CEDARVS";
  public static final String NLMVS = "NLMVS";
  /** How many filler ontologies the registry lists beside this world's two. */
  public static final int FILLERS = 100;

  public static final String NS = "http://example.org/test#";
  public static final String ROOT = NS + "Root";
  public static final String ORGAN = NS + "Organ";
  public static final String TISSUE = NS + "Tissue";
  public static final String HEART = NS + "Heart";
  public static final String HAS_PART = NS + "hasPart";
  public static final String HAS_CHAMBER = NS + "hasChamber";

  public static final String VIRUS_NS = "http://example.org/virus#";
  public static final String ORGANISM = VIRUS_NS + "Organism";
  public static final String VIRUS = VIRUS_NS + "Virus";
  public static final String CORONAVIRUS = VIRUS_NS + "Coronavirus";
  public static final String HUMAN_CORONAVIRUS = VIRUS_NS + "HumanCoronavirus";
  public static final String BAT_CORONAVIRUS = VIRUS_NS + "BatCoronavirus";
  public static final String INFLUENZA_VIRUS = VIRUS_NS + "InfluenzaVirus";
  public static final String BACTERIUM = VIRUS_NS + "Bacterium";
  public static final String VACCINE = VIRUS_NS + "Vaccine";
  public static final String CORONAVIRUS_VACCINE = VIRUS_NS + "CoronavirusVaccine";
  public static final String RELATED_TO = VIRUS_NS + "relatedTo";
  public static final String HAS_HOST = VIRUS_NS + "hasHost";
  public static final String HAS_HUMAN_HOST = VIRUS_NS + "hasHumanHost";
  public static final String HAS_HOST_RANGE = VIRUS_NS + "hasHostRange";

  public static final String VALUE_SET = "https://cadsr.nci.nih.gov/metadata/CADSR-VS/VD100v1";
  public static final String YES = "https://cadsr.nci.nih.gov/metadata/CADSR-VS/VM1v1";
  public static final String NO = "https://cadsr.nci.nih.gov/metadata/CADSR-VS/VM2v1";
  public static final String YES_CONCEPT = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C49488";

  public static final String CEDARVS_NS = "http://example.org/cedarvs#";
  public static final String STUDY_FILE_TYPE = CEDARVS_NS + "Study_File_Type";
  public static final String STUDY_MEDICATION = CEDARVS_NS + "Study_Medication";
  public static final String STUDY_TYPE = CEDARVS_NS + "Study_Type";
  public static final String ROLE_IN_STUDY = CEDARVS_NS + "Role_In_Study";

  public static final String CORONAVIRUS_VACCINES = "http://example.org/nlmvs#Coronavirus_Vaccines";
  public static final String MRNA_VACCINE = "http://example.org/cvx#207";
  public static final String VECTOR_VACCINE = "http://example.org/cvx#212";
  public static final String INACTIVATED_VACCINE = "http://example.org/cvx#510";

  public static final String PROVISIONAL_CLASS =
      "https://data.bioontology.org/provisional_classes/0b9e1f8a-1111-2222-3333-444455556666";
  public static final String PROVISIONAL_LUNG =
      "https://data.bioontology.org/provisional_classes/1f0a2e9b-1111-2222-3333-444455556666";
  public static final String PROVISIONAL_RELATION =
      "https://data.bioontology.org/provisional_relations/7c2d1e3f-1111-2222-3333-444455556666";
  private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";

  private static final List<String> COLLECTIONS = List.of(CEDARVS, NLMVS, VS_COLLECTION);
  /** The ontologies and collections this world holds, in the order the registry lists them. */
  private static final List<String> SOURCES = List.of(ONTOLOGY, VIRUSES, CEDARVS, NLMVS, VS_COLLECTION);

  /** A class, value set, value or property. A null definition means the term has none. */
  private record Term(String iri, String label, String ontology, String parent, String definition,
                      List<String> synonyms, Map<String, List<String>> properties) {

    Term with(Map<String, List<String>> properties) {
      return new Term(iri, label, ontology, parent, definition, synonyms, properties);
    }
  }

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final List<Term> CLASSES = List.of(
      term(ROOT, "Root", ONTOLOGY, null),
      term(ORGAN, "Organ", ONTOLOGY, ROOT),
      term(TISSUE, "Tissue", ONTOLOGY, ROOT),
      term(HEART, "Heart", ONTOLOGY, ORGAN, "Cor"),
      term(ORGANISM, "Organism", VIRUSES, null),
      term(VIRUS, "Virus", VIRUSES, ORGANISM),
      term(CORONAVIRUS, "Coronavirus", VIRUSES, VIRUS, "CoV"),
      term(HUMAN_CORONAVIRUS, "Human coronavirus", VIRUSES, CORONAVIRUS)
          .with(Map.of(VIRUS_NS + "strain", List.of("229E"))),
      term(BAT_CORONAVIRUS, "Bat coronavirus", VIRUSES, CORONAVIRUS),
      term(INFLUENZA_VIRUS, "Influenza virus", VIRUSES, VIRUS),
      term(BACTERIUM, "Bacterium", VIRUSES, ORGANISM),
      term(VACCINE, "Vaccine", VIRUSES, null),
      term(CORONAVIRUS_VACCINE, "Coronavirus vaccine", VIRUSES, VACCINE),
      term(VALUE_SET, "Yes No", VS_COLLECTION, null),
      term(YES, "Yes", VS_COLLECTION, VALUE_SET)
          .with(Map.of(SKOS + "notation", List.of("Y"), SKOS + "relatedMatch", List.of(YES_CONCEPT))),
      term(NO, "No", VS_COLLECTION, VALUE_SET).with(Map.of(SKOS + "notation", List.of("N"))),
      term(STUDY_FILE_TYPE, "Study File Type", CEDARVS, null),
      term(STUDY_MEDICATION, "Study Medication", CEDARVS, STUDY_FILE_TYPE),
      term(CEDARVS_NS + "Adverse_Events", "Adverse Events", CEDARVS, STUDY_FILE_TYPE),
      term(CEDARVS_NS + "Demographics", "Demographics", CEDARVS, STUDY_FILE_TYPE),
      term(CEDARVS_NS + "Lab_Test_Results", "Lab Test Results", CEDARVS, STUDY_FILE_TYPE),
      term(STUDY_TYPE, "Study Type", CEDARVS, null),
      term(CEDARVS_NS + "Interventional", "Interventional", CEDARVS, STUDY_TYPE),
      term(ROLE_IN_STUDY, "Role In Study", CEDARVS, null),
      term(CEDARVS_NS + "Investigator", "Investigator", CEDARVS, ROLE_IN_STUDY),
      term(CORONAVIRUS_VACCINES, "Coronavirus Vaccines", NLMVS, null),
      term(MRNA_VACCINE, "mRNA coronavirus vaccine", NLMVS, CORONAVIRUS_VACCINES),
      term(VECTOR_VACCINE, "Viral vector coronavirus vaccine", NLMVS, CORONAVIRUS_VACCINES),
      term(INACTIVATED_VACCINE, "Inactivated virus vaccine", NLMVS, CORONAVIRUS_VACCINES));
  private static final List<Term> PROPERTIES = List.of(
      new Term(HAS_PART, "has part", ONTOLOGY, null, "Relates a whole to its part.", List.of(), Map.of()),
      new Term(HAS_CHAMBER, "has chamber", ONTOLOGY, HAS_PART, "Relates a whole to its chamber.", List.of(), Map.of()),
      new Term(RELATED_TO, "related to", VIRUSES, null, "Relates one organism to another.", List.of(), Map.of()),
      new Term(HAS_HOST, "has host", VIRUSES, RELATED_TO, "Relates a virus to an organism it infects.", List.of(),
          Map.of()),
      new Term(HAS_HUMAN_HOST, "has human host", VIRUSES, HAS_HOST, "Relates a virus to the people it infects.",
          List.of(), Map.of()),
      new Term(HAS_HOST_RANGE, "has host range", VIRUSES, null, null, List.of(), Map.of()));

  private final HttpServer server;
  private final List<String> requests = new CopyOnWriteArrayList<>();

  public FakeBioPortal() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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

  /** A class or value whose definition is drawn from its label, as every class and value here is. */
  private static Term term(String iri, String label, String ontology, String parent, String... synonyms) {
    return new Term(iri, label, ontology, parent, "The " + label.toLowerCase(Locale.ROOT) + ".", List.of(synonyms),
        Map.of());
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
      return propertySearch(query);
    }
    if (first.equals("provisional_classes")) {
      return path.size() == 1 ? paged(provisionalClasses(), query) : provisionalClassFor(path.get(1));
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
      SOURCES.forEach(acronym -> ontologies.add(ontology(acronym)));
      // The server refuses a listing shorter than BioPortal's real one as a failed load.
      for (int i = 1; i <= FILLERS; i++) {
        ontologies.add(ontology(String.format("FILLER%03d", i)));
      }
      return ontologies;
    }
    String acronym = path.get(1);
    if (!SOURCES.contains(acronym)) {
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
        return array(acronym.equals(ONTOLOGY) ? provisionalClasses() : List.of());
      case "classes":
        return classes(acronym, path.subList(3, path.size()), query);
      case "properties":
        return properties(acronym, path.subList(3, path.size()));
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
    Term term = find(inOntology, rest.get(0));
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

  private Object properties(String acronym, List<String> rest) {
    List<Term> inOntology = PROPERTIES.stream().filter(p -> p.ontology().equals(acronym)).toList();
    if (rest.isEmpty()) {
      return array(inOntology.stream().map(this::property).toList());
    }
    if (rest.get(0).equals("roots")) {
      return array(inOntology.stream().filter(p -> p.parent() == null).map(this::property).toList());
    }
    Term property = find(inOntology, rest.get(0));
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

  /**
   * A search by text, within a subtree, or for a collection's value sets. An ontology type keeps the ontologies'
   * classes or the collections' terms; value-set roots are kept alone or left out on request.
   */
  private Object search(Map<String, String> query) {
    List<String> sources = sources(query.containsKey("ontology") ? query.get("ontology") : query.get("ontologies"));
    if (sources != null && !SOURCES.containsAll(sources)) {
      return null;
    }
    String q = query.get("q");
    List<Term> found = matching(CLASSES, q, sources, "true".equals(query.get("also_search_properties")));
    if (query.containsKey("ontology_types")) {
      boolean collections = query.get("ontology_types").equals("VALUE_SET_COLLECTION");
      found = found.stream().filter(t -> COLLECTIONS.contains(t.ontology()) == collections).toList();
    }
    if ("true".equals(query.get("valueset_roots_only"))) {
      found = found.stream().filter(FakeBioPortal::isValueSet).toList();
    }
    if ("true".equals(query.get("valueset_exclude_roots"))) {
      found = found.stream().filter(t -> !isValueSet(t)).toList();
    }
    if (query.containsKey("subtree_root_id")) {
      List<Term> subtree = descendantsOf(CLASSES, query.get("subtree_root_id"));
      found = found.stream().filter(subtree::contains).toList();
    }
    List<ObjectNode> results = new ArrayList<>();
    for (Term term : found) {
      ObjectNode result = bpClass(term);
      String matchType = q == null || contains(term.label(), q) ? "prefLabel"
          : term.synonyms().stream().anyMatch(s -> contains(s, q)) ? "synonym" : "property";
      result.put("matchType", matchType);
      results.add(result);
    }
    return paged(results, query);
  }

  /** A property search, held to an exact match of the label or to properties with a definition on request. */
  private Object propertySearch(Map<String, String> query) {
    List<String> sources = sources(query.get("ontologies"));
    if (sources != null && !SOURCES.containsAll(sources)) {
      return null;
    }
    String q = query.get("q");
    boolean exact = "true".equals(query.get("require_exact_match"));
    boolean defined = "true".equals(query.get("require_definitions"));
    List<ObjectNode> results = matching(PROPERTIES, q, sources, false).stream()
        .filter(p -> !exact || p.label().equalsIgnoreCase(q))
        .filter(p -> !defined || p.definition() != null)
        .map(this::property).toList();
    return paged(results, query);
  }

  private static List<String> sources(String acronyms) {
    return acronyms == null ? null : Arrays.asList(acronyms.split(","));
  }

  private static List<Term> matching(List<Term> terms, String q, List<String> sources, boolean alsoProperties) {
    Predicate<Term> inSources = t -> sources == null || sources.contains(t.ontology());
    Predicate<Term> matches = t -> q == null || q.isEmpty() || contains(t.label(), q)
        || t.synonyms().stream().anyMatch(s -> contains(s, q))
        || alsoProperties && t.properties().values().stream().flatMap(List::stream).anyMatch(v -> contains(v, q));
    return terms.stream().filter(inSources.and(matches)).toList();
  }

  private static boolean contains(String text, String q) {
    return text.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));
  }

  private static boolean isValueSet(Term term) {
    return COLLECTIONS.contains(term.ontology()) && term.parent() == null;
  }

  private ObjectNode ontology(String acronym) {
    boolean collection = COLLECTIONS.contains(acronym);
    String name = acronym.equals(ONTOLOGY) ? "Test Ontology" : acronym.equals(VIRUSES) ? "Virus Ontology"
        : acronym + (collection ? " value sets" : "");
    return JSON.createObjectNode().put("@id", base() + "ontologies/" + acronym)
        .put("@type", "http://data.bioontology.org/metadata/Ontology").put("acronym", acronym).put("name", name)
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
    long properties = PROPERTIES.stream().filter(p -> p.ontology().equals(acronym)).count();
    return JSON.createObjectNode().put("id", base() + "ontologies/" + acronym + "/submissions/1/metrics")
        .put("created", "2026-10-02T00:00:00Z")
        .put("classes", String.valueOf(classes)).put("individuals", "0").put("properties", String.valueOf(properties))
        .put("maxDepth", "3").put("maxChildCount", "2").put("averageChildCount", "1").put("classesWithOneChild", "1")
        .put("classesWithMoreThan25Children", "0").put("classesWithNoDefinition", "0");
  }

  private ObjectNode bpClass(Term term) {
    ObjectNode node = JSON.createObjectNode().put("@id", term.iri()).put("@type", "http://www.w3.org/2002/07/owl#Class")
        .put("prefLabel", term.label()).put("hasChildren", !childrenOf(CLASSES, term.iri()).isEmpty())
        .put("provisional", false);
    ArrayNode synonyms = node.putArray("synonym");
    term.synonyms().forEach(synonyms::add);
    ArrayNode definition = node.putArray("definition");
    if (term.definition() != null) {
      definition.add(term.definition());
    }
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
    ArrayNode definition = node.putArray("definition");
    if (term.definition() != null) {
      definition.add(term.definition());
    }
    node.set("links", links(term, "properties"));
    return node;
  }

  private ObjectNode links(Term term, String kind) {
    String self = base() + "ontologies/" + term.ontology() + "/" + kind + "/" + URLEncoder.encode(term.iri(), StandardCharsets.UTF_8);
    return JSON.createObjectNode().put("self", self).put("ontology", base() + "ontologies/" + term.ontology())
        .put("children", self + "/children").put("parents", self + "/parents").put("descendants", self + "/descendants")
        .put("tree", self + "/tree").put("ui", "https://bioportal.example.org/" + term.ontology());
  }

  private List<ObjectNode> provisionalClasses() {
    return List.of(
        provisionalClass(PROVISIONAL_CLASS, "Provisional Heart", ROOT, List.of(provisionalRelation())),
        provisionalClass(PROVISIONAL_LUNG, "Provisional Lung", ORGAN, List.of()));
  }

  private ObjectNode provisionalClass(String iri, String label, String subclassOf, List<ObjectNode> relations) {
    String noun = label.substring(label.indexOf(' ') + 1).toLowerCase(Locale.ROOT);
    ObjectNode node = JSON.createObjectNode().put("@id", iri).put("@type", "http://www.w3.org/2002/07/owl#Class")
        .put("label", label).put("creator", "https://data.bioontology.org/users/cedar")
        .put("ontology", base() + "ontologies/" + ONTOLOGY).put("subclassOf", subclassOf).put("provisional", true)
        .put("created", "2026-10-19T00:00:00Z");
    node.putArray("definition").add("A " + noun + " proposed by a CEDAR user.");
    node.putArray("synonym").add("Proposed " + noun);
    ArrayNode relationList = node.putArray("relations");
    relations.forEach(relationList::add);
    return node;
  }

  private ObjectNode provisionalClassFor(String id) {
    for (ObjectNode provisional : provisionalClasses()) {
      String iri = provisional.get("@id").asText();
      if (id.equals(shortId(iri)) || id.equals(iri)) {
        return provisional;
      }
    }
    return null;
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
    int pageCount = (all.size() + size - 1) / size;
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
