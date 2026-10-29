package org.metadatacenter.terms.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class EpaPfasSourceTest {
  @TempDir Path directory;
  private HttpServer server;
  private EpaPfasSource source;
  private final ObjectMapper json = new ObjectMapper();
  private final List<String> headerViolations = new ArrayList<>();
  private String failure = "";
  private String alias = "PFOA";
  private static final String PFOA = "DTXSID8031865";
  private static final String PFOS = "DTXSID3031864";
  private static final String KEY = "test-credential-never-archived";

  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String path = exchange.getRequestURI().getPath();
      String key = exchange.getRequestHeaders().getFirst("x-api-key");
      if (path.startsWith("/ctx/") ? !KEY.equals(key) : key != null) headerViolations.add(path);
      String body = response(path);
      if (path.contains("by-dtxsid")) {
        if (!"POST".equals(exchange.getRequestMethod())) headerViolations.add("not POST");
        JsonNode ids = json.readTree(exchange.getRequestBody());
        if (ids.size() != 2) headerViolations.add("incomplete batch");
      }
      int status = failure.equals("http") && path.startsWith("/ctx/") ? 403 : 200;
      byte[] bytes = (status == 403 ? KEY : body).getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    source = new EpaPfasSource(KEY, URI.create(base + "/srs/"), URI.create(base + "/ctx/"));
  }

  @AfterEach void stop() { server.stop(0); }

  private String response(String path) {
    if (path.endsWith("/477")) return "{\"subsListAcronym\":\"PFAS Method 533\",\"synonymCount\":2}";
    if (path.endsWith("/475")) return "{\"subsListAcronym\":\"PFAS Method 537.1\",\"synonymCount\":1}";
    String pfoa = "{\"currentCasNumber\":\"335-67-1\",\"epaName\":\"PFOA EPA name\",\"dtxsid\":\""
        + (failure.equals("identity") ? PFOS : "") + "\",\"synonyms\":["
        + "{\"synonymName\":\"SRS alias\",\"activeStatus\":true},"
        + "{\"synonymName\":\"withdrawn name\",\"activeStatus\":false}]}";
    if (path.endsWith("PFAS Method 533")) return "[" + pfoa
        + (failure.equals("truncated") ? "" : ",{\"currentCasNumber\":\"1763-23-1\",\"dtxsid\":\"" + PFOS + "\"}") + "]";
    if (path.endsWith("PFAS Method 537.1")) return "[" + pfoa + "]";
    if (path.contains("/chemical/search/equal/")) {
      boolean acid = path.endsWith("335-67-1");
      String cas = acid ? "335-67-1" : "1763-23-1";
      String id = acid ? PFOA : PFOS;
      return "[{\"casrn\":\"" + cas + "\",\"dtxsid\":\"" + id + "\"}"
          + (failure.equals("ambiguous") ? ",{\"casrn\":\"" + cas + "\",\"dtxsid\":\"DTXSID9999999\"}" : "") + "]";
    }
    if (path.contains("/chemical/detail/")) return "["
        + "{\"dtxsid\":\"" + PFOA + "\",\"casrn\":\"335-67-1\",\"preferredName\":\"Perfluorooctanoic acid\"}"
        + (failure.equals("details") ? "" : ",{\"dtxsid\":\"" + PFOS + "\",\"casrn\":\"1763-23-1\",\"preferredName\":\"Perfluorooctanesulfonic acid\"}") + "]";
    // Missing synonyms for a substance are allowed: the live EPA endpoint omits some records.
    return "[{\"dtxsid\":\"" + PFOA + "\",\"beilstein\":[\"" + alias
        + "\"],\"good\":[\"good name\"],\"deleted\":[\"deleted name\"],\"other\":[\"untrusted name\"]}]";
  }

  @Test void archivesEvidenceAndBuildsThreeSetsWithCanonicalLinksAndSearchableNames() throws Exception {
    Path archive = source.download(EpaPfasSource.ACRONYM, 1, directory);
    String raw = Files.readString(archive);
    assertFalse(raw.contains(KEY));
    JsonNode bundle = json.readTree(raw);
    assertEquals(8, bundle.path("responses").size());
    assertEquals(2, bundle.path("chemicals").size());
    assertEquals(List.of(2, 1, 2), List.of(bundle.path("sets").get(0).path("members").size(),
        bundle.path("sets").get(1).path("members").size(), bundle.path("sets").get(2).path("members").size()));
    assertTrue(headerViolations.isEmpty(), headerViolations.toString());
    try (SnapshotStore store = SnapshotStore.openInMemory()) {
      store.initSchema();
      EpaPfasExtractor.extract(archive, store);
      String iri = EpaPfasSource.CHEMICAL_IRI + PFOA;
      for (String query : List.of("PFOA", "335-67-1", PFOA, "SRS alias", "good name")) {
        assertEquals(List.of(iri), store.searchByLabelUnderRoot(EpaPfasSource.COMBINED_IRI, query, false, 0, 1)
            .stream().map(SnapshotStore.Concept::iri).toList(), query);
      }
      for (String query : List.of("deleted name", "untrusted name", "withdrawn name")) {
        assertTrue(store.searchByLabelUnderRoot(EpaPfasSource.COMBINED_IRI, query, false, 0, 1).isEmpty());
      }
    }
  }

  @Test void rejectsIncompleteAmbiguousAndConflictingUpstreamDataBeforePublishing() throws Exception {
    for (String problem : List.of("truncated", "ambiguous", "identity", "details", "http")) {
      failure = problem;
      try (CatalogStore catalog = CatalogStore.openInMemory()) {
        catalog.initSchema();
        Exception error = assertThrows(Exception.class, () -> new IngestJob(source)
            .ingestValueSetCollectionLatest(catalog, EpaPfasSource.ACRONYM, directory.resolve(problem)), problem);
        assertFalse(error.toString().contains(KEY));
        assertTrue(catalog.listOntologies().isEmpty(), problem);
      }
    }
  }

  @Test void changedNamesCreateANewPinAndOldSnapshotRemainsSearchable() throws Exception {
    try (CatalogStore catalog = CatalogStore.openFile(directory.resolve("catalog.sqlite").toString())) {
      catalog.initSchema();
      IngestJob job = new IngestJob(source);
      var first = job.ingestValueSetCollectionLatest(catalog, EpaPfasSource.ACRONYM, directory.resolve("snapshots"));
      var firstSnapshot = catalog.resolveLatest(EpaPfasSource.ACRONYM).orElseThrow();
      assertEquals(null, firstSnapshot.releasedAt(), "EPA capture time is not a source release date");
      var unchanged = job.ingestValueSetCollectionLatest(catalog, EpaPfasSource.ACRONYM, directory.resolve("snapshots"));
      assertEquals(first.versionId(), unchanged.versionId());
      assertEquals(firstSnapshot, catalog.resolveLatest(EpaPfasSource.ACRONYM).orElseThrow());
      alias = "replacement abbreviation";
      var second = job.ingestValueSetCollectionLatest(catalog, EpaPfasSource.ACRONYM, directory.resolve("snapshots"));
      assertNotEquals(first.versionId(), second.versionId());
      assertEquals(2, catalog.listSnapshots(EpaPfasSource.ACRONYM).size());
      var history = catalog.ingestHistory(EpaPfasSource.ACRONYM);
      assertEquals(3, history.size());
      assertEquals(List.of(first.versionId(), first.versionId(), second.versionId()),
          history.stream().map(CatalogStore.IngestEvent::versionId).toList());
      for (var event : history) {
        assertEquals(null, event.releasedAt());
        assertTrue(Files.exists(Path.of(event.rawFilePath())));
        assertEquals(event.rawFileHash(), PropertyBackfillJob.sha256(Path.of(event.rawFilePath())));
      }
      assertEquals(List.of(EpaPfasSource.ACRONYM), catalog.listValueSetCollections());
      assertEquals("epa", catalog.snapshotProvenance(second.versionId(), EpaPfasSource.ACRONYM).orElseThrow().backend());
      try (SnapshotStore old = SnapshotStore.openFile(first.snapshotFile().toString())) {
        assertEquals(1, old.searchByLabelUnderRoot(EpaPfasSource.COMBINED_IRI, "PFOA", false, 0, 1).size());
        assertTrue(old.searchByLabelUnderRoot(EpaPfasSource.COMBINED_IRI, alias, false, 0, 1).isEmpty());
      }
    }
  }
}
