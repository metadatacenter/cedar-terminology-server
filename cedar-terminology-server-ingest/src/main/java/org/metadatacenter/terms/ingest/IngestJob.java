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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
  public record IngestResult(int submissionId, String versionId, Path snapshotFile, int classCount, int edgeCount) {}

  private final SubmissionSource source;
  private final OwlHierarchyExtractor owlExtractor = new OwlHierarchyExtractor();
  private final SkosHierarchyExtractor skosExtractor = new SkosHierarchyExtractor();

  public IngestJob(SubmissionSource source) {
    this.source = source;
  }

  /**
   * Selects the extractor for an ontology: a per-ontology hierarchy override if one is registered
   * (e.g. RxNorm's isa backbone), otherwise the format default (SKOS relations vs OWL/OBO subClassOf).
   */
  private HierarchyExtractor extractorFor(String acronym, String format) {
    Optional<HierarchyConfig> override = HierarchyConfigs.forOntology(acronym);
    if (override.isPresent()) {
      return new RelationHierarchyExtractor(override.get());
    }
    return "SKOS".equalsIgnoreCase(format) ? skosExtractor : owlExtractor;
  }

  /**
   * Ingests the latest submission of an ontology and points {@code latest} at it.
   */
  public IngestResult ingestLatest(CatalogStore catalog, String acronym, Path snapshotDir)
      throws IOException, InterruptedException, SQLException {
    return ingestSubmission(catalog, acronym, source.latestSubmission(acronym), snapshotDir, true);
  }

  /**
   * Ingests every submission (version) of an ontology as its own snapshot, then points
   * {@code latest} at the newest (highest submission id). Older versions remain resolvable by their
   * content-hash version id. Submissions that fail to download or extract are logged and skipped so
   * one bad historical submission does not abort the whole backfill.
   */
  public List<IngestResult> ingestAll(CatalogStore catalog, String acronym, Path snapshotDir)
      throws IOException, InterruptedException, SQLException {
    List<Submission> submissions = new ArrayList<>(source.listSubmissions(acronym));
    submissions.sort(Comparator.comparingInt(Submission::submissionId));
    List<IngestResult> results = new ArrayList<>();
    for (Submission sub : submissions) {
      try {
        results.add(ingestSubmission(catalog, acronym, sub, snapshotDir, false));
      } catch (Exception e) {
        log.warn("Skipping {} submission {} (version {}): {}",
            acronym, sub.submissionId(), sub.version(), e.getMessage());
      }
    }
    if (!submissions.isEmpty()) {
      Submission newest = submissions.get(submissions.size() - 1);
      results.stream()
          .filter(r -> r.submissionId() == newest.submissionId())
          .findFirst()
          .ifPresent(r -> setLatestQuietly(catalog, acronym, r.versionId()));
    }
    return results;
  }

  /**
   * Ingests one submission into a new snapshot under {@code snapshotDir}, registering it in the
   * catalog and optionally pointing {@code latest} at it. OWL/OBO submissions are extracted via
   * OWLAPI; the hierarchy status is recorded as {@code subsumption}.
   */
  public IngestResult ingestSubmission(CatalogStore catalog, String acronym, Submission sub,
                                       Path snapshotDir, boolean setAsLatest)
      throws IOException, InterruptedException, SQLException {
    log.info("Ingesting {} submission {} (version {}, format {})",
        acronym, sub.submissionId(), sub.version(), sub.format());

    // Licensing guard: never download or ingest content BioPortal marks as restricted/licensed.
    OntologyAccess access = source.accessInfo(acronym);
    if (!access.isPublic()) {
      throw new IOException("Refusing to ingest restricted ontology " + acronym
          + " (viewingRestriction=" + access.viewingRestriction() + "); licensed content is not ingested");
    }

    Path ontoDir = snapshotDir.resolve(acronym);
    Path raw = source.download(acronym, sub.submissionId(), ontoDir.resolve("raw"));
    String versionId = sha256(raw);

    Path snapshotFile = ontoDir.resolve(versionId + ".sqlite");
    Files.deleteIfExists(snapshotFile);
    HierarchyExtractor.Result extracted;
    try (SnapshotStore store = SnapshotStore.openFile(snapshotFile.toString())) {
      store.initSchema();
      extracted = extractorFor(acronym, sub.format()).extractFromFile(raw.toFile(), store);
    } catch (Exception e) {
      throw new IOException("Extraction failed for " + acronym + " submission " + sub.submissionId(), e);
    }

    catalog.upsertOntology(new CatalogStore.OntologyInfo(acronym, acronym, null, sub.format()));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo(
        versionId, acronym, sub.version(), sub.released(), Instant.now().toString(),
        sub.format(), "subsumption", extracted.classCount(), extracted.edgeCount(),
        snapshotFile.toString(), versionId,
        access.viewingRestriction() == null ? "public" : access.viewingRestriction()));
    if (setAsLatest) {
      catalog.setTag(acronym, CatalogStore.TAG_LATEST, versionId);
    }

    log.info("Ingested {} submission {} -> {} ({} classes, {} edges)",
        acronym, sub.submissionId(), versionId, extracted.classCount(), extracted.edgeCount());
    return new IngestResult(sub.submissionId(), versionId, snapshotFile, extracted.classCount(), extracted.edgeCount());
  }

  private static void setLatestQuietly(CatalogStore catalog, String acronym, String versionId) {
    try {
      catalog.setTag(acronym, CatalogStore.TAG_LATEST, versionId);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to set latest tag for " + acronym, e);
    }
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
   * Usage: IngestJob &lt;catalogPath&gt; &lt;snapshotDir&gt; [--all] &lt;acronym&gt; [acronym...]
   * With {@code --all}, every submission (version) of each ontology is ingested; otherwise only the
   * latest. The BioPortal API key is read from the {@code BIOPORTAL_API_KEY} environment variable.
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("Usage: IngestJob <catalogPath> <snapshotDir> [--all] <acronym> [acronym...]");
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

    boolean all = false;
    List<Integer> submissionIds = new ArrayList<>();
    List<String> acronyms = new ArrayList<>();
    for (int i = 2; i < args.length; i++) {
      if ("--all".equals(args[i])) {
        all = true;
      } else if ("--submission".equals(args[i]) && i + 1 < args.length) {
        submissionIds.add(Integer.parseInt(args[++i]));
      } else {
        acronyms.add(args[i]);
      }
    }

    BioPortalDownloader downloader = new BioPortalDownloader(apiKey);
    IngestJob job = new IngestJob(downloader);
    try (CatalogStore catalog = CatalogStore.openFile(catalogPath.toString())) {
      catalog.initSchema();
      for (String acronym : acronyms) {
        if (!submissionIds.isEmpty()) {
          // Ingest specific historical submissions without moving the latest tag.
          List<Submission> subs = downloader.listSubmissions(acronym);
          for (int id : submissionIds) {
            Submission sub = subs.stream().filter(s -> s.submissionId() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No submission " + id + " for " + acronym));
            IngestResult r = job.ingestSubmission(catalog, acronym, sub, snapshotDir, false);
            System.out.printf("%s submission %d (%s): version %s, %d classes, %d edges%n",
                acronym, id, sub.format(), r.versionId(), r.classCount(), r.edgeCount());
          }
        } else if (all) {
          List<IngestResult> rs = job.ingestAll(catalog, acronym, snapshotDir);
          System.out.printf("%s: ingested %d versions (latest -> %s)%n",
              acronym, rs.size(),
              rs.stream().max(Comparator.comparingInt(IngestResult::submissionId))
                  .map(IngestResult::versionId).orElse("none"));
        } else {
          IngestResult r = job.ingestLatest(catalog, acronym, snapshotDir);
          System.out.printf("%s: version %s, %d classes, %d edges -> %s%n",
              acronym, r.versionId(), r.classCount(), r.edgeCount(), r.snapshotFile());
        }
      }
    }
  }
}
