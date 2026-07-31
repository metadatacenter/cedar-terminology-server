package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotDiff;
import org.metadatacenter.terms.store.SnapshotStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Proves (or refutes) that a snapshot's identity is source-independent: it ingests the same ontology
 * release from two different authorities — BioPortal and OBO Foundry — and compares the resulting
 * content-hash {@code version_id}. Identity is the normalized hash of the extracted model
 * (VERSIONING-DESIGN §4.3), so the same release drawn from a different distributor, in a different
 * serialization, must produce the same id. This is the concrete test of that claim (roadmap D2).
 *
 * <p>It ingests BioPortal's latest submission first, reads the release date from it, then pulls that
 * exact dated release from OBO Foundry's versioned PURL so the two ingests describe the same logical
 * version. When the ids match, identity survived the change of source and serialization. When they
 * differ, it runs a diff so the difference is characterized rather than merely reported — a real
 * outcome too (BioPortal may hold a re-processed or differently-dated build).
 *
 * <p>Usage: {@code CrossSourceIdentityCheck <acronym> [releaseDate]}. With an explicit release date
 * (e.g. {@code 2024-05-29}) both sides target that release; otherwise BioPortal's latest sets it.
 * Reads {@code BIOPORTAL_API_KEY}. Ingests into throwaway temp catalogs; nothing touches a real store.
 */
public class CrossSourceIdentityCheck {

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: CrossSourceIdentityCheck <acronym> [releaseDate]");
      System.exit(2);
    }
    String acronym = args[0];
    String requestedDate = args.length > 1 ? args[1] : null;

    String apiKey = System.getenv("BIOPORTAL_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      System.err.println("BIOPORTAL_API_KEY environment variable is not set");
      System.exit(2);
    }

    Path work = Files.createTempDirectory("xsource-" + acronym.toLowerCase());
    System.out.println("Cross-source identity check for " + acronym
        + (requestedDate == null ? " (BioPortal latest)" : " release " + requestedDate));
    System.out.println("Work dir: " + work + "\n");

    // ---- BioPortal side -----------------------------------------------------------------------
    BioPortalDownloader bp = new BioPortalDownloader(apiKey);
    Submission bpSub = pickSubmission(bp, acronym, requestedDate);
    String releaseDate = normalizeDate(bpSub.version(), bpSub.released());
    System.out.printf("BioPortal: submission %d, version '%s', released '%s' -> release date %s%n",
        bpSub.submissionId(), bpSub.version(), bpSub.released(), releaseDate);
    IngestJob.IngestResult bpResult =
        ingest(bp, acronym, bpSub, work.resolve("bioportal"));
    System.out.printf("  version_id %s  (%d classes, %d edges)%n%n",
        bpResult.versionId(), bpResult.classCount(), bpResult.edgeCount());

    // ---- OBO Foundry side: the same dated release, a different authority ------------------------
    String oboDate = requestedDate != null ? requestedDate : releaseDate;
    OboFoundrySubmissionSource obo = new OboFoundrySubmissionSource(oboDate);
    System.out.println("OBO Foundry: " + obo.downloadUrl(acronym));
    IngestJob.IngestResult oboResult;
    try {
      Submission oboSub = obo.latestSubmission(acronym);
      oboResult = ingest(obo, acronym, oboSub, work.resolve("obofoundry"));
    } catch (Exception dated) {
      System.out.println("  dated release unavailable (" + dated.getMessage()
          + "); retrying OBO Foundry current release");
      OboFoundrySubmissionSource current = new OboFoundrySubmissionSource();
      System.out.println("OBO Foundry: " + current.downloadUrl(acronym));
      oboResult = ingest(current, acronym, current.latestSubmission(acronym), work.resolve("obofoundry"));
      oboDate = "current";
    }
    System.out.printf("  version_id %s  (%d classes, %d edges)%n%n",
        oboResult.versionId(), oboResult.classCount(), oboResult.edgeCount());

    // ---- Verdict -------------------------------------------------------------------------------
    boolean match = bpResult.versionId().equals(oboResult.versionId());
    System.out.println("========================================================================");
    if (match) {
      System.out.println("MATCH: identical content-hash version_id from BioPortal and OBO Foundry.");
      System.out.println("Identity is source-independent for " + acronym + " (release " + releaseDate + ").");
    } else {
      System.out.println("DIFFER: the two authorities produced different version_ids.");
      System.out.println("  BioPortal (" + releaseDate + "): " + bpResult.versionId());
      System.out.println("  OBO Foundry (" + oboDate + "): " + oboResult.versionId());
      System.out.println("Characterizing the difference (BioPortal -> OBO Foundry):");
      characterize(bpResult.snapshotFile(), oboResult.snapshotFile());
    }
    System.out.println("========================================================================");
    System.exit(match ? 0 : 1);
  }

  /** BioPortal's latest submission, or the one whose version/released names the requested date. */
  private static Submission pickSubmission(BioPortalDownloader bp, String acronym, String requestedDate)
      throws Exception {
    if (requestedDate == null) {
      return bp.latestSubmission(acronym);
    }
    List<Submission> subs = bp.listSubmissions(acronym);
    return subs.stream()
        .filter(s -> (s.version() != null && s.version().contains(requestedDate))
            || (s.released() != null && s.released().startsWith(requestedDate)))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "BioPortal has no " + acronym + " submission for release " + requestedDate));
  }

  private static IngestJob.IngestResult ingest(SubmissionSource source, String acronym, Submission sub, Path dir)
      throws Exception {
    Files.createDirectories(dir);
    try (CatalogStore catalog = CatalogStore.openFile(dir.resolve("catalog.sqlite").toString())) {
      catalog.initSchema();
      return new IngestJob(source).ingestSubmission(catalog, acronym, sub, dir, true);
    }
  }

  /** A release date as OBO Foundry names its release directories: the declared version if it is a
   *  bare date, else the calendar day of the released timestamp. */
  private static String normalizeDate(String version, String released) {
    if (version != null && version.matches("\\d{4}-\\d{2}-\\d{2}")) {
      return version;
    }
    if (released != null && released.length() >= 10) {
      return released.substring(0, 10);
    }
    return version != null ? version : released;
  }

  /** Diff the two snapshots both ways so a mismatch is described, not just flagged. */
  private static void characterize(Path fromFile, Path toFile) throws Exception {
    try (SnapshotStore from = SnapshotStore.openFile(fromFile.toString());
         SnapshotStore to = SnapshotStore.openFile(toFile.toString())) {
      SnapshotDiff.Diff d = new SnapshotDiff().diff(from, to);
      System.out.println("  " + d.summary());
      printSample("  only in BioPortal", d.removedConcepts());
      printSample("  only in OBO Foundry", d.addedConcepts());
    }
  }

  private static void printSample(String label, List<String> concepts) {
    if (concepts.isEmpty()) {
      return;
    }
    System.out.println(label + " (" + concepts.size() + "): "
        + concepts.stream().limit(8).toList());
  }
}
