package org.metadatacenter.terms.ingest;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ingest reads an ontology's releases from BioPortal, or from another OntoPortal at a given base address: who
 * may read the ontology, the list of its submissions, and each submission's file. The API key goes in a header,
 * never the address, and the file lands in the target folder whatever name the server gives it.
 */
@Tag("integration")
class BioPortalDownloaderTest {

  private HttpServer server;
  private final List<String> authorizations = new CopyOnWriteArrayList<>();
  private String fileName = "dtest.owl";
  private int downloadStatus = 200;

  @BeforeEach
  void startTheOntoPortal() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
      String path = exchange.getRequestURI().getPath();
      String body;
      int status = 200;
      if (path.equals("/ontologies/DTEST")) {
        body = "{\"viewingRestriction\": \"public\", \"hasLicense\": \"CC-BY\", \"name\": \"Disease Test\"}";
      } else if (path.equals("/ontologies/DTEST/submissions")) {
        body = "[{\"submissionId\": 1, \"version\": \"v1\", \"released\": \"2024-01-01\", \"hasOntologyLanguage\": \"OWL\"},"
            + " {\"submissionId\": 2, \"version\": \"v2\", \"released\": \"2025-01-01\", \"hasOntologyLanguage\": \"OWL\"}]";
      } else if (path.equals("/ontologies/EMPTY/submissions")) {
        body = "[]";
      } else if (path.startsWith("/ontologies/DTEST/submissions/") && path.endsWith("/download")) {
        status = downloadStatus;
        body = "<rdf:RDF/>";
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
      } else {
        status = 404;
        body = "{}";
      }
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    server.start();
  }

  @AfterEach
  void stopTheOntoPortal() {
    server.stop(0);
  }

  @Test
  void theOntologysAccessAndSubmissionsAreRead() throws Exception {
    BioPortalDownloader portal = new BioPortalDownloader("the-key", base() + "/");

    OntologyAccess access = portal.accessInfo("DTEST");
    assertEquals("public", access.viewingRestriction());
    assertEquals(2, portal.listSubmissions("DTEST").size());
    assertEquals(2, portal.latestSubmission("DTEST").submissionId(), "the latest submission has the highest id");
    assertEquals("v2", portal.latestSubmission("DTEST").version());
    assertThrows(IOException.class, () -> portal.latestSubmission("EMPTY"));
    assertThrows(IOException.class, () -> portal.accessInfo("MISSING"));
    assertThrows(IOException.class, () -> portal.listSubmissions("MISSING"));
    assertTrue(authorizations.stream().allMatch("apikey token=the-key"::equals), authorizations.toString());
  }

  @Test
  void aSubmissionIsDownloadedIntoTheTargetFolder(@TempDir Path dir) throws Exception {
    BioPortalDownloader portal = new BioPortalDownloader("the-key", base());
    Path target = dir.resolve("downloads");

    Path file = portal.download("DTEST", 2, target);

    assertEquals(target.resolve("dtest.owl"), file, "the file takes the name the server gives it");
    assertEquals("<rdf:RDF/>", Files.readString(file));
  }

  @Test
  void aNameThatClimbsOutOfTheFolderIsReducedToItsLastPart(@TempDir Path dir) throws Exception {
    fileName = "../../escaped.owl";
    Path target = dir.resolve("a/b");

    Path file = new BioPortalDownloader("the-key", base()).download("DTEST", 2, target);

    assertEquals(target.resolve("escaped.owl"), file);
    assertFalse(Files.exists(dir.resolve("escaped.owl")));
    fileName = "..";
    assertEquals(target.resolve("DTEST_sub2"), new BioPortalDownloader("the-key", base()).download("DTEST", 2, target),
        "a name with nothing usable falls back to the acronym and submission");
  }

  @Test
  void aRefusedDownloadSaysWhy(@TempDir Path dir) {
    downloadStatus = 403;
    IOException licensed = assertThrows(IOException.class,
        () -> new BioPortalDownloader("the-key", base()).download("DTEST", 2, dir));
    assertTrue(licensed.getMessage().contains("not permitted"), licensed.getMessage());
    downloadStatus = 500;
    assertThrows(IOException.class, () -> new BioPortalDownloader("the-key", base()).download("DTEST", 2, dir));
  }

  private String base() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }
}
