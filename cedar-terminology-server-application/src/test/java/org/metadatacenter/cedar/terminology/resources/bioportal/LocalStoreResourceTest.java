package org.metadatacenter.cedar.terminology.resources.bioportal;

import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.TerminologyServerApplicationTest;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.OntologyVersion;
import org.metadatacenter.terms.domainObjects.VersionTriple;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.domainObjects.VersionDiff;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;
import org.metadatacenter.util.test.TestAuthUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.metadatacenter.cedar.terminology.utils.Constants.BP_CHILDREN;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_CLASSES;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_ENDPOINT;
import static org.metadatacenter.cedar.terminology.utils.Constants.BP_ONTOLOGIES;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration test that the local SQLite backend is served through the real HTTP stack.
 *
 * A synthetic ontology "LOCALTEST" is written to a temporary catalog + snapshot before the app
 * starts, and the local store is enabled for it via system properties (set in the static
 * initializer, which runs before the {@code DropwizardTestSupport} starts the app). The class-children
 * endpoint for a LOCALTEST class must then be answered from the snapshot, not BioPortal.
 *
 * Auth is provided by {@link TestAuthUtil}'s in-memory user service (installed after startup), so
 * this test needs neither Neo4j nor BioPortal — only the CEDAR environment variables that
 * {@link CedarConfig} requires.
 */
public class LocalStoreResourceTest {

