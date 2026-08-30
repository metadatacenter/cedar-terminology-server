package org.metadatacenter.cedar.terminology.equivalence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.TerminologyServerApplicationTest;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.util.test.TestAuthUtil;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * REST-level equivalence: the local snapshot backend must reproduce BioPortal's behavior for the
 * calls the CEDAR UI makes, on version-pinned snapshots committed as fixtures. The two ontologies
 * cover the tutorial's constraint modes:
 * <ul>
 *   <li>OBI (submission 65, 2026-05-08) — the assembled-classes / assay branch;</li>
 *   <li>UBERON (submission 355, 2023-07-25) — the {@code organ} branch.</li>
 * </ul>
 * The app runs in strict local-only mode ({@code terminologyStore.localOnly=true}) with both served
 * locally, so a pass proves the local backend answered rather than falling back to BioPortal.
 *
 * <p>Goldens are captured from BioPortal for the same releases and committed as CEDAR-projected id
 * sets, so the comparison is offline and deterministic. These are the deterministic, exact-set cases
 * (class-hierarchy browsing + the CEE's enumerated classes); typed {@code /search} against Solr is a
 * separate, report-only comparison.
 */
public class EquivalenceTest {

  private static final String OBI_ASSAY = "http://purl.obolibrary.org/obo/OBI_0000070";
  private static final String UBERON_ORGAN = "http://purl.obolibrary.org/obo/UBERON_0000062";
  private static final String CL_HEPATOCYTE = "http://purl.obolibrary.org/obo/CL_0000182";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static ClientBuilder clientBuilder;
  private static String authHeader;
  private static String baseBp;

  /**
   * Build a runtime catalog pointing at the committed snapshot fixtures and enable strict local-only
   * routing — before the app rule boots, since the app reads these system properties at startup.
   */
  static {
    try {
      Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
      environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "0");
      environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "0");
      environment.put("CEDAR_TERMINOLOGY_STOP_PORT", "0");
      CedarEnvironmentSource.setOverride(environment);

      Path tmp = Files.createTempDirectory("equivalence");
      Path catalogPath = tmp.resolve("catalog.sqlite");
      try (CatalogStore catalog = CatalogStore.openFile(catalogPath.toString())) {
        catalog.initSchema();
        register(catalog, "OBI", "Ontology for Biomedical Investigations",
            "equivalence/snapshots/OBI.sqlite", "obi-2026-05-08", "2026-05-08", 5218, 6386);
        register(catalog, "UBERON", "Uberon multi-species anatomy ontology",
            "equivalence/snapshots/UBERON.sqlite", "uberon-2023-07-25", "2023-07-25", 26624, 48669);
        register(catalog, "CL", "Cell Ontology",
            "equivalence/snapshots/CL.sqlite", "cl-6abe12f1", "2024-01-01", 19167, 36335);
      }
      System.setProperty("terminologyStore.catalogPath", catalogPath.toString());
      System.setProperty("terminologyStore.localOntologies", "OBI,UBERON,CL");
      System.setProperty("terminologyStore.localOnly", "true");
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static void register(CatalogStore catalog, String acronym, String name, String fixtureResource,
                               String versionId, String released, int classCount, int edgeCount) throws Exception {
    Path fixture = Paths.get(ResourceHelpers.resourceFilePath(fixtureResource));
    catalog.upsertOntology(new CatalogStore.OntologyInfo(acronym, name, null, "OWL"));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo(versionId, acronym, released, released,
        released + "T00:00:00Z", "OWL", "subsumption", classCount, edgeCount, fixture.toString(), versionId, "public"));
    catalog.setTag(acronym, CatalogStore.TAG_LATEST, versionId);
  }

  public static final DropwizardTestSupport<TerminologyServerConfiguration> RULE =
      new DropwizardTestSupport<>(TerminologyServerApplicationTest.class,
          ResourceHelpers.resourceFilePath("test-config.yml"));

  @BeforeAll
  public static void setUp() throws Exception {
    RULE.before();
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_TERMINOLOGY);
    CedarConfig cedarConfig = CedarConfig.getInstance(environment);
    TestAuthUtil.installInMemoryUserService(cedarConfig);
    authHeader = TestAuthUtil.getTestUser1AuthHeader(cedarConfig);
    clientBuilder = ClientBuilder.newBuilder();
    baseBp = "http://localhost:" + RULE.getLocalPort() + "/bioportal";
  }

  @AfterAll
  public static void tearDown() {
    RULE.after();
  }

  /* ---- helpers ---- */

  private static String enc(String iri) {
    return URLEncoder.encode(iri, StandardCharsets.UTF_8);
  }

  private Response get(String url) {
    return clientBuilder.build().target(URI.create(url)).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
  }

  private static Set<String> loadGoldenIds(String name) throws Exception {
    String json = Files.readString(Paths.get(ResourceHelpers.resourceFilePath("equivalence/golden/" + name + ".json")));
    return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Set<String>>() {});
  }

  private static Set<String> shortIds(List<OntologyClass> classes) {
    return classes.stream().map(OntologyClass::getId).collect(Collectors.toSet());
  }

  /** GET a paged class relation (children/descendants) and assert its id set equals the golden. */
  private void assertPagedClassSet(String ontology, String iri, String relation, int pageSize, String golden)
      throws Exception {
    Response r = get(baseBp + "/ontologies/" + ontology + "/classes/" + enc(iri) + "/" + relation
        + "?page=1&pageSize=" + pageSize);
    Assertions.assertEquals(200, r.getStatus());
    PagedResults<OntologyClass> pr = r.readEntity(new GenericType<PagedResults<OntologyClass>>() {});
    r.close();
    Assertions.assertEquals(loadGoldenIds(golden), shortIds(pr.getCollection()));
  }

  /** GET the (unpaged) parents of a class and assert its id set equals the golden. */
  private void assertParentSet(String ontology, String iri, String golden) throws Exception {
    Response r = get(baseBp + "/ontologies/" + ontology + "/classes/" + enc(iri) + "/parents");
    Assertions.assertEquals(200, r.getStatus());
    List<OntologyClass> parents = r.readEntity(new GenericType<List<OntologyClass>>() {});
    r.close();
    Assertions.assertEquals(loadGoldenIds(golden), shortIds(parents));
  }

  /* ---- OBI: the assay branch ---- */

  @Test
  public void obi_descendantsOfAssay_matchBioPortalSet() throws Exception {
    assertPagedClassSet("OBI", OBI_ASSAY, "descendants", 2500, "assay_descendants_ids");
  }

  @Test
  public void obi_childrenOfAssay_matchBioPortalSet() throws Exception {
    // Exact only because the extractor reads the named genus of equivalentClass definitions.
    assertPagedClassSet("OBI", OBI_ASSAY, "children", 500, "assay_children_ids");
  }

  @Test
  public void obi_parentsOfAssay_matchBioPortalSet() throws Exception {
    assertParentSet("OBI", OBI_ASSAY, "assay_parents_ids");
  }

  /* ---- UBERON: the organ branch (the tutorial's branch constraint) ---- */

  @Test
  public void uberon_descendantsOfOrgan_matchBioPortalSet() throws Exception {
    assertPagedClassSet("UBERON", UBERON_ORGAN, "descendants", 4000, "organ_descendants_ids");
  }

  @Test
  public void uberon_childrenOfOrgan_matchBioPortalSet() throws Exception {
    assertPagedClassSet("UBERON", UBERON_ORGAN, "children", 500, "organ_children_ids");
  }

  @Test
  public void uberon_parentsOfOrgan_matchBioPortalSet() throws Exception {
    assertParentSet("UBERON", UBERON_ORGAN, "organ_parents_ids");
  }

  /* ---- CL: the whole-ontology case, probed via hepatocyte (the tutorial's cell type) ---- */

  @Test
  public void cl_hepatocyteChildren_matchBioPortalSet() throws Exception {
    assertPagedClassSet("CL", CL_HEPATOCYTE, "children", 500, "hepatocyte_children_ids");
  }

  @Test
  public void cl_hepatocyteDescendants_matchBioPortalSet() throws Exception {
    assertPagedClassSet("CL", CL_HEPATOCYTE, "descendants", 500, "hepatocyte_descendants_ids");
  }

  @Test
  public void cl_hepatocyteParents_matchBioPortalSet() throws Exception {
    assertParentSet("CL", CL_HEPATOCYTE, "hepatocyte_parents_ids");
  }

  // Roots are compared on the full IRI (@id), not the short id: one root, oboInOwl#ObsoleteClass, is a
  // hash IRI whose short id is ambiguous (split on '/' vs '#'). The branch goldens stay short ids —
  // they are all obo/ IRIs with no such ambiguity.
  private Set<String> rootIds(String ontology) throws Exception {
    Response r = get(baseBp + "/ontologies/" + ontology + "/classes/roots");
    Assertions.assertEquals(200, r.getStatus());
    List<OntologyClass> roots = r.readEntity(new GenericType<List<OntologyClass>>() {});
    r.close();
    return roots.stream().map(OntologyClass::getLdId).collect(Collectors.toSet());
  }

  /**
   * OBI roots match BioPortal exactly. The extractor computes roots BioPortal's way: a root is a
   * non-obsolete class that asserts {@code subClassOf owl:Thing}. Excluding obsolete classes (OBI
   * asserts owl:Thing for 19 classes, 9 of them deprecated) yields exactly BioPortal's 10.
   */
  @Test
  public void obi_rootsMatchBioPortalSet() throws Exception {
    Assertions.assertEquals(loadGoldenIds("obi_roots_ids"), rootIds("OBI"));
  }

  /**
   * UBERON and CL roots are a superset of BioPortal's — a characterized divergence with a known,
   * bigger cause. The owl:Thing + obsolete rule (see {@link #obi_rootsMatchBioPortalSet}) brought CL
   * from 537 roots to 250 and UBERON to 53, but BioPortal has 66 and 9. The remainder is flattened
   * import stubs: these ontologies import NCBITaxon / GO / PATO / CHEBI etc., whose classes appear in
   * the served file as bare {@code subClassOf owl:Thing} declarations with no real parent, so they
   * look like roots locally. BioPortal resolves the imports fully and roots only each imported
   * hierarchy's true top (e.g. NCBITaxon_1). Matching that needs import resolution, not a structural
   * rule. The invariant that holds either way: every BioPortal root is also a local root.
   */
  @Test
  public void uberon_rootsIncludeAllBioPortalRoots() throws Exception {
    Assertions.assertTrue(
        rootIds("UBERON").containsAll(loadGoldenIds("uberon_roots_ids")),"every BioPortal UBERON root must also be a local root");
  }

  @Test
  public void cl_rootsIncludeAllBioPortalRoots() throws Exception {
    Assertions.assertTrue(
        rootIds("CL").containsAll(loadGoldenIds("cl_roots_ids")),"every BioPortal CL root must also be a local root");
  }

  /* ---- the ontology list: catalog-backed, no BioPortal crawl ---- */

  @Test
  public void ontologyList_reportsOnlyTheVersionedOntologies() throws Exception {
    // GET /bioportal/ontologies is answered from the local catalog, so it lists exactly the
    // ontologies this server versions (OBI, UBERON, CL) rather than crawling BioPortal's ~1300.
    Response r = get(baseBp + "/ontologies");
    Assertions.assertEquals(200, r.getStatus());
    List<Ontology> ontologies = r.readEntity(new GenericType<List<Ontology>>() {});
    r.close();
    Assertions.assertEquals(Set.of("OBI", "UBERON", "CL"),
        ontologies.stream().map(Ontology::getId).collect(Collectors.toSet()));
    // Served hierarchically (not flat), so tree/roots endpoints render as a hierarchy.
    Assertions.assertTrue( ontologies.stream().noneMatch(Ontology::getIsFlat),"versioned ontologies are hierarchical");
  }

  /* ---- the CEE's enumerated-classes path (deterministic) ---- */

  @Test
  public void cee_enumeratedClasses_integratedSearchReturnsThemSortedByLabel() throws Exception {
    ArrayNode enumerated = (ArrayNode) MAPPER.readTree(
        Files.readString(Paths.get(ResourceHelpers.resourceFilePath("equivalence/golden/enumerated_classes_input.json"))));

    ObjectNode valueConstraints = MAPPER.createObjectNode();
    valueConstraints.set("ontologies", MAPPER.createArrayNode());
    valueConstraints.set("branches", MAPPER.createArrayNode());
    valueConstraints.set("valueSets", MAPPER.createArrayNode());
    valueConstraints.set("classes", enumerated);
    ObjectNode parameterObject = MAPPER.createObjectNode();
    parameterObject.set("valueConstraints", valueConstraints);
    parameterObject.put("inputText", "");
    ObjectNode body = MAPPER.createObjectNode();
    body.set("parameterObject", parameterObject);
    body.put("page", 1);
    body.put("pageSize", 50);

    Response r = clientBuilder.build().target(URI.create(baseBp + "/integrated-search")).request()
        .post(Entity.json(body));
    Assertions.assertEquals(200, r.getStatus());
    PagedResults<SearchResult> results = r.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    r.close();

    List<String> expected = MAPPER.convertValue(enumerated, new com.fasterxml.jackson.core.type.TypeReference<List<JsonNode>>() {})
        .stream()
        .sorted(Comparator.comparing(n -> n.get("prefLabel").asText(), String.CASE_INSENSITIVE_ORDER))
        .map(n -> n.get("uri").asText())
        .collect(Collectors.toList());
    List<String> actual = results.getCollection().stream().map(SearchResult::getLdId).collect(Collectors.toList());
    Assertions.assertEquals(expected, actual);
    Assertions.assertEquals(Integer.valueOf(expected.size()), results.getTotalCount());
  }

  /* ---- typed search: quantify the Solr divergence rather than assert exact equality ---- */

  /**
   * Local class search matches labels by case-insensitive substring; BioPortal search is Solr
   * (tokenization, stemming, synonyms). The result SETS cannot be identical, but for a plain term
   * they are very close — this guards that closeness and logs the exact divergence each run. For
   * {@code assay} in OBI the overlap is ~0.985 (local over-matches a few substrings; Solr adds a few
   * synonym hits).
   */
  @Test
  public void obi_typedSearchIsCloseToBioPortalSolr() throws Exception {
    ObjectNode ontology = MAPPER.createObjectNode();
    ontology.put("acronym", "OBI");
    ObjectNode valueConstraints = MAPPER.createObjectNode();
    valueConstraints.set("ontologies", MAPPER.createArrayNode().add(ontology));
    valueConstraints.set("branches", MAPPER.createArrayNode());
    valueConstraints.set("valueSets", MAPPER.createArrayNode());
    valueConstraints.set("classes", MAPPER.createArrayNode());
    ObjectNode parameterObject = MAPPER.createObjectNode();
    parameterObject.set("valueConstraints", valueConstraints);
    parameterObject.put("inputText", "assay");
    ObjectNode body = MAPPER.createObjectNode();
    body.set("parameterObject", parameterObject);
    body.put("page", 1);
    body.put("pageSize", 3000);

    Response r = clientBuilder.build().target(URI.create(baseBp + "/integrated-search")).request()
        .post(Entity.json(body));
    Assertions.assertEquals(200, r.getStatus());
    PagedResults<SearchResult> results = r.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    r.close();

    Set<String> local = results.getCollection().stream()
        .map(sr -> sr.getLdId().substring(sr.getLdId().lastIndexOf('/') + 1)).collect(Collectors.toSet());
    Set<String> bp = loadGoldenIds("obi_search_assay_bp_ids");
    Set<String> intersection = new java.util.HashSet<>(local);
    intersection.retainAll(bp);
    Set<String> union = new java.util.HashSet<>(local);
    union.addAll(bp);
    double jaccard = (double) intersection.size() / union.size();
    System.out.printf("[equivalence] OBI /search 'assay': local=%d BioPortal=%d shared=%d Jaccard=%.3f "
            + "local-only=%d BioPortal-only=%d%n",
        local.size(), bp.size(), intersection.size(), jaccard,
        local.size() - intersection.size(), bp.size() - intersection.size());
    Assertions.assertTrue(
        jaccard >= 0.9,"local substring search should overlap BioPortal Solr by Jaccard >= 0.9, was " + jaccard);
  }

  /* ---- the tutorial pathway, end to end (the "Tissue Sample" template's three fields) ----
   * https://metadatacenter.readthedocs.io/en/latest/tutorials/cedar_term_tutorial/
   * Cell Type = whole CL (fill: hepatocyte); Organ = UBERON organ branch (fill: liver);
   * Assay Type = three assembled OBI classes. Each field's runtime autocomplete is the CEE's
   * integrated-search, replayed here against the local backend and checked against BioPortal. */

  private static final String UBERON_LIVER = "UBERON_0002107";
  private static final String CL_HEPATOCYTE_ID = "CL_0000182";

  private ObjectNode valueConstraints(ArrayNode ontologies, ArrayNode branches, ArrayNode classes) {
    ObjectNode vc = MAPPER.createObjectNode();
    vc.set("ontologies", ontologies == null ? MAPPER.createArrayNode() : ontologies);
    vc.set("branches", branches == null ? MAPPER.createArrayNode() : branches);
    vc.set("valueSets", MAPPER.createArrayNode());
    vc.set("classes", classes == null ? MAPPER.createArrayNode() : classes);
    return vc;
  }

  private PagedResults<SearchResult> integratedSearch(ObjectNode valueConstraints, String inputText, int pageSize) {
    ObjectNode parameterObject = MAPPER.createObjectNode();
    parameterObject.set("valueConstraints", valueConstraints);
    parameterObject.put("inputText", inputText);
    ObjectNode body = MAPPER.createObjectNode();
    body.set("parameterObject", parameterObject);
    body.put("page", 1);
    body.put("pageSize", pageSize);
    Response r = clientBuilder.build().target(URI.create(baseBp + "/integrated-search")).request()
        .post(Entity.json(body));
    Assertions.assertEquals(200, r.getStatus());
    PagedResults<SearchResult> res = r.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    r.close();
    return res;
  }

  private static Set<String> resultShortIds(List<SearchResult> results) {
    return results.stream().map(sr -> sr.getLdId().substring(sr.getLdId().lastIndexOf('/') + 1))
        .collect(Collectors.toSet());
  }

  private static double jaccard(Set<String> a, Set<String> b) {
    Set<String> intersection = new java.util.HashSet<>(a);
    intersection.retainAll(b);
    Set<String> union = new java.util.HashSet<>(a);
    union.addAll(b);
    return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
  }

  @Test
  public void tutorial_cellType_wholeCL_autocompleteMatchesBioPortal() throws Exception {
    // Cell Type constrained to the whole Cell Ontology; user types "hepatocyte".
    ObjectNode cl = MAPPER.createObjectNode();
    cl.put("acronym", "CL");
    Set<String> local = resultShortIds(
        integratedSearch(valueConstraints(MAPPER.createArrayNode().add(cl), null, null), "hepatocyte", 500)
            .getCollection());
    Set<String> bp = loadGoldenIds("hepatocyte_cl_search_bp_ids");
    Assertions.assertTrue( local.contains(CL_HEPATOCYTE_ID),"the tutorial's pick (hepatocyte) must be selectable");
    Assertions.assertTrue( bp.containsAll(local),"no local hits beyond BioPortal's");
    double j = jaccard(local, bp);
    System.out.printf("[tutorial] Cell Type 'hepatocyte'/CL: local=%d BioPortal=%d Jaccard=%.3f%n",
        local.size(), bp.size(), j);
    Assertions.assertTrue( j >= 0.9,"hepatocyte autocomplete should match BioPortal closely, Jaccard was " + j);
  }

  @Test
  public void tutorial_organ_uberonBranch_autocompleteFindsLiverWithinTheBranch() throws Exception {
    // Organ constrained to the UBERON organ branch; user types "liver".
    ObjectNode branch = MAPPER.createObjectNode();
    branch.put("acronym", "UBERON");
    branch.put("uri", UBERON_ORGAN);
    Set<String> local = resultShortIds(
        integratedSearch(valueConstraints(null, MAPPER.createArrayNode().add(branch), null), "liver", 500)
            .getCollection());
    Set<String> organBranch = loadGoldenIds("organ_descendants_ids");
    Assertions.assertTrue( local.contains(UBERON_LIVER),"the tutorial's pick (liver) must be selectable");
    Assertions.assertTrue(
        organBranch.containsAll(local),"every autocomplete result stays within the organ branch (matches BioPortal exactly)");
    System.out.printf("[tutorial] Organ 'liver'/UBERON organ-branch: local=%d, all within the branch%n", local.size());
  }

  @Test
  public void tutorial_assayType_threeAssembledObiClassesReturnedExactly() throws Exception {
    // Assay Type constrained to three assembled OBI classes; the CEE filters them server-side.
    ArrayNode assays = (ArrayNode) MAPPER.readTree(Files.readString(
        Paths.get(ResourceHelpers.resourceFilePath("equivalence/golden/tutorial_assays_input.json"))));
    List<String> actual = integratedSearch(valueConstraints(null, null, assays), "", 50)
        .getCollection().stream().map(SearchResult::getLdId).collect(Collectors.toList());
    // BioPortal returns the enumerated classes sorted by preferred label (histopathology, imaging, microscopy).
    Assertions.assertEquals(List.of(
        "http://purl.obolibrary.org/obo/OBI_0002564",
        "http://purl.obolibrary.org/obo/OBI_0000185",
        "http://purl.obolibrary.org/obo/OBI_0002119"), actual);
  }
}
