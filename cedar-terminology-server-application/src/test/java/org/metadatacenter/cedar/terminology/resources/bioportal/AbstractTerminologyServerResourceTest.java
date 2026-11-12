package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.cedar.terminology.resources.FakeBioPortal;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.util.test.TestAuthUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.metadatacenter.cedar.terminology.util.Constants.BP_PROPERTY_SEARCH;
import static org.metadatacenter.cedar.terminology.util.Constants.BP_SEARCH;
import static org.metadatacenter.cedar.terminology.utils.Constants.*;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * The harness the BioPortal resource tests share: a terminology server on the real HTTP stack, reading a
 * {@link FakeBioPortal} started for each test class, and a client signed in as the first test user.
 */
@Tag("integration")
public abstract class AbstractTerminologyServerResourceTest {

  protected static final String BASE_URL = "http://localhost";

  /**
   * Status codes not part of the JAX-RS Status enum.
   */
  protected static final int STATUS_CODE_UNPROCESSABLE_ENTITY = 422;

  public static final DropwizardTestSupport<TerminologyServerConfiguration> RULE =
      new DropwizardTestSupport<>(TerminologyServerApplication.class,
          ResourceHelpers.resourceFilePath("test-config.yml"));

  protected static FakeBioPortal bioPortal;
  protected static Client client;
  protected static String authHeader;
  protected static ObjectMapper mapper;
  protected static String baseUrlBp;
  protected static String baseUrlBpSearch;
  protected static String baseUrlBpIntegratedSearch;
  protected static String baseUrlBpIntegratedRetrieve;
  protected static String baseUrlBpPropertySearch;
  protected static String baseUrlBpOntologies;
  protected static String baseUrlBpVSCollections;

  @BeforeAll
  public static void oneTimeSetUpAbstract() throws Exception {
    bioPortal = new FakeBioPortal();
    // The server reads its environment as it boots, so the override comes first. Its ports are assigned by the
    // system, so it never collides with a running development server.
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_STOP_PORT", "0");
    environment.put("CEDAR_BIOPORTAL_REST_BASE", bioPortal.base());
    CedarEnvironmentSource.setOverride(environment);
    RULE.before();

    CedarConfig cedarConfig =
        CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_TERMINOLOGY));
    // The application wires a Neo4j-backed user service. An in-memory one authenticates the API key without
    // Neo4j or Keycloak.
    TestAuthUtil.installInMemoryUserService(cedarConfig);
    authHeader = TestAuthUtil.getTestUser1AuthHeader(cedarConfig);

    baseUrlBp = BASE_URL + ":" + RULE.getLocalPort() + "/" + BP_ENDPOINT;
    baseUrlBpSearch = baseUrlBp + "/" + BP_SEARCH;
    baseUrlBpIntegratedSearch = baseUrlBp + "/" + BP_INTEGRATED_SEARCH;
    baseUrlBpIntegratedRetrieve = baseUrlBp + "/" + BP_INTEGRATED_RETRIEVE;
    baseUrlBpPropertySearch = baseUrlBp + "/" + BP_PROPERTY_SEARCH;
    baseUrlBpOntologies = baseUrlBp + "/" + BP_ONTOLOGIES;
    baseUrlBpVSCollections = baseUrlBp + "/" + BP_VALUE_SET_COLLECTIONS;

    // A request the server never answers fails its test rather than holding up the build.
    client = ClientBuilder.newBuilder()
        .property(ClientProperties.CONNECT_TIMEOUT, 10_000)
        .property(ClientProperties.READ_TIMEOUT, 60_000)
        .build();
    mapper = new ObjectMapper();
  }

  @AfterAll
  public static void oneTimeTearDownAbstract() {
    client.close();
    RULE.after();
    bioPortal.close();
  }

  /** A GET or POST to the server, signed in as the first test user. */
  protected static Invocation.Builder request(String url) {
    return client.target(url).request().header(HTTP_HEADER_AUTHORIZATION, authHeader);
  }

  /** What a route answers, asserted to be 200 with a JSON body, read as the given type. */
  protected static <T> T read(String url, GenericType<T> type) {
    try (Response response = request(url).get()) {
      if (response.getStatus() != Status.OK.getStatusCode()) {
        Assertions.fail(url + " answered " + response.getStatus() + ": " + response.readEntity(String.class));
      }
      Assertions.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE), url);
      return response.readEntity(type);
    }
  }

  protected static <T> T read(String url, Class<T> type) {
    return read(url, new GenericType<>(type));
  }

  /** The status a route answers. */
  protected static int statusOf(String url) {
    try (Response response = request(url).get()) {
      return response.getStatus();
    }
  }

  /** Asserts a page's paging fields, in the order its JSON gives them. */
  protected static void assertPaging(PagedResults<?> results, Integer page, Integer pageCount, Integer pageSize,
                                     Integer totalCount, Integer prevPage, Integer nextPage) {
    Assertions.assertEquals(Arrays.asList(page, pageCount, pageSize, totalCount, prevPage, nextPage),
        Arrays.asList(results.getPage(), results.getPageCount(), results.getPageSize(), results.getTotalCount(),
            results.getPrevPage(), results.getNextPage()),
        "page, pageCount, pageSize, totalCount, prevPage, nextPage");
  }

  /** The child of a tree node with the given identifier, failing when the node was not expanded to it. */
  protected static TreeNode child(TreeNode node, String iri) {
    return node.getChildren().stream().filter(c -> c.getLdId().equals(iri)).findFirst()
        .orElseThrow(() -> new AssertionError(node.getLdId() + " was not expanded to " + iri));
  }

  protected static String encode(String iri) {
    return URLEncoder.encode(iri, StandardCharsets.UTF_8);
  }
}
