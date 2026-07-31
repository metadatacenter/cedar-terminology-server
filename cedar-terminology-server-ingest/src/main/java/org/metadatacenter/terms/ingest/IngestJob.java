package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    Optional<Set<IRI>> owlHierarchyProperties = HierarchyConfigs.owlHierarchyProperties(acronym);
    if (owlHierarchyProperties.isPresent()) {
      // A partonomy ontology (e.g. BTO): subsumption plus the configured relation restrictions.
      return new OwlHierarchyExtractor(owlHierarchyProperties.get());
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
   * Ingests the latest submission of a BioPortal value-set collection and points {@code latest} at
   * it. A collection is an ontology of type {@code VALUE_SET_COLLECTION} in BioPortal — fetched,
   * downloaded, hashed, and snapshotted through the exact same content-hash mechanism as an ontology
   * ({@link #ingestSubmission}); the only difference is that the catalog row is marked
   * {@link CatalogStore#KIND_VALUE_SET_COLLECTION} so its version resolves separately. This is what
   * lets a value-set-valued constraint be frozen on publish.
   */
  public IngestResult ingestValueSetCollectionLatest(CatalogStore catalog, String acronym, Path snapshotDir)
      throws IOException, InterruptedException, SQLException {
    IngestResult r = ingestLatest(catalog, acronym, snapshotDir);
    catalog.setOntologyKind(acronym, CatalogStore.KIND_VALUE_SET_COLLECTION);
    return r;
  }

  /**
   * Ingests every submission (version) of a value-set collection and marks the collection's row as
   * {@link CatalogStore#KIND_VALUE_SET_COLLECTION}. The value-set-collection analogue of
   * {@link #ingestAll}, giving the collection real version history to resolve and diff.
   */
  public List<IngestResult> ingestAllValueSetCollection(CatalogStore catalog, String acronym, Path snapshotDir)
      throws IOException, InterruptedException, SQLException {
    List<IngestResult> results = ingestAll(catalog, acronym, snapshotDir);
    if (!results.isEmpty()) {
      catalog.setOntologyKind(acronym, CatalogStore.KIND_VALUE_SET_COLLECTION);
    }
    return results;
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
    String rawHash = sha256(raw);        // hash of the archival download, kept as file_hash provenance
    Path loadable = decompress(raw);     // .zip/.gz submissions must be expanded before parsing
    loadable = stripOboImports(loadable);// OBO import: declarations must be dropped before parsing

    // Extract into a temp file and only replace the live snapshot once extraction succeeds with a
    // non-empty result. Extracting in place (delete-then-write) would lose a good snapshot whenever
    // extraction fails or yields nothing -- as happened when a classpath gap made OWLAPI's import
    // resolution throw NoClassDefFoundError (an Error, missed by catch(Exception)), leaving an empty
    // file with the previous good data already deleted. Catch Throwable so such an Error becomes a
    // skippable failure rather than clobbering data or aborting a batch.
    //
    // The version id is the normalized content hash (VERSIONING-DESIGN §4.3), so it can only be
    // computed after extraction. The temp file is therefore named by the raw hash (name-independent
    // of identity); the final file is named by the content-hash version id.
    Path tempFile = ontoDir.resolve(rawHash + ".sqlite.tmp");
    Files.deleteIfExists(tempFile);
    HierarchyExtractor.Result extracted;
    String versionId;
    try (SnapshotStore store = SnapshotStore.openFile(tempFile.toString())) {
      store.initSchema();
      extracted = extractorFor(acronym, sub.format()).extractFromFile(loadable.toFile(), store);
      // Drop dead-end import references from the roots: unlabeled foreign classes with no labeled
      // descendant are unresolved-owl:imports dangling references, not real tree entry points.
      store.pruneDeadEndImportRoots(acronym);
      // Then give any still-unlabeled class a fallback label from its IRI fragment (matching
      // BioPortal), so label-less ontologies are searchable/browsable rather than blank. After the
      // prune, which keys on the genuinely-unlabeled state.
      store.fillMissingLabelsFromIri();
      // Identity = the normalized served model, independent of the source bytes/serialization. Two
      // uploads that extract to the same hierarchy share a version id and merge to one snapshot.
      versionId = store.normalizedContentHash(true);
    } catch (Throwable e) {
      Files.deleteIfExists(tempFile);
      throw new IOException("Extraction failed for " + acronym + " submission " + sub.submissionId(), e);
    }
    if (extracted.classCount() == 0) {
      Files.deleteIfExists(tempFile);
      throw new IOException("Extraction produced 0 classes for " + acronym + " submission "
          + sub.submissionId() + "; refusing to overwrite the existing snapshot with an empty one");
    }
    Path snapshotFile = ontoDir.resolve(versionId + ".sqlite");
    Files.move(tempFile, snapshotFile, StandardCopyOption.REPLACE_EXISTING);

    catalog.upsertOntology(new CatalogStore.OntologyInfo(acronym, acronym, null, sub.format()));
    catalog.addSnapshot(new CatalogStore.SnapshotInfo(
        versionId, acronym, sub.version(), sub.released(), Instant.now().toString(),
        sub.format(), "subsumption", extracted.classCount(), extracted.edgeCount(),
        catalogRelativePath(catalog, snapshotFile), rawHash,
        access.viewingRestriction() == null ? "public" : access.viewingRestriction()));
    // Display/audit-only provenance: BioPortal's reliable per-upload submission id (in hand here,
    // unreconstructable offline later) and the version string's self-claimed date.
    catalog.setSnapshotProvenance(versionId, acronym, sub.submissionId(),
        CatalogStore.SnapshotProvenance.sourceDateFromDeclaredVersion(sub.version()));
    if (setAsLatest) {
      catalog.setTag(acronym, CatalogStore.TAG_LATEST, versionId);
    }

    log.info("Ingested {} submission {} -> {} ({} classes, {} edges)",
        acronym, sub.submissionId(), versionId, extracted.classCount(), extracted.edgeCount());
    return new IngestResult(sub.submissionId(), versionId, snapshotFile, extracted.classCount(), extracted.edgeCount());
  }

  /**
   * The path to record for a snapshot: relative to the catalog's directory when possible, so the
   * store (catalog + snapshots) can be copied anywhere and served without rewriting paths. Uses
   * forward slashes so the stored value is portable across operating systems. Falls back to the
   * absolute path for an in-memory catalog (no base directory to relativize against).
   */
  private static String catalogRelativePath(CatalogStore catalog, Path snapshotFile) {
    Path abs = snapshotFile.toAbsolutePath().normalize();
    return catalog.baseDir()
        .map(base -> base.toAbsolutePath().normalize().relativize(abs).toString()
            .replace(java.io.File.separatorChar, '/'))
        .orElse(abs.toString());
  }

  private static void setLatestQuietly(CatalogStore catalog, String acronym, String versionId) {
    try {
      catalog.setTag(acronym, CatalogStore.TAG_LATEST, versionId);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to set latest tag for " + acronym, e);
    }
  }

  /**
   * BioPortal sometimes serves a submission as a {@code .gz} or {@code .zip} archive. OWLAPI does
   * not expand those (it feeds the archive bytes to the XML parser and fails with "Content is not
   * allowed in prolog"). Detect the archive by its magic bytes and expand the ontology to a sibling
   * file, which is what the extractor parses. For a multi-entry zip the largest entry is taken (the
   * ontology, not a catalog/readme). Returns the input unchanged when it is not compressed.
   */
  static Path decompress(Path raw) throws IOException {
    byte[] magic = new byte[4];
    try (InputStream in = Files.newInputStream(raw)) { in.read(magic); }
    boolean gz = (magic[0] & 0xff) == 0x1f && (magic[1] & 0xff) == 0x8b;
    boolean zip = magic[0] == 'P' && magic[1] == 'K' && magic[2] == 3 && magic[3] == 4;
    if (!gz && !zip) return raw;
    Path out = raw.resolveSibling(raw.getFileName() + ".expanded");
    if (gz) {
      try (InputStream in = new GZIPInputStream(Files.newInputStream(raw))) {
        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
      }
      return out;
    }
    Path largest = null;
    long largestSize = -1;
    try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(raw))) {
      ZipEntry e;
      int i = 0;
      while ((e = zin.getNextEntry()) != null) {
        if (e.isDirectory()) continue;
        Path cand = raw.resolveSibling(raw.getFileName() + ".entry" + (i++));
        Files.copy(zin, cand, StandardCopyOption.REPLACE_EXISTING);
        long sz = Files.size(cand);
        if (sz > largestSize) {
          largestSize = sz;
          if (largest != null) Files.deleteIfExists(largest);
          largest = cand;
        } else {
          Files.deleteIfExists(cand);
        }
      }
    }
    if (largest == null) throw new IOException("empty zip archive: " + raw);
    Files.move(largest, out, StandardCopyOption.REPLACE_EXISTING);
    return out;
  }

  /**
   * Removes {@code import:} declarations from an OBO file before it is parsed. OWLAPI's OBO→OWL
   * converter ({@code OWLAPIObo2Owl}) resolves each {@code import:} over the network with a
   * hardcoded default loader configuration, so the {@link org.semanticweb.owlapi.model.MissingImportHandlingStrategy#SILENT}
   * the extractors set on their own load is ignored: an unreachable import (e.g. PECO's
   * {@code exposure-envo_pattern.owl}, a GitHub 404) aborts the whole load with an
   * {@code UnloadableImportException}. Stripping the declarations is the OBO equivalent of the
   * SILENT policy the RDF/XML path already applies — each ontology's own asserted hierarchy is
   * extracted; imported reference ontologies (ChEBI, ENVO, …) are out of scope and would otherwise
   * flood the snapshot with foreign classes. IRIs referenced as parents survive as bare, parentless
   * concept endpoints, exactly as an unresolved import leaves them on the OWL path.
   *
   * Only OBO input (identified by a {@code format-version:} header) that actually carries
   * {@code import:} lines is rewritten, to a sibling {@code .noimports} file; anything else is
   * returned unchanged.
   */
  static Path stripOboImports(Path loadable) throws IOException {
    List<String> lines = Files.readAllLines(loadable);
    boolean isObo = false;
    boolean hasImport = false;
    for (String line : lines) {
      if (line.startsWith("format-version:")) isObo = true;
      else if (line.startsWith("import:")) hasImport = true;
      if (isObo && hasImport) break;
    }
    if (!isObo || !hasImport) return loadable;
    List<String> kept = new ArrayList<>(lines.size());
    for (String line : lines) {
      if (!line.startsWith("import:")) kept.add(line);
    }
    Path out = loadable.resolveSibling(loadable.getFileName() + ".noimports");
    Files.write(out, kept);
    return out;
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
   * Usage: IngestJob &lt;catalogPath&gt; &lt;snapshotDir&gt; [--all] [--valuesets] &lt;acronym&gt; [acronym...]
   * With {@code --all}, every submission (version) of each ontology is ingested; otherwise only the
   * latest. With {@code --valuesets}, the listed acronyms are ingested as value-set collections
   * (same content-hash mechanism, marked {@code value_set_collection} in the catalog) rather than
   * ontologies. The BioPortal API key is read from the {@code BIOPORTAL_API_KEY} environment variable.
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("Usage: IngestJob <catalogPath> <snapshotDir> [--all] [--valuesets] <acronym> [acronym...]");
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
    boolean valuesets = false;
    List<Integer> submissionIds = new ArrayList<>();
    List<String> acronyms = new ArrayList<>();
    for (int i = 2; i < args.length; i++) {
      if ("--all".equals(args[i])) {
        all = true;
      } else if ("--valuesets".equals(args[i])) {
        valuesets = true;
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
          if (valuesets) {
            catalog.setOntologyKind(acronym, CatalogStore.KIND_VALUE_SET_COLLECTION);
          }
        } else if (all) {
          List<IngestResult> rs = valuesets
              ? job.ingestAllValueSetCollection(catalog, acronym, snapshotDir)
              : job.ingestAll(catalog, acronym, snapshotDir);
          System.out.printf("%s%s: ingested %d versions (latest -> %s)%n",
              valuesets ? "[value-set collection] " : "", acronym, rs.size(),
              rs.stream().max(Comparator.comparingInt(IngestResult::submissionId))
                  .map(IngestResult::versionId).orElse("none"));
        } else {
          IngestResult r = valuesets
              ? job.ingestValueSetCollectionLatest(catalog, acronym, snapshotDir)
              : job.ingestLatest(catalog, acronym, snapshotDir);
          System.out.printf("%s%s: version %s, %d classes, %d edges -> %s%n",
              valuesets ? "[value-set collection] " : "", acronym, r.versionId(), r.classCount(),
              r.edgeCount(), r.snapshotFile());
        }
      }
    }
  }
}
