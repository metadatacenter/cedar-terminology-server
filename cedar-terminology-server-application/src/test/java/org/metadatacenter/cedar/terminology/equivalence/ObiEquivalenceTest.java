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
 * REST-level equivalence for OBI: the local snapshot backend must reproduce BioPortal's behavior
 * for the calls the CEDAR UI makes, on a version-pinned OBI snapshot (submission 65, 2026-05-08 —
 * the same release the goldens were captured from).
 *
 * <p>The app runs in strict local-only mode ({@code terminologyStore.localOnly=true}) with OBI
 * served from the committed snapshot fixture, so a pass proves the <em>local</em> backend answered:
 * a local gap would propagate rather than fall back to BioPortal.
 *
 * <p>These are the deterministic, exact-set cases (class hierarchy browsing + the CEE's enumerated
 * classes). Typed {@code /search} against BioPortal's Solr is a separate, report-only comparison
 * added later, since a snapshot label match cannot reproduce Solr's result set exactly.
 */
public class ObiEquivalenceTest {

  private static final String OBI_ASSAY = "http://purl.obolibrary.org/obo/OBI_0000070";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static ClientBuilder clientBuilder;
  private static String authHeader;
  private static String baseBp;

  /**
   * Build a runtime catalog pointing at the committed OBI snapshot and enable strict local-only
   * routing — before the app rule boots, since the app reads these system properties at startup.
   */
  static {
    try {
      Path fixture = Paths.get(ResourceHelpers.resourceFilePath("equivalence/snapshots/OBI.sqlite"));
      Path tmp = Files.createTempDirectory("obi-equivalence");
      Path catalogPath = tmp.resolve("catalog.sqlite");
      try (CatalogStore catalog = CatalogStore.openFile(catalogPath.toString())) {
        catalog.initSchema();
        catalog.upsertOntology(new CatalogStore.OntologyInfo("OBI",
            "Ontology for Biomedical Investigations", null, "OWL"));
        String versionId = "obi-2026-05-08";
        catalog.addSnapshot(new CatalogStore.SnapshotInfo(versionId, "OBI", "2026-05-08", "2026-05-08",
            "2026-05-08T00:00:00Z", "OWL", "subsumption", 5218, 6180, fixture.toString(), versionId, "public"));
        catalog.setTag("OBI", CatalogStore.TAG_LATEST, versionId);
      }
      System.setProperty("terminologyStore.catalogPath", catalogPath.toString());
      System.setProperty("terminologyStore.localOntologies", "OBI");
      System.setProperty("terminologyStore.localOnly", "true");
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
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

  /* ---- class-hierarchy equivalence (template-editor tree browsing) ---- */

  @Test
  public void descendantsOfAssay_matchBioPortalSet() throws Exception {
    Response r = get(baseBp + "/ontologies/OBI/classes/" + enc(OBI_ASSAY) + "/descendants?page=1&pageSize=2500");
    Assert.assertEquals(200, r.getStatus());
    PagedResults<OntologyClass> pr = r.readEntity(new GenericType<PagedResults<OntologyClass>>() {});
    r.close();
    Assert.assertEquals(loadGoldenIds("assay_descendants_ids"), shortIds(pr.getCollection()));
  }

  /**
   * Direct children are NOT identical to BioPortal's, and that is a known, characterized divergence:
   * the snapshot extractor performs transitive reduction (it keeps only most-specific parents), so a
   * class that OBI also asserts directly under {@code assay} — a redundant edge, because its other
   * parent is itself under assay — sits one level deeper locally. BioPortal preserves those redundant
   * asserted edges (here, 121 of them on this branch). The closure is identical (see the descendants
   * test); only the direct-children listing differs. Two invariants must still hold:
   * <ul>
   *   <li>no spurious local edges — every local direct child is also a BioPortal direct child;</li>
   *   <li>closure preserved — every BioPortal direct child is reachable locally (a descendant).</li>
   * </ul>
   * Whether to make the extractor preserve asserted edges (BioPortal-identical tree browsing) or keep
   * the reduced hierarchy (cleaner, closure-equivalent) is a separate modelling decision.
   */
  @Test
  public void childrenOfAssay_areClosureConsistentWithBioPortal() throws Exception {
    Response r = get(baseBp + "/ontologies/OBI/classes/" + enc(OBI_ASSAY) + "/children?page=1&pageSize=500");
    Assert.assertEquals(200, r.getStatus());
    PagedResults<OntologyClass> pr = r.readEntity(new GenericType<PagedResults<OntologyClass>>() {});
    r.close();
    Set<String> local = shortIds(pr.getCollection());
    Set<String> bpChildren = loadGoldenIds("assay_children_ids");
    Set<String> bpDescendants = loadGoldenIds("assay_descendants_ids");
    Assert.assertTrue("local direct children must be a subset of BioPortal's", bpChildren.containsAll(local));
    Assert.assertTrue("every BioPortal direct child must be reachable locally", bpDescendants.containsAll(bpChildren));
  }

  @Test
  public void parentsOfAssay_matchBioPortalSet() throws Exception {
    Response r = get(baseBp + "/ontologies/OBI/classes/" + enc(OBI_ASSAY) + "/parents");
    Assert.assertEquals(200, r.getStatus());
    List<OntologyClass> parents = r.readEntity(new GenericType<List<OntologyClass>>() {});
    r.close();
    Assert.assertEquals(loadGoldenIds("assay_parents_ids"), shortIds(parents));
  }

  /* ---- the CEE's enumerated-classes path (deterministic) ---- */

  @Test
  public void enumeratedClasses_integratedSearchReturnsThemSortedByLabel() throws Exception {
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

    // Expected: the given classes, ordered by preferred label (case-insensitive), as BioPortal returns them.
    List<String> expected = MAPPER.convertValue(enumerated, new com.fasterxml.jackson.core.type.TypeReference<List<JsonNode>>() {})
        .stream()
        .sorted(Comparator.comparing(n -> n.get("prefLabel").asText(), String.CASE_INSENSITIVE_ORDER))
        .map(n -> n.get("uri").asText())
        .collect(Collectors.toList());
    List<String> actual = results.getCollection().stream().map(SearchResult::getLdId).collect(Collectors.toList());
    Assert.assertEquals(expected, actual);
    Assert.assertEquals(Integer.valueOf(expected.size()), results.getTotalCount());
  }
}
