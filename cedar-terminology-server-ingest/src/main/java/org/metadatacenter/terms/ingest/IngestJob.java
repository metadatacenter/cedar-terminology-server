package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Ingests ontology submissions into version-pinned SQLite snapshots and registers them in the
 * catalog.
 *
 * For each ontology it downloads a submission's raw file (kept as the archival source of truth),
 * hashes it to a reproducible {@code version_id}, extracts the hierarchy into a per-snapshot
 * SQLite file, and records the snapshot in the catalog with the {@code latest} tag pointed at it.
 *
 * Ingestion is a batch process, deliberately separate from the running server: nothing here is on
 * the server's runtime path, so the OWL toolchain stays out of the deployed service.
 */
public class IngestJob {

  private static final Logger log = LoggerFactory.getLogger(IngestJob.class);

  /** Result of ingesting one snapshot. */
  public record IngestResult(String versionId, Path snapshotFile, int classCount, int edgeCount) {}

  private final SubmissionSource source;
  private final OwlHierarchyExtractor extractor;

  public IngestJob(SubmissionSource source, OwlHierarchyExtractor extractor) {
    this.source = source;
    this.extractor = extractor;
  }

  /**
   * Ingests the latest submission of an ontology into a new snapshot under {@code snapshotDir} and
   * points {@code latest} at it in the catalog. Currently handles OWL/OBO submissions (extracted
   * via OWLAPI); the hierarchy status is recorded as {@code subsumption}.
   */
  public IngestResult ingestLatest(CatalogStore catalog, String acronym, Path snapshotDir)
      throws IOException, InterruptedException, SQLException {
    Submission sub = source.latestSubmission(acronym);
    log.info("Ingesting {} submission {} (version {}, format {})",
        acronym, sub.submissionId(), sub.version(), sub.format());

    Path ontoDir = snapshotDir.resolve(acronym);
    Path raw = source.download(acronym, sub.submissionId(), ontoDir.resolve("raw"));
    String versionId = sha256(raw);

    Path snapshotFile = ontoDir.resolve(versionId + ".sqlite");
    Files.deleteIfExists(snapshotFile);
    OwlHierarchyExtractor.Result extracted;
    try (SnapshotStore store = SnapshotStore.openFile(snapshotFile.toString())) {
      store.initSchema();
      extracted = extractor.extractFromFile(raw.toFile(), store);
    } catch (Exception e) {
      throw new IOException("Extraction failed for " + acronym + " submission " + sub.submissionId(), e);
    }

    catalog.upsertOntology(new CatalogStore.OntologyInfo(acronym, acronym, null, sub.format()));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo(
        versionId, acronym, sub.version(), sub.released(), Instant.now().toString(),
        sub.format(), "subsumption", extracted.classCount(), extracted.edgeCount(),
        snapshotFile.toString(), versionId, "open"));
    catalog.setTag(acronym, CatalogStore.TAG_LATEST, versionId);

    log.info("Ingested {} -> {} ({} classes, {} edges)",
        acronym, versionId, extracted.classCount(), extracted.edgeCount());
    return new IngestResult(versionId, snapshotFile, extracted.classCount(), extracted.edgeCount());
  }

  static String sha256(Path file) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream in = Files.newInputStream(file)) {
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) {
          digest.update(buf, 0, n);
        }
      }
      StringBuilder sb = new StringBuilder();
      for (byte b : digest.digest()) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /**
   * Usage: IngestJob &lt;catalogPath&gt; &lt;snapshotDir&gt; &lt;acronym&gt; [acronym...]
   * The BioPortal API key is read from the {@code BIOPORTAL_API_KEY} environment variable.
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("Usage: IngestJob <catalogPath> <snapshotDir> <acronym> [acronym...]");
      System.exit(2);
    }
    String apiKey = System.getenv("BIOPORTAL_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      System.err.println("BIOPORTAL_API_KEY environment variable is not set");
      System.exit(2);
    }
    Path catalogPath = Path.of(args[0]);
    Path snapshotDir = Path.of(args[1]);
    Files.createDirectories(snapshotDir);

    IngestJob job = new IngestJob(new BioPortalDownloader(apiKey), new OwlHierarchyExtractor());
    try (CatalogStore catalog = CatalogStore.openFile(catalogPath.toString())) {
      catalog.initSchema();
      for (int i = 2; i < args.length; i++) {
        String acronym = args[i];
        IngestResult r = job.ingestLatest(catalog, acronym, snapshotDir);
        System.out.printf("%s: version %s, %d classes, %d edges -> %s%n",
            acronym, r.versionId(), r.classCount(), r.edgeCount(), r.snapshotFile());
      }
    }
  }
}
