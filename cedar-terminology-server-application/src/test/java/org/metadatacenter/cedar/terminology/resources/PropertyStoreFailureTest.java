package org.metadatacenter.cedar.terminology.resources;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import io.dropwizard.testing.DropwizardTestSupport;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.metadatacenter.cedar.terminology.LocalStoreTestProperties;
import org.metadatacenter.cedar.terminology.TerminologyServerApplication;
import org.metadatacenter.cedar.terminology.TerminologyServerConfiguration;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotProperties;
import org.metadatacenter.terms.store.SnapshotStore;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The property routes answer a failing ontology store in the shared error body, over the real HTTP stack.
 *
 * <p>The failure answered 500 through an exception that carried no entity, so the body had no message
 * and no errorId, and an operator had nothing to find the logged database exception by. The body now
 * says that the snapshot could not be read and carries an errorId, and the database exception is
 * logged under that errorId and nowhere in the body.
 *
 * <p>The one ontology served here has a snapshot that claims its properties were extracted but has
 * lost the table holding them, so every read of a property fails in SQLite, as a damaged file would.
 * The list of an ontology's versions reads only the catalog and the extraction marker, so this fixture
 * cannot make it fail. It shares the failure path of the other four routes.
 */
class PropertyStoreFailureTest {

  private static final String ONT = "BROKEN";
  private static final String BASE = "http://broken/";
  private static final String MESSAGE = "Could not read the ontology snapshot";
  private static final String JSON = "application/json";

  static {
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_TERMINOLOGY_HTTP_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_ADMIN_PORT", "0");
    environment.put("CEDAR_TERMINOLOGY_STOP_PORT", "0");
    CedarEnvironmentSource.setOverride(environment);
    try {
      Path dir = Files.createTempDirectory("property-store-failure");
      Path snapshot = dir.resolve("snapshot.sqlite");
      try (SnapshotStore s = SnapshotStore.openFile(snapshot.toString())) {
        s.initSchema();
        s.addConcept(BASE + "disease", "Disease");
        s.properties().write(List.of(new SnapshotProperties.Property(BASE + "relatedTo", "object", "related to",
            false, List.of(), List.of())));
        s.materialize();
      }
      try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + snapshot);
           Statement statement = c.createStatement()) {
        statement.execute("DROP TABLE ontology_property");
      }
      Path catalog = dir.resolve("catalog.sqlite");
      try (CatalogStore c = CatalogStore.openFile(catalog.toString())) {
        c.initSchema();
        c.upsertOntology(new CatalogStore.OntologyInfo(ONT, "Broken", null, "OWL"));
        c.addSnapshot(new CatalogStore.SnapshotInfo("b1", ONT, "1.0", "2025-01-01", "2025-01-01T00:00:00Z",
            "OWL", "subsumption", 1, 0, snapshot.toString(), "b1", "open"));
        c.setTag(ONT, CatalogStore.TAG_LATEST, "b1");
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
  private final ListAppender<ILoggingEvent> log = new ListAppender<>();

  @BeforeAll
  static void start() throws Exception {
    SERVER.before();
  }

  @AfterAll
  static void stop() {
    SERVER.after();
    LocalStoreTestProperties.clear();
  }

  @BeforeEach
  void captureTheLog() {
    log.start();
    ((Logger) LoggerFactory.getLogger(CedarResponse.class)).addAppender(log);
  }

  @AfterEach
  void releaseTheLog() {
    ((Logger) LoggerFactory.getLogger(CedarResponse.class)).detachAppender(log);
    log.stop();
  }

  @ParameterizedTest(name = "{0} {1}")
  @CsvSource(delimiter = '|', value = {
      "GET  | /properties?sourceAcronym=BROKEN&propertyIri=http://broken/relatedTo&kind=object |",
      "GET  | /properties/hierarchy?sourceAcronym=BROKEN&propertyIri=http://broken/relatedTo&kind=object |",
      "GET  | /properties/roots?sourceAcronym=BROKEN&kind=object |",
      "POST | /properties/search | {\"query\":\"related\",\"sources\":[{\"sourceAcronym\":\"BROKEN\"}]}"})
  void aStoreFailureNamesItselfAndLogsTheDatabaseExceptionUnderItsErrorId(String method, String path, String body)
      throws Exception {
    HttpResponse<String> response = send(method, path, JSON, body, JSON);

    assertEquals(500, response.statusCode(), response.body());
    assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith(JSON),
        response.headers().map().toString());
    JsonNode error = JsonMapper.STRICT_MAPPER.readTree(response.body());
    assertEquals(500, error.path("statusCode").asInt(), response.body());
    assertEquals("INTERNAL_SERVER_ERROR", error.path("status").asText(), response.body());
    assertEquals(MESSAGE, error.path("message").asText(), response.body());
    String errorId = error.path("errorId").asText();
    assertEquals(errorId, UUID.fromString(errorId).toString(), response.body());
    String text = response.body().toLowerCase(Locale.ROOT);
    for (String leak : List.of("sql", "no such table", "ontology_property", "exception", "snapshot.sqlite")) {
      assertFalse(text.contains(leak), "the body carries " + leak + ": " + response.body());
    }

    ILoggingEvent logged = log.list.stream()
        .filter(event -> event.getFormattedMessage().contains(errorId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("nothing was logged under " + errorId + ": " + log.list));
    assertTrue(logged.getThrowableProxy().getMessage().contains("no such table"),
        logged.getThrowableProxy().getClassName() + ": " + logged.getThrowableProxy().getMessage());
  }

  /**
   * The refusals the routes document beside the store failure answer the shared error body too. The
   * framework refuses each of these before the route runs. An offset that is not a number is answered
   * with a message that identifies the parameter and says what form it takes, without the text of the
   * failed conversion. The other two carry no message.
   */
  @ParameterizedTest(name = "{0}: {1} {2}")
  @CsvSource(delimiter = '|', value = {
      "406 | GET  | /properties/versions?sourceAcronym=BROKEN                 | application/json | | text/plain       |",
      "415 | POST | /properties/search                                        | text/plain | {\"query\":\"related\"} | application/json |",
      "400 | GET  | /properties/roots?sourceAcronym=BROKEN&kind=object&offset=x | application/json | | application/json | "
          + "The query parameter offset must be an integer from -2147483648 to 2147483647"})
  void theOtherDocumentedRefusalsAnswerTheSharedErrorBody(int status, String method, String path,
                                                         String contentType, String body, String accept,
                                                         String message)
      throws Exception {
    HttpResponse<String> response = send(method, path, contentType, body, accept);

    assertEquals(status, response.statusCode(), response.body());
    JsonNode error = JsonMapper.STRICT_MAPPER.readTree(response.body());
    assertEquals(status, error.path("statusCode").asInt(), response.body());
    assertTrue(error.has("errorKey") && error.path("parameters").isObject(), response.body());
    assertTrue(error.has("message"), response.body());
    assertEquals(message, error.path("message").asText(null), response.body());
    assertFalse(response.body().contains("For input string"), response.body());
  }

  private static HttpResponse<String> send(String method, String path, String contentType, String body,
                                           String accept) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + SERVER.getLocalPort()
        + path)).header("Accept", accept);
    if (method.equals("POST")) {
      request.header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body));
    } else {
      request.GET();
    }
    return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
