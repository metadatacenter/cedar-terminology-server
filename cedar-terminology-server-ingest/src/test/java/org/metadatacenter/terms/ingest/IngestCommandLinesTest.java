package org.metadatacenter.terms.ingest;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The store is built and maintained by command lines an operator runs: the ingest itself, which fetches an
 * ontology's release and writes its snapshot and catalog entry, the job's maintenance passes over an existing store,
 * the search index, and the one-time catalog conversions the versioning runbook lists. Each runs here as an
 * operator would run it, against one store: a small ontology fetched from a local server by URL.
 */
@Tag("integration")
class IngestCommandLinesTest {

  private static final String ACRONYM = "DTEST";
  private static final String BASE = "http://example.org/dtest/";

  @TempDir
  static Path dir;

  private static HttpServer server;
  private static Path catalog;
  private static Path snapshots;
  private static Path ontologyFile;

  @BeforeAll
  static void ingestTheOntology() throws Exception {
    ontologyFile = dir.resolve("dtest.owl");
    writeOntology(ontologyFile);
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/files/dtest.owl", exchange -> {
      byte[] bytes = Files.readAllBytes(ontologyFile);
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    server.start();
    catalog = dir.resolve("catalog.sqlite");
    snapshots = dir.resolve("snapshots");

    String printed = run(() -> IngestJob.main(new String[]{catalog.toString(), snapshots.toString(), "--source", "url",
        "--url", url(), ACRONYM}));

    assertTrue(printed.contains(ACRONYM), printed);
    try (CatalogStore store = CatalogStore.openFile(catalog.toString())) {
      CatalogStore.SnapshotInfo latest = store.resolveLatest(ACRONYM).orElseThrow();
      try (SnapshotStore snapshot = SnapshotStore.openFile(latest.filePath())) {
        assertTrue(snapshot.contains(BASE + "melanoma"), "the snapshot holds the ontology's classes");
      }
    }
  }

  @AfterAll
  static void stopTheServer() {
    server.stop(0);
  }

  @Test
  void theJobsMaintenancePassesRunOverTheStore() throws Exception {
    assertTrue(job("--verify").contains("1 ok"), "a sound store verifies");
    assertTrue(job("--verify", "--deep").contains("verify (deep): 1 snapshots"));
    assertTrue(job("--backfill-labels", ACRONYM).startsWith("backfill-labels:"));
    assertTrue(job("--backfill-labels-from-raw", ACRONYM).startsWith("backfill-labels-from-raw:"));
    assertTrue(job("--backfill-definitions-from-raw", ACRONYM).startsWith("backfill-definitions-from-raw:"));
    assertTrue(job("--refetch-definitions", ACRONYM).startsWith("refetch-definitions:"));
    assertTrue(job("--backfill-header-iri", ACRONYM).startsWith("backfill-header-iri:"));
    assertTrue(job("--prune-orphans").contains("dry-run"), "pruning without --apply deletes nothing");
    assertTrue(job("--retain-raw-by-hash").startsWith("retain-raw-by-hash:"));
    assertTrue(job("--verify").contains("1 ok"), "the store is still sound after every pass");
  }

  @Test
  void theSearchIndexIsBuiltFromTheCatalog() throws Exception {
    Path index = dir.resolve("index.sqlite");
    String printed = run(() -> SearchIndexJob.main(new String[]{catalog.toString(), index.toString()}));
    assertTrue(printed.contains("indexed " + ACRONYM), printed);
    String again = run(() -> SearchIndexJob.main(new String[]{catalog.toString(), index.toString(), "--acronyms", ACRONYM}));
    assertFalse(again.contains("indexed " + ACRONYM), "an index that is current is not built again: " + again);
  }

  /** The one-time conversions report on a store they have nothing to change in, and change nothing. */
  @Test
  void theCatalogConversionsRunAndChangeNothingInACurrentStore() throws Exception {
    String versionBefore = latestVersion();
    // The report lists an ontology with more than one snapshot by default, so this one is named.
    String report = run(() -> ContentHashReport.main(new String[]{catalog.toString(), ACRONYM}));
    assertTrue(report.contains("Totals: 1 snapshots across 1 ontologies"), report);
    assertTrue(run(() -> ContentHashCutover.main(new String[]{catalog.toString()})).contains("Dry run"));
    run(() -> DeriveOntologyIriBackfill.main(new String[]{catalog.toString(), ACRONYM}));
    run(() -> ProvenanceBackfill.main(new String[]{catalog.toString()}));
    run(() -> IriDeconfliction.main(new String[]{catalog.toString()}));
    run(() -> PruneRootsBackfill.main(new String[]{catalog.toString()}));
    run(() -> PropertyBackfillJob.main(new String[]{catalog.toString(), ACRONYM}));
    assertEquals(versionBefore, latestVersion(), "a dry run or a pass with nothing to do leaves the version alone");
    assertTrue(job("--verify").contains("1 ok"));
  }

  /** Runs a maintenance pass. Each pass that needs a source gets the URL source; the default needs a BioPortal key. */
  private static String job(String... options) throws Exception {
    String[] args = new String[options.length + 6];
    args[0] = catalog.toString();
    args[1] = snapshots.toString();
    args[2] = "--source";
    args[3] = "url";
    args[4] = "--url";
    args[5] = url();
    System.arraycopy(options, 0, args, 6, options.length);
    return run(() -> IngestJob.main(args)).strip();
  }

  private static String latestVersion() throws Exception {
    try (CatalogStore store = CatalogStore.openFile(catalog.toString())) {
      return store.resolveLatest(ACRONYM).orElseThrow().versionId();
    }
  }

  private static String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/files/dtest.owl";
  }

  private interface Main {
    void run() throws Exception;
  }

  /** Runs a command line and keeps what it prints. */
  private static String run(Main main) throws Exception {
    PrintStream original = System.out;
    ByteArrayOutputStream printed = new ByteArrayOutputStream();
    System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
    try {
      main.run();
    } catch (Exception | Error e) {
      original.print(printed.toString(StandardCharsets.UTF_8));
      throw e;
    } finally {
      System.setOut(original);
    }
    return printed.toString(StandardCharsets.UTF_8);
  }

  /** disease, with the child cancer, whose child is melanoma; each class labeled and defined. */
  private static void writeOntology(Path file) throws Exception {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    OWLDataFactory factory = manager.getOWLDataFactory();
    OWLOntology ontology = manager.createOntology(IRI.create(BASE + "dtest.owl"));
    OWLClass disease = factory.getOWLClass(IRI.create(BASE + "disease"));
    OWLClass cancer = factory.getOWLClass(IRI.create(BASE + "cancer"));
    OWLClass melanoma = factory.getOWLClass(IRI.create(BASE + "melanoma"));
    manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(cancer, disease));
    manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(melanoma, cancer));
    for (OWLClass c : new OWLClass[]{disease, cancer, melanoma}) {
      String name = c.getIRI().getShortForm();
      manager.addAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(
          factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()), c.getIRI(), factory.getOWLLiteral(name)));
      manager.addAxiom(ontology, factory.getOWLAnnotationAssertionAxiom(
          factory.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115")), c.getIRI(),
          factory.getOWLLiteral("A " + name + ".")));
    }
    try (OutputStream out = Files.newOutputStream(file)) {
      manager.saveOntology(ontology, out);
    }
  }
}