  static {
    // Must run before the test support boots the server, which reads the port env vars.
    // Alternate server ports, so the test instance never collides with a running dev server.
    java.util.Map<String, String> environment =
        new java.util.HashMap<>(org.metadatacenter.config.environment.CedarEnvironmentSource.getAll());
    environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "19004");
    environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "19104");
    environment.put("CEDAR_TERMINOLOGY_STOP_PORT", "19204");
    org.metadatacenter.config.environment.CedarEnvironmentSource.setOverride(environment);
  }

  private static final String ONT = "LOCALTEST";
  // Named CEDARVS because integrated-search restricts a value-set constraint's collection to the three
  // known collections (CEDARVS / NLMVS / CADSR-VS) via BP_VS_COLLECTIONS_READ_REGEX; a value-set read
  // (pinned or current) is only reachable for one of those.
  private static final String VS = "CEDARVS";
  private static final String BASE = "http://localtest/";

  static {
    try {
      Path dir = Files.createTempDirectory("localstore-it");

      Path snapshot = dir.resolve("snap.sqlite");
      try (SnapshotStore s = SnapshotStore.openFile(snapshot.toString())) {
        s.initSchema();
        s.addConcept(BASE + "disease", "Disease");
        s.addConcept(BASE + "cancer", "Cancer");
        s.addConcept(BASE + "melanoma", "Melanoma");
        s.addEdge(BASE + "cancer", BASE + "disease", "rdfs:subClassOf");
        s.addEdge(BASE + "melanoma", BASE + "cancer", "rdfs:subClassOf");
        s.materialize();
      }

      // A second, newer version that adds one concept ("infection", a sibling of "cancer" under
      // "disease"). It leaves cancer's children unchanged, so the children test above still holds,
      // while giving the /versions and /versions/diff endpoints real history to report.
      Path snapshot2 = dir.resolve("snap2.sqlite");
      try (SnapshotStore s = SnapshotStore.openFile(snapshot2.toString())) {
        s.initSchema();
        s.addConcept(BASE + "disease", "Disease");
        s.addConcept(BASE + "cancer", "Cancer");
        s.addConcept(BASE + "melanoma", "Melanoma");
        s.addConcept(BASE + "infection", "Infection");
        s.addEdge(BASE + "cancer", BASE + "disease", "rdfs:subClassOf");
        s.addEdge(BASE + "melanoma", BASE + "cancer", "rdfs:subClassOf");
        s.addEdge(BASE + "infection", BASE + "disease", "rdfs:subClassOf");
        s.materialize();
      }

      Path catalog = dir.resolve("catalog.sqlite");
      try (CatalogStore c = CatalogStore.openFile(catalog.toString())) {
        c.initSchema();
        c.upsertOntology(new CatalogStore.OntologyInfo(ONT, "Local Test", null, "OWL"));
        c.addSnapshot(new CatalogStore.SnapshotInfo("v1", ONT, "1.0", "2025-01-01", "2025-01-01T00:00:00Z",
            "OWL", "subsumption", 3, 2, snapshot.toString(), "v1", "open"));
        c.addSnapshot(new CatalogStore.SnapshotInfo("v2", ONT, "2.0", "2025-06-01", "2025-06-01T00:00:00Z",
            "OWL", "subsumption", 4, 3, snapshot2.toString(), "v2", "open"));
        c.setTag(ONT, CatalogStore.TAG_LATEST, "v2");
        // Claim the term ID-space so a class/term IRI can be mapped back to its owning ontology (the
        // reverse of the A6 iri derivation). idspace("http://localtest/cancer") is "http://localtest/",
        // so that is LOCALTEST's raw namespace; the iri value itself is only provenance here.
        c.setOntologyIri(ONT, BASE, BASE);

        // A value-set collection, versioned by the same content-hash mechanism. Deliberately NOT in
        // the localOntologies allowlist below: value-set-collection version resolution gates on the
        // catalog's kind marker, not the search/browse allowlist. It reuses the ONT snapshot file (the
        // version-current endpoint reads only catalog columns for the triple).
        c.upsertOntology(new CatalogStore.OntologyInfo(VS, "Local Value Sets", null, "SKOS"));
        c.addSnapshot(new CatalogStore.SnapshotInfo("vs1", VS, "2024-05-01", "2024-05-01",
            "2024-05-02T00:00:00Z", "SKOS", "subsumption", 3, 2, snapshot.toString(), "vs1", "open"));
        // A second, newer version of the collection (reusing the v2 snapshot, which adds "infection"
        // under "disease"), left un-tagged so `latest` stays vs1 — it gives a value-set constraint a
        // pinned version distinct from current, so a frozen value-set read can be exercised.
        c.addSnapshot(new CatalogStore.SnapshotInfo("vs2", VS, "2024-11-01", "2024-11-01",
            "2024-11-02T00:00:00Z", "SKOS", "subsumption", 4, 3, snapshot2.toString(), "vs2", "open"));
        c.setTag(VS, CatalogStore.TAG_LATEST, "vs1");
        c.setOntologyKind(VS, CatalogStore.KIND_VALUE_SET_COLLECTION);
      }

      // Override the (empty) cedar-main.yml localStore config for this test. Uses the non-"cedar."
      // property names the app recognizes, set before the app starts so the local store is enabled.
      System.setProperty("terminologyStore.catalogPath", catalog.toString());
      // Serve LOCALTEST and the LOCALVS collection: a value-set collection is in the serving allowlist
      // (as CEDARVS is in production) so its members can be enumerated at populate time.
      System.setProperty("terminologyStore.localOntologies", ONT + "," + VS);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static final DropwizardTestSupport<TerminologyServerConfiguration> RULE =
      new DropwizardTestSupport<>(TerminologyServerApplicationTest.class,
          ResourceHelpers.resourceFilePath("test-config.yml"));

  private static ClientBuilder clientBuilder;
  private static String authHeader;
  private static String childrenUrlBase;

  @BeforeAll
  public static void setUp() throws Exception {
    RULE.before();
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_TERMINOLOGY);
    CedarConfig cedarConfig = CedarConfig.getInstance(environment);

    // Replace the app's Neo4j-backed user service with an in-memory one (no auth backend needed).
    TestAuthUtil.installInMemoryUserService(cedarConfig);
    authHeader = TestAuthUtil.getTestUser1AuthHeader(cedarConfig);

    clientBuilder = ClientBuilder.newBuilder();
    childrenUrlBase = "http://localhost:" + RULE.getLocalPort() + "/" + BP_ENDPOINT + "/" + BP_ONTOLOGIES;
  }

  @AfterAll
  public static void tearDown() {
    RULE.after();
  }

  @Test
  public void childrenServedFromLocalStore() {
    String classId = BASE + "cancer";
    String encoded = URLEncoder.encode(classId, StandardCharsets.UTF_8);
    String url = childrenUrlBase + "/" + ONT + "/" + BP_CLASSES + "/" + encoded + "/" + BP_CHILDREN;

    Response response = clientBuilder.build().target(url).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();

    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    PagedResults<OntologyClass> children = response.readEntity(new GenericType<PagedResults<OntologyClass>>() {
    });
    response.close();

    // "melanoma" is the only child of "cancer" in the local snapshot; served without BioPortal.
    Assertions.assertEquals(Integer.valueOf(1), children.getTotalCount());
    OntologyClass child = children.getCollection().get(0);
    Assertions.assertEquals(BASE + "melanoma", child.getLdId());
    Assertions.assertEquals("Melanoma", child.getPrefLabel());
  }

  @Test
  public void versionsServedFromLocalStore() {
    String url = childrenUrlBase + "/" + ONT + "/versions";
    Response response = clientBuilder.build().target(url).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();

    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    List<OntologyVersion> versions = response.readEntity(new GenericType<List<OntologyVersion>>() {});
    response.close();

    // Both ingested versions are reported, keyed by content-hash id.
    Assertions.assertEquals(Set.of("v1", "v2"),
        versions.stream().map(OntologyVersion::versionId).collect(Collectors.toSet()));
    // Exactly one is the current version, and it is v2 (the tagged latest).
    List<OntologyVersion> latest = versions.stream().filter(OntologyVersion::latest).collect(Collectors.toList());
    Assertions.assertEquals(1, latest.size());
    Assertions.assertEquals("v2", latest.get(0).versionId());
    // Each entry carries the full triple: effectiveDate (the release day) alongside the id and the
    // declared version. v2 released 2025-06-01.
    Assertions.assertEquals("2025-06-01", latest.get(0).effectiveDate());
    Assertions.assertEquals("2.0", latest.get(0).version());
  }

  @Test
  public void currentVersionTripleServedFromLocalStore() {
    String url = childrenUrlBase + "/" + ONT + "/versions/current";
    Response response = clientBuilder.build().target(url).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();

    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    VersionTriple triple = response.readEntity(VersionTriple.class);
    response.close();

    // The current snapshot is the tagged latest (v2): its content-hash id, release day, and label.
    Assertions.assertEquals("v2", triple.id());
    Assertions.assertEquals("2025-06-01", triple.effectiveDate());
    Assertions.assertEquals("2.0", triple.declaredVersion());
  }

  @Test
  public void valueSetCollectionCurrentVersionServedFromLocalStore() {
    String url = "http://localhost:" + RULE.getLocalPort() + "/" + BP_ENDPOINT
        + "/vs-collections/version-current?collection=" + VS;
    Response response = clientBuilder.build().target(url).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();

    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    VersionTriple triple = response.readEntity(VersionTriple.class);
    response.close();

    // The collection's current snapshot triple — resolved even though LOCALVS is not in the ontology
    // serving allowlist, because it is marked a value-set collection in the catalog.
    Assertions.assertEquals("vs1", triple.id());
    Assertions.assertEquals("2024-05-01", triple.effectiveDate());
  }

  @Test
  public void valueSetCollectionCurrentVersionIs404ForAnOntologyOrUnknown() {
    // An ordinary ontology acronym is not a value-set collection (the kind guard), and an unknown one
    // is not served — both 404, so a value-set constraint pointing at neither is left unpinned.
    for (String collection : new String[]{ONT, "NOPE"}) {
      String url = "http://localhost:" + RULE.getLocalPort() + "/" + BP_ENDPOINT
          + "/vs-collections/version-current?collection=" + collection;
      Response response = clientBuilder.build().target(url).request()
          .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
      response.close();
      Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus(),
          "collection=" + collection + " must not resolve");
    }
  }

  @Test
  public void classCurrentVersionServedForLocalConceptIri() {
    // A class-valued constraint names a term IRI but not its ontology. The IRI's namespace is mapped
    // back to LOCALTEST, whose current triple (v2) is returned — the freeze-on-publish path for a
    // class constraint. This is the only endpoint that resolves an ontology from a bare term IRI.
    String url = "http://localhost:" + RULE.getLocalPort() + "/" + BP_ENDPOINT + "/classes/version-current?uri="
        + URLEncoder.encode(BASE + "melanoma", StandardCharsets.UTF_8);
    Response response = clientBuilder.build().target(url).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();

    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    VersionTriple triple = response.readEntity(VersionTriple.class);
    response.close();

    Assertions.assertEquals("v2", triple.id());
    Assertions.assertEquals("2025-06-01", triple.effectiveDate());
    Assertions.assertEquals("2.0", triple.declaredVersion());
  }

  @Test
  public void classCurrentVersionIs404ForAnUnservedNamespace() {
    // A term whose namespace maps to no locally served ontology cannot be pinned — 404, so a class
    // constraint pointing outside the local store is left unpinned rather than mis-resolved.
    String url = "http://localhost:" + RULE.getLocalPort() + "/" + BP_ENDPOINT + "/classes/version-current?uri="
        + URLEncoder.encode("http://elsewhere.example/nope", StandardCharsets.UTF_8);
    Response response = clientBuilder.build().target(url).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    response.close();
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  public void integratedSearchResolvesAPinByDeclaredVersion() {
    // The pin is the self-declared label ("1.0"/"2.0"), not the content-hash id ("v1"/"v2") that
    // integratedSearchHonoursPinnedVersionOverHttp uses — the declared-version branch of the resolver
    // reached over HTTP. This is exactly the shape a frozen template carries. v1 has 3 concepts, v2 4.
    Assertions.assertEquals(3, integratedSearchOntologyConceptCount(",\"version\":\"1.0\""));
    Assertions.assertEquals(4, integratedSearchOntologyConceptCount(",\"version\":\"2.0\""));
  }

  @Test
  public void integratedSearchResolvesAPinByAsOfDate() {
    // A date pin serves the newest snapshot released on or before it (as-of-date resolution). v1
    // released 2025-01-01, v2 2025-06-01: a 2025-03-01 pin lands on v1 (3 concepts), a later date on
    // v2 (4). (A date before either would resolve to empty and fall back to the remote adapter, which
    // is unconfigured here, so that boundary is left to the store-layer unit tests.)
    Assertions.assertEquals(3, integratedSearchOntologyConceptCount(",\"version\":\"2025-03-01\""));
    Assertions.assertEquals(4, integratedSearchOntologyConceptCount(",\"version\":\"2025-09-01\""));
  }

  @Test
  public void versionDiffServedFromLocalStore() {
    String url = childrenUrlBase + "/" + ONT + "/versions/diff?from=v1&to=v2";
    Response response = clientBuilder.build().target(url).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();

    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    VersionDiff diff = response.readEntity(VersionDiff.class);
    response.close();

    // v2 added exactly one concept ("infection") and one subsumption edge; nothing was removed.
    Assertions.assertEquals(3, diff.conceptsBefore());
    Assertions.assertEquals(4, diff.conceptsAfter());
    Assertions.assertEquals(1, diff.conceptsAdded());
    Assertions.assertEquals(0, diff.conceptsRemoved());
    Assertions.assertEquals(1, diff.edgesAdded());
    Assertions.assertTrue(diff.sampleAddedConcepts().contains(BASE + "infection"),
        "the added concept IRI is sampled in the diff");
  }

  @Test
  public void versionDiffForUnknownVersionIs404() {
    String url = childrenUrlBase + "/" + ONT + "/versions/diff?from=v1&to=nope";
    Response response = clientBuilder.build().target(url).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    response.close();
    Assertions.assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  /**
   * Serve-at-version through the real integrated-search HTTP endpoint, JSON body included. The
   * ontology constraint's optional {@code version} is a plain field with a getter but no setter, so
   * this is the test that proves Jackson does not silently drop it on the way in — the feature is
   * only meaningful if a pinned version actually reaches the store. Enumerating LOCALTEST (empty
   * input text) returns its 3 concepts pinned to v1 and its 4 at latest (v2, which added "infection").
   * Service-level tests construct the constraint in Java and so cannot catch a deserialization gap.
   */
  @Test
  public void integratedSearchHonoursPinnedVersionOverHttp() {
    Assertions.assertEquals(3, integratedSearchOntologyConceptCount(",\"version\":\"v1\""));
    Assertions.assertEquals(4, integratedSearchOntologyConceptCount("")); // no version -> latest (v2)
  }

  @Test
  public void integratedSearchHonoursPinnedVersionForABranch() {
    // A branch (a class + its descendants) served at a pinned version. The "disease" branch spans the
    // whole tree; v2 adds "infection" under "disease", so latest carries exactly one more concept than
    // v1. Pinning v1 must serve the smaller v1 subtree, proving the pin reaches the branch read path.
    int pinnedV1 = integratedSearchCount("{\"ontologies\":[],\"branches\":[{\"acronym\":\"" + ONT
        + "\",\"uri\":\"" + BASE + "disease\",\"version\":\"v1\"}],\"valueSets\":[],\"classes\":[]}");
    int latest = integratedSearchCount("{\"ontologies\":[],\"branches\":[{\"acronym\":\"" + ONT
        + "\",\"uri\":\"" + BASE + "disease\"}],\"valueSets\":[],\"classes\":[]}");
    Assertions.assertTrue(pinnedV1 >= 2, "v1 branch is non-empty");
    Assertions.assertEquals(pinnedV1 + 1, latest, "v2 adds exactly one concept to the disease branch");
  }

  @Test
  public void integratedSearchHonoursPinnedVersionForAValueSet() {
    // A value set's members are the children of its root class in the collection snapshot. Under
    // "disease": 1 child (cancer) in vs1, 2 (cancer, infection) in vs2. Pinning each serves that version.
    Assertions.assertEquals(1, integratedSearchCount("{\"ontologies\":[],\"branches\":[],\"valueSets\":[{"
        + "\"vsCollection\":\"" + VS + "\",\"uri\":\"" + BASE + "disease\",\"version\":\"vs1\"}],\"classes\":[]}"));
    Assertions.assertEquals(2, integratedSearchCount("{\"ontologies\":[],\"branches\":[],\"valueSets\":[{"
        + "\"vsCollection\":\"" + VS + "\",\"uri\":\"" + BASE + "disease\",\"version\":\"vs2\"}],\"classes\":[]}"));
  }

  @Test
  public void integratedSearchToleratesTheFrozenSpecFieldsOnAClassConstraint() {
    // A frozen template carries iri / sourceSystem / version on a class entry too. An enumerated class
    // is self-describing (uri + prefLabel used as-is), so those fields are not consumed — but the
    // request must still deserialize and return the class rather than 400 on the extra fields.
    int count = integratedSearchCount("{\"ontologies\":[],\"branches\":[],\"valueSets\":[],\"classes\":[{"
        + "\"uri\":\"" + BASE + "cancer\",\"prefLabel\":\"Cancer\",\"type\":\"OntologyClass\","
        + "\"source\":\"" + ONT + "\",\"iri\":\"" + BASE + "cancer\",\"sourceSystem\":\"bioportal\","
        + "\"version\":{\"id\":\"v1\",\"effectiveDate\":\"2025-01-01\",\"declaredVersion\":\"1.0\"}}]}");
    Assertions.assertEquals(1, count);
  }

  /** POST integrated-search with an explicit valueConstraints object (inner JSON) and return the total. */
  private static int integratedSearchCount(String valueConstraintsJson) {
    String url = "http://localhost:" + RULE.getLocalPort() + "/" + BP_ENDPOINT + "/integrated-search";
    String body = "{\"parameterObject\":{\"valueConstraints\":" + valueConstraintsJson
        + ",\"inputText\":\"\"},\"page\":1,\"pageSize\":50}";
    Response response = clientBuilder.build().target(url).request().post(Entity.json(body));
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    PagedResults<SearchResult> results = response.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    response.close();
    return results.getTotalCount();
  }

  /** POST integrated-search enumerating one ontology constraint (empty input text) and return its
   *  total result count. {@code versionSuffix} is either {@code ""} or {@code ,"version":"..."}. */
  private static int integratedSearchOntologyConceptCount(String versionSuffix) {
    String url = "http://localhost:" + RULE.getLocalPort() + "/" + BP_ENDPOINT + "/integrated-search";
    String body = "{\"parameterObject\":{\"valueConstraints\":{"
        + "\"ontologies\":[{\"acronym\":\"" + ONT + "\"" + versionSuffix + "}],"
        + "\"branches\":[],\"valueSets\":[],\"classes\":[]},\"inputText\":\"\"},"
        + "\"page\":1,\"pageSize\":50}";
    Response response = clientBuilder.build().target(url).request().post(Entity.json(body));
    Assertions.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    PagedResults<SearchResult> results =
        response.readEntity(new GenericType<PagedResults<SearchResult>>() {});
    response.close();
    return results.getTotalCount();
  }
}
