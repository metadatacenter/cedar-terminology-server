package org.metadatacenter.cedar.terminology.equivalence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.metadatacenter.cedar.terminology.TerminologyServerApplicationTest;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.terms.customObjects.PagedResults;
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

  @ClassRule
  public static final DropwizardAppRule<TerminologyServerConfiguration> RULE =
      new DropwizardAppRule<>(TerminologyServerApplicationTest.class,
          ResourceHelpers.resourceFilePath("test-config.yml"));

  @BeforeClass
  public static void setUp() {
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_TERMINOLOGY);
    CedarConfig cedarConfig = CedarConfig.getInstance(environment);
    TestAuthUtil.installInMemoryUserService(cedarConfig);
    authHeader = TestAuthUtil.getTestUser1AuthHeader(cedarConfig);
    clientBuilder = ResteasyClientBuilder.newBuilder();
    baseBp = "http://localhost:" + RULE.getLocalPort() + "/bioportal";
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
    Assert.assertEquals(200, r.getStatus());
    PagedResults<OntologyClass> pr = r.readEntity(new GenericType<PagedResults<OntologyClass>>() {});
    r.close();
    Assert.assertEquals(loadGoldenIds(golden), shortIds(pr.getCollection()));
  }

  /** GET the (unpaged) parents of a class and assert its id set equals the golden. */
  private void assertParentSet(String ontology, String iri, String golden) throws Exception {
    Response r = get(baseBp + "/ontologies/" + ontology + "/classes/" + enc(iri) + "/parents");
    Assert.assertEquals(200, r.getStatus());
    List<OntologyClass> parents = r.readEntity(new GenericType<List<OntologyClass>>() {});
    r.close();
    Assert.assertEquals(loadGoldenIds(golden), shortIds(parents));
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

  /**
   * Roots are NOT identical to BioPortal's, and this is a characterized divergence in how a root is
   * computed under imports. CL imports large upper ontologies (BFO, GO, PATO, …); many imported
   * classes arrive as bare references with no {@code subClassOf} in the served file, so locally they
   * are parentless and surface as roots (local 537 vs BioPortal 66). BioPortal lists as roots only
   * the direct subclasses of {@code owl:Thing}. What must hold: every BioPortal root is also a local
   * root (BioPortal's roots are a subset). Whether to align the root rule to BioPortal's (roots =
   * asserted {@code owl:Thing} children) is a separate decision, like the genus fix.
   */
  @Test
  public void cl_rootsIncludeAllBioPortalRoots() throws Exception {
    Response r = get(baseBp + "/ontologies/CL/classes/roots");
    Assert.assertEquals(200, r.getStatus());
    List<OntologyClass> roots = r.readEntity(new GenericType<List<OntologyClass>>() {});
    r.close();
    Assert.assertTrue("every BioPortal CL root must also be a local root",
        shortIds(roots).containsAll(loadGoldenIds("cl_roots_ids")));
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
    Assert.assertEquals(200, r.getStatus());
    PagedResults<SearchResult> results = r.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    r.close();

    List<String> expected = MAPPER.convertValue(enumerated, new com.fasterxml.jackson.core.type.TypeReference<List<JsonNode>>() {})
        .stream()
        .sorted(Comparator.comparing(n -> n.get("prefLabel").asText(), String.CASE_INSENSITIVE_ORDER))
        .map(n -> n.get("uri").asText())
        .collect(Collectors.toList());
    List<String> actual = results.getCollection().stream().map(SearchResult::getLdId).collect(Collectors.toList());
    Assert.assertEquals(expected, actual);
    Assert.assertEquals(Integer.valueOf(expected.size()), results.getTotalCount());
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
    Assert.assertEquals(200, r.getStatus());
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
    Assert.assertTrue("local substring search should overlap BioPortal Solr by Jaccard >= 0.9, was " + jaccard,
        jaccard >= 0.9);
  }
}
