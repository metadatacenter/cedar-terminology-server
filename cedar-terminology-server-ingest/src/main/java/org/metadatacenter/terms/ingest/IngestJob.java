package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.OntologyIri;
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

    Path ontoDir = snapshotDir.resolve(acronym);
    Prepared prep = prepareSubmission(acronym, sub, ontoDir); // licensing guard + download + decompress + strip
    OntologyAccess access = prep.access();
    String rawHash = prep.rawHash();     // hash of the archival download, kept as file_hash provenance
    // The ontology's self-declared owl:Ontology IRI, read cheaply from the file head. A fallback
    // identity for when the class namespace is a file/host base or a merely-imported namespace that
    // de-confliction declines (VERSIONING item 6).
    String headerIri = OntologyHeaderIri.fromFile(prep.loadable()).orElse(null);

    // Extract into a temp file and only replace the live snapshot once extraction succeeds with a
    // non-empty result. Extracting in place (delete-then-write) would lose a good snapshot whenever
    // extraction fails or yields nothing -- as happened when a classpath gap made OWLAPI's import
    // resolution throw NoClassDefFoundError (an Error, missed by catch(Exception)), leaving an empty
    // file with the previous good data already deleted. Catch Throwable so such an Error becomes a
    // skippable failure rather than clobbering data or aborting a batch.
    //
    // The version id is the normalized content hash (VERSIONING-ROADMAP "The Model" §4.3), so it can only be
    // computed after extraction. The temp file is therefore named by the raw hash (name-independent
    // of identity); the final file is named by the content-hash version id.
    Path tempFile = ontoDir.resolve(rawHash + ".sqlite.tmp");
    Files.deleteIfExists(tempFile);
    HierarchyExtractor.Result extracted;
    String versionId;
    String ownNamespace; // this snapshot's dominant own ID-space, for the canonical iri
    try (SnapshotStore store = SnapshotStore.openFile(tempFile.toString())) {
      store.initSchema();
      Extraction ex = extractInto(store, acronym, sub, prep.loadable());
      if (headerIri != null) {
        store.setMeta("ontology_iri", headerIri); // record the declared IRI in the snapshot
      }
      extracted = ex.result();
      versionId = ex.versionId();
      ownNamespace = ex.ownNamespace();
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

    // Register the snapshot and reconcile its canonical identity atomically. These steps are all
    // idempotent upserts, so re-running the ingest after a crash heals any partial state; the
    // transaction additionally ensures a concurrent reader never sees a half-registered snapshot (a
    // snapshot row without its latest tag, or a served snapshot with no canonical iri). The snapshot
    // file was moved into place first — it is content-addressed by version id — so a rollback leaves
    // only an unreferenced orphan file that the next ingest overwrites in place.
    String ownNamespaceFinal = ownNamespace; // effectively-final copy for the transaction lambda
    // The source's human-readable title becomes the catalog display name (shown in the ontology
    // picker). BioPortal supplies one in its submission metadata; a direct-URL/OBO source does not, so
    // fall back to the title the ontology declares in its own owl:Ontology header, and only then to the
    // acronym. This keeps a URL-sourced ontology's real name across re-ingests instead of resetting it.
    String displayName = access.name() != null && !access.name().isBlank() ? access.name()
        : OntologyHeaderTitle.fromFile(prep.loadable()).filter(t -> !t.isBlank()).orElse(acronym);
    catalog.inTransaction(() -> {
      catalog.upsertOntology(new CatalogStore.OntologyInfo(acronym, displayName, null, sub.format()));
      catalog.addSnapshot(new CatalogStore.SnapshotInfo(
          versionId, acronym, sub.version(), sub.released(), Instant.now().toString(),
          sub.format(), "subsumption", extracted.classCount(), extracted.edgeCount(),
          catalogRelativePath(catalog, snapshotFile), rawHash,
          access.viewingRestriction() == null ? "public" : access.viewingRestriction()));
      // Display/audit-only provenance: BioPortal's reliable per-upload submission id (in hand here,
      // unreconstructable offline later) and the version string's self-claimed date.
      catalog.setSnapshotProvenance(versionId, acronym, sub.submissionId(),
          CatalogStore.SnapshotProvenance.sourceDateFromDeclaredVersion(sub.version()));
      // Record the backend the bytes came from (default bioportal). Identity is unaffected — the same
      // release from a different authority resolves to the same content-hash version_id and merges here.
      catalog.setSnapshotBackend(versionId, acronym, source.backendId());
      if (setAsLatest) {
        catalog.setTag(acronym, CatalogStore.TAG_LATEST, versionId);
      }
      // Derive and store the ontology's canonical iri (VERSIONING-ROADMAP "The Model" §6.4) at ingest — its
      // content-derived, source-independent cross-source identity — rather than leaving it to the
      // derivation backfill, so a fresh ingest is iri-identified immediately and two sources of one
      // ontology can be joined by iri. Set on the first ingest and whenever this is the latest; the own
      // namespace is stable across versions, so re-setting is idempotent. Runs after the latest tag so
      // the de-confliction below sees this ontology's own content.
      if (ownNamespaceFinal != null && (setAsLatest || catalog.ontologyIri(acronym).isEmpty())) {
        String canonicalIri = OntologyIri.canonical(ownNamespaceFinal);
        catalog.setOntologyIri(acronym, canonicalIri, ownNamespaceFinal);
        // Enforce the identity invariant: a placeholder/host base or a merely-imported namespace is
        // shared with a content-distinct ontology. Decline it here (and any importer this ingest
        // displaces), keeping the iri only for its true OBO owner; then reap any orphaned identity row.
        IriDeconfliction.reconcile(catalog, canonicalIri);
        catalog.pruneOrphanIdentities();
      }
      // Header-IRI fallback (item 6): if the class namespace produced no identity, or de-confliction
      // just declined it (a file/host base, or a namespace this ontology only imports), fall back to the
      // ontology's own owl:Ontology IRI. Guarded on the acronym still being identity-less, so a clean
      // class-derived, source-independent iri always wins. De-conflict it too — a header IRI shared by
      // two content-distinct ontologies is still not a merge.
      if (headerIri != null && catalog.ontologyIri(acronym).isEmpty()) {
        String headerCanonical = OntologyIri.canonical(headerIri);
        catalog.setOntologyIri(acronym, headerCanonical, headerIri);
        IriDeconfliction.reconcile(catalog, headerCanonical);
        catalog.pruneOrphanIdentities();
      }
    });

    log.info("Ingested {} submission {} -> {} ({} classes, {} edges)",
        acronym, sub.submissionId(), versionId, extracted.classCount(), extracted.edgeCount());
    return new IngestResult(sub.submissionId(), versionId, snapshotFile, extracted.classCount(), extracted.edgeCount());
  }

  /** A downloaded, parse-ready submission: the loadable file, its raw-bytes hash, and its access info. */
  private record Prepared(Path loadable, String rawHash, OntologyAccess access) {}

  /** The result of extracting a submission into a store: the counts, the content-hash version id, and
   *  the ontology's dominant own ID-space (for the canonical iri). */
  private record Extraction(HierarchyExtractor.Result result, String versionId, String ownNamespace) {}

  /**
   * Applies the licensing guard, downloads the submission, and expands/normalizes it to a parse-ready
   * file (decompress {@code .gz}/{@code .zip}, strip OBO {@code import:} lines). Shared by
   * {@link #ingestSubmission} and {@link #backfillLabels} so both feed the extractor identical bytes.
   */
  private Prepared prepareSubmission(String acronym, Submission sub, Path ontoDir)
      throws IOException, InterruptedException {
    // Licensing guard: never download or ingest content BioPortal marks as restricted/licensed.
    OntologyAccess access = source.accessInfo(acronym);
    if (!access.isPublic()) {
      throw new IOException("Refusing to ingest restricted ontology " + acronym
          + " (viewingRestriction=" + access.viewingRestriction() + "); licensed content is not ingested");
    }
    Path raw = source.download(acronym, sub.submissionId(), ontoDir.resolve("raw"));
    String rawHash = sha256(raw);
    Path loadable = decompress(raw);      // .zip/.gz submissions must be expanded before parsing
    loadable = stripOboImports(loadable); // OBO import: declarations must be dropped before parsing
    return new Prepared(loadable, rawHash, access);
  }

  /**
   * Extracts a prepared submission into {@code store} with the exact post-processing ingest applies —
   * prune dead-end import roots, then IRI-fragment fallback labels — and returns the counts, the
   * content-hash version id, and the own ID-space. Shared so {@link #backfillLabels} reproduces a
   * snapshot's identity byte-for-byte; the label backfill gates on the returned version id matching.
   */
  private Extraction extractInto(SnapshotStore store, String acronym, Submission sub, Path loadable)
      throws Exception {
    HierarchyExtractor.Result extracted =
        extractorFor(acronym, sub.format()).extractFromFile(loadable.toFile(), store);
    // Drop dead-end import references from the roots: unlabeled foreign classes with no labeled
    // descendant are unresolved-owl:imports dangling references, not real tree entry points.
    store.pruneDeadEndImportRoots(acronym);
    // Then give any still-unlabeled class a fallback label from its IRI fragment (matching BioPortal),
    // so label-less ontologies are searchable/browsable rather than blank. After the prune, which keys
    // on the genuinely-unlabeled state.
    store.fillMissingLabelsFromIri();
    // Identity = the normalized served model, independent of the source bytes/serialization.
    String versionId = store.normalizedContentHash(true);
    String ownNamespace = store.dominantOwnIdspace(acronym).orElse(null);
    return new Extraction(extracted, versionId, ownNamespace);
  }

  /** Tally of a label backfill run. */
  public record BackfillSummary(int filled, int alreadyLabeled, int otherBackend, int noSubmission,
                                int mismatched, int failed, long labelsAdded) {}

  /**
   * Backfills the multilingual {@code label} table of already-ingested snapshots that predate label
   * capture. For each snapshot served from this run's source (matched by backend), re-fetches the exact
   * submission, re-extracts it into a throwaway store, and — only if the recomputed content-hash
   * version id equals the snapshot's — copies the captured labels into the existing snapshot file. The
   * version-id gate makes it fail-safe: a snapshot is enriched in place with identity-preserving labels,
   * or skipped, never rewritten with different content. Snapshots that already carry labels are skipped,
   * so the run is resumable and idempotent. {@code only} limits the run to the given acronyms (empty =
   * all). The catalog is never mutated.
   */
  private static final String BACKFILLED_MARKER = "labels_backfilled";

  public BackfillSummary backfillLabels(CatalogStore catalog, Path snapshotDir, Set<String> only)
      throws SQLException {
    int filled = 0, already = 0, otherBackend = 0, noSub = 0, mismatched = 0, failed = 0;
    long labelsAdded = 0;
    String myBackend = source.backendId();
    List<String> acronyms = new ArrayList<>();
    for (CatalogStore.OntologyInfo o : catalog.listOntologies()) {
      if (only.isEmpty() || only.contains(o.acronym())) {
        acronyms.add(o.acronym());
      }
    }
    java.util.Optional<Path> base = catalog.baseDir();
    int idx = 0;
    for (String acronym : acronyms) {
      idx++;
      for (CatalogStore.SnapshotInfo snap : catalog.listSnapshots(acronym)) {
        Path file = base.map(b -> b.resolve(snap.filePath())).orElse(Path.of(snap.filePath()));
        try {
          // Skip snapshots already processed (resumable/idempotent): either they carry captured labels,
          // or a prior backfill marked them done (a label-less ontology legitimately has zero labels).
          // initSchema migrates a pre-label snapshot to the empty label/meta tables.
          try (SnapshotStore existing = SnapshotStore.openFile(file.toString())) {
            existing.initSchema();
            if (existing.labelCount() > 0 || existing.getMeta(BACKFILLED_MARKER).isPresent()) {
              already++;
              continue;
            }
          }
          // Only backfill snapshots this source can re-fetch (matched by backend).
          java.util.Optional<CatalogStore.SnapshotProvenance> prov =
              catalog.snapshotProvenance(snap.versionId(), acronym);
          String backend = prov.map(CatalogStore.SnapshotProvenance::backend).orElse("bioportal");
          if (!myBackend.equals(backend)) {
            otherBackend++;
            continue;
          }
          // Prefer the exact submission when its id was recorded; otherwise fall back to the source's
          // current latest. Either way the version-id gate below rejects any content drift, so the
          // fallback can only ever enrich the identical snapshot, never a different one.
          Integer submissionId = prov.map(CatalogStore.SnapshotProvenance::submissionId).orElse(null);
          Submission sub;
          if (submissionId != null) {
            sub = source.listSubmissions(acronym).stream()
                .filter(s -> s.submissionId() == submissionId).findFirst().orElse(null);
          } else {
            sub = source.latestSubmission(acronym);
          }
          if (sub == null) {
            noSub++;
            log.warn("[{}/{}] {}: no re-fetchable submission (id {})", idx, acronyms.size(),
                acronym, submissionId);
            continue;
          }
          Path ontoDir = snapshotDir.resolve(acronym);
          Prepared prep = prepareSubmission(acronym, sub, ontoDir);
          Path tempFile = ontoDir.resolve(prep.rawHash() + ".backfill.tmp");
          Files.deleteIfExists(tempFile);
          List<SnapshotStore.LabelRow> labels;
          String recomputed;
          try (SnapshotStore tmp = SnapshotStore.openFile(tempFile.toString())) {
            tmp.initSchema();
            recomputed = extractInto(tmp, acronym, sub, prep.loadable()).versionId();
            labels = tmp.allLabels();
          } finally {
            Files.deleteIfExists(tempFile);
          }
          if (!recomputed.equals(snap.versionId())) {
            mismatched++;
            log.warn("[{}/{}] {}: re-extraction hashed to {} not {}; leaving snapshot untouched",
                idx, acronyms.size(), acronym, recomputed, snap.versionId());
            continue;
          }
          try (SnapshotStore existing = SnapshotStore.openFile(file.toString())) {
            existing.setBusyTimeoutMillis(60_000); // wait out the live server's brief read locks
            existing.initSchema();
            existing.addLabels(labels);
            existing.setMeta(BACKFILLED_MARKER, recomputed); // mark done even when there were 0 labels
          }
          filled++;
          labelsAdded += labels.size();
          log.info("[{}/{}] {} {}: +{} labels", idx, acronyms.size(), acronym, snap.versionId(), labels.size());
        } catch (Throwable e) {
          failed++;
          log.warn("[{}/{}] {} {}: backfill failed: {}", idx, acronyms.size(), acronym, snap.versionId(),
              e.toString());
          System.err.printf("FAIL %s %s: %s%n", acronym, snap.versionId(), e);
        }
      }
    }
    return new BackfillSummary(filled, already, otherBackend, noSub, mismatched, failed, labelsAdded);
  }

  /**
   * Like {@link #backfillLabels}, but re-extracts each snapshot's labels from the <b>retained local raw
   * download</b> — the file under {@code <snapshotDir>/<acronym>/raw/} whose SHA-256 equals the
   * snapshot's stored {@code file_hash} — instead of re-fetching from the source. This is the reliable
   * path once a source has drifted: BioPortal no longer serves the exact bytes a snapshot was built from
   * (its live submission moved on, or the ontology was withdrawn), so a re-download hashes to a different
   * version and the identity gate declines it (the {@code hash-mismatch} case). The retained raw is by
   * definition that exact content, so the recomputed hash matches and the labels attach. Needs no network
   * and no source credentials. Resumable/idempotent (skips snapshots already labeled or marked);
   * {@code only} limits the run to the given acronyms (empty = all).
   */
  public BackfillSummary backfillLabelsFromRaw(CatalogStore catalog, Path snapshotDir, Set<String> only)
      throws SQLException {
    int filled = 0, already = 0, noRaw = 0, drifted = 0, failed = 0;
    long labelsAdded = 0;
    List<String> acronyms = new ArrayList<>();
    for (CatalogStore.OntologyInfo o : catalog.listOntologies()) {
      if (only.isEmpty() || only.contains(o.acronym())) {
        acronyms.add(o.acronym());
      }
    }
    java.util.Optional<Path> base = catalog.baseDir();
    int idx = 0;
    for (String acronym : acronyms) {
      idx++;
      for (CatalogStore.SnapshotInfo snap : catalog.listSnapshots(acronym)) {
        Path file = base.map(b -> b.resolve(snap.filePath())).orElse(Path.of(snap.filePath()));
        try {
          try (SnapshotStore existing = SnapshotStore.openFile(file.toString())) {
            existing.initSchema();
            if (existing.labelCount() > 0 || existing.getMeta(BACKFILLED_MARKER).isPresent()) {
              already++;
              continue;
            }
          }
          Path rawDir = snapshotDir.resolve(acronym).resolve("raw");
          Path raw = findRawByHash(rawDir, snap.fileHash());
          if (raw == null) {
            noRaw++;
            log.warn("[{}/{}] {}: no retained raw matches file_hash {}", idx, acronyms.size(),
                acronym, snap.fileHash());
            continue;
          }
          Path loadable = stripOboImports(decompress(raw));
          // extractInto only reads the format off the Submission; the rest is display metadata we don't need.
          Submission sub = new Submission(0, "", "", snap.format());
          Path tempFile = rawDir.resolveSibling(snap.versionId() + ".rawbackfill.tmp");
          Files.deleteIfExists(tempFile);
          List<SnapshotStore.LabelRow> labels;
          String recomputed;
          try (SnapshotStore tmp = SnapshotStore.openFile(tempFile.toString())) {
            tmp.initSchema();
            recomputed = extractInto(tmp, acronym, sub, loadable).versionId();
            labels = tmp.allLabels();
          } finally {
            Files.deleteIfExists(tempFile);
          }
          // No version-id gate here (unlike the source-refetch path): the matched file_hash already
          // proves this raw is the exact bytes the snapshot was built from, and labels are keyed by
          // concept IRI (addLabels is INSERT-OR-IGNORE on c.iri), so authentic labels attach to the right
          // concepts even when today's extractor derives a different model hash than the stored snapshot
          // (extractor evolution since ingest). A drift is noted, not fatal.
          if (!recomputed.equals(snap.versionId())) {
            drifted++;
            log.info("[{}/{}] {}: extractor drift (recomputed {} vs stored {}); labels still authentic "
                + "(raw hash matched)", idx, acronyms.size(), acronym, recomputed, snap.versionId());
          }
          try (SnapshotStore existing = SnapshotStore.openFile(file.toString())) {
            existing.setBusyTimeoutMillis(60_000); // wait out the live server's brief read locks
            existing.initSchema();
            existing.addLabels(labels);
            existing.setMeta(BACKFILLED_MARKER, recomputed);
          }
          filled++;
          labelsAdded += labels.size();
          log.info("[{}/{}] {} {}: +{} labels (from local raw)", idx, acronyms.size(), acronym,
              snap.versionId(), labels.size());
        } catch (Throwable e) {
          failed++;
          log.warn("[{}/{}] {} {}: raw backfill failed: {}", idx, acronyms.size(), acronym,
              snap.versionId(), e.toString());
          System.err.printf("FAIL %s %s: %s%n", acronym, snap.versionId(), e);
        }
      }
    }
    // otherBackend is unused here (no source involved); noSubmission carries no-matching-raw, mismatched
    // carries the drift count (those were still filled — informational only).
    return new BackfillSummary(filled, already, 0, noRaw, drifted, failed, labelsAdded);
  }

  /**
   * Fills in definitions for snapshots already written, from the raw download each was built from.
   *
   * The same route the label backfill takes, for the same reason: the raw matched by file hash is
   * the exact bytes the snapshot came from, so what is extracted from it is authentic even where
   * today's extractor computes a different model hash than the one stored. Definitions attach by
   * concept IRI, so a drift in the hierarchy does not misplace them.
   *
   * Additive throughout — the definition table is outside content identity, so no version id moves
   * and no pin is disturbed. Resumable: a snapshot that already holds definitions is skipped.
   */
  /** Marks a snapshot whose definitions have been considered, so a resume skips it. */
  private static final String DEFINED_MARKER = "definitionsBackfilled";

  public BackfillSummary backfillDefinitionsFromRaw(CatalogStore catalog, Path snapshotDir, Set<String> only)
      throws SQLException {
    int filled = 0, already = 0, noRaw = 0, none = 0, failed = 0;
    long added = 0;
    List<String> acronyms = new ArrayList<>();
    for (CatalogStore.OntologyInfo o : catalog.listOntologies()) {
      if (only.isEmpty() || only.contains(o.acronym())) {
        acronyms.add(o.acronym());
      }
    }
    java.util.Optional<Path> base = catalog.baseDir();
    int idx = 0;
    for (String acronym : acronyms) {
      idx++;
      for (CatalogStore.SnapshotInfo snap : catalog.listSnapshots(acronym)) {
        Path file = base.map(b -> b.resolve(snap.filePath())).orElse(Path.of(snap.filePath()));
        try {
          try (SnapshotStore existing = SnapshotStore.openFile(file.toString())) {
            existing.initSchema();
            if (existing.definitionCount() > 0 || existing.getMeta(DEFINED_MARKER).isPresent()) {
              already++;
              continue;
            }
          }
          Path rawDir = snapshotDir.resolve(acronym).resolve("raw");
          Path raw = findRawByHash(rawDir, snap.fileHash());
          if (raw == null) {
            noRaw++;
            continue;
          }
          Path loadable = stripOboImports(decompress(raw));
          Submission sub = new Submission(0, "", "", snap.format());
          Path tempFile = rawDir.resolveSibling(snap.versionId() + ".defbackfill.tmp");
          Files.deleteIfExists(tempFile);
          List<SnapshotStore.DefinitionRow> definitions;
          try (SnapshotStore tmp = SnapshotStore.openFile(tempFile.toString())) {
            tmp.initSchema();
            extractInto(tmp, acronym, sub, loadable);
            definitions = tmp.allDefinitions();
          } finally {
            Files.deleteIfExists(tempFile);
          }
          try (SnapshotStore existing = SnapshotStore.openFile(file.toString())) {
            existing.setBusyTimeoutMillis(60_000); // wait out the live server's brief read locks
            existing.initSchema();
            existing.addDefinitions(definitions);
            // Marked either way, so an ontology that genuinely asserts none is not retried for ever.
            existing.setMeta(DEFINED_MARKER, String.valueOf(definitions.size()));
          }
          if (definitions.isEmpty()) {
            none++;
          } else {
            filled++;
            added += definitions.size();
          }
          log.info("[{}/{}] {} {}: +{} definitions (from local raw)", idx, acronyms.size(), acronym,
              snap.versionId(), definitions.size());
        } catch (Throwable e) {
          failed++;
          log.warn("[{}/{}] {} {}: definition backfill failed: {}", idx, acronyms.size(), acronym,
              snap.versionId(), e.toString());
        }
      }
    }
    // noSubmission carries no-matching-raw; mismatched carries the count that legitimately have none.
    return new BackfillSummary(filled, already, 0, noRaw, none, failed, added);
  }

  /** The file in {@code rawDir} whose SHA-256 equals {@code wantHash} (the exact download a snapshot was
   *  built from), or null. Located by content, so a later re-download left in the same dir can't fool it. */
  private static Path findRawByHash(Path rawDir, String wantHash) throws IOException {
    if (wantHash == null || wantHash.isBlank() || !Files.isDirectory(rawDir)) {
      return null;
    }
    try (java.util.stream.Stream<Path> entries = Files.list(rawDir)) {
      for (Path p : (Iterable<Path>) entries.sorted()::iterator) {
        if (Files.isRegularFile(p) && wantHash.equals(sha256(p))) {
          return p;
        }
      }
    }
    return null;
  }

  /** Tally of a store integrity check. */
  public record VerifySummary(int snapshots, int ok, int missingFile, int unreadable, int emptyConcepts,
                              int hashMismatch, int unresolvableLatest, int orphanFiles) {
    public boolean clean() {
      return missingFile + unreadable + emptyConcepts + hashMismatch + unresolvableLatest + orphanFiles == 0;
    }
  }

  /**
   * Read-only integrity check of the store. The reproducibility guarantee is only as good as snapshot
   * retention, and nothing else verifies it: this confirms every catalog snapshot row resolves to a
   * present, readable file that holds concepts; that every ontology's {@code latest} resolves to a
   * present snapshot; and reports any snapshot {@code .sqlite} on disk the catalog does not reference (an
   * orphan). With {@code deep}, it also recomputes each snapshot's content hash and asserts it equals the
   * stored {@code version_id} — this reads every snapshot in full (28&nbsp;GB+), so it is opt-in. Mutates
   * nothing; problems are logged with a leading tag (MISSING FILE / UNREADABLE / EMPTY / HASH MISMATCH /
   * NO LATEST / ORPHAN FILE) and tallied.
   */
  public VerifySummary verifyStore(CatalogStore catalog, Path snapshotDir, boolean deep)
      throws SQLException, IOException {
    int total = 0, ok = 0, missingFile = 0, unreadable = 0, emptyConcepts = 0, hashMismatch = 0,
        unresolvableLatest = 0;
    java.util.Optional<Path> base = catalog.baseDir();
    java.util.Set<Path> referenced = new java.util.HashSet<>();
    for (CatalogStore.OntologyInfo o : catalog.listOntologies()) {
      for (CatalogStore.SnapshotInfo snap : catalog.listSnapshots(o.acronym())) {
        total++;
        Path file = base.map(b -> b.resolve(snap.filePath())).orElse(Path.of(snap.filePath()));
        referenced.add(file.toAbsolutePath().normalize());
        if (!Files.isRegularFile(file)) {
          missingFile++;
          log.error("MISSING FILE   {} {} -> {}", o.acronym(), snap.versionId(), snap.filePath());
          continue;
        }
        try (SnapshotStore s = SnapshotStore.openFile(file.toString())) {
          if (s.conceptCount() == 0) { // throws below if the concept table is absent (malformed store)
            emptyConcepts++;
            log.warn("EMPTY          {} {} (0 concepts)", o.acronym(), snap.versionId());
            continue;
          }
          if (deep) {
            String recomputed = s.normalizedContentHash(true);
            if (!recomputed.equals(snap.versionId())) {
              hashMismatch++;
              log.error("HASH MISMATCH  {} recomputed {} != stored {}", o.acronym(), recomputed,
                  snap.versionId());
              continue;
            }
          }
        } catch (Exception e) {
          unreadable++;
          log.error("UNREADABLE     {} {} ({}): {}", o.acronym(), snap.versionId(), file, e.toString());
          continue;
        }
        ok++;
      }
      // Every served ontology should have a latest that resolves to a present snapshot file.
      java.util.Optional<CatalogStore.SnapshotInfo> latest = catalog.resolveLatest(o.acronym());
      if (latest.isEmpty()) {
        unresolvableLatest++;
        log.error("NO LATEST      {} (latest tag missing or dangling)", o.acronym());
      } else {
        Path lf = base.map(b -> b.resolve(latest.get().filePath())).orElse(Path.of(latest.get().filePath()));
        if (!Files.isRegularFile(lf)) {
          unresolvableLatest++;
          log.error("LATEST MISSING {} -> {} (file absent)", o.acronym(), latest.get().filePath());
        }
      }
    }
    int orphanFiles = reportOrphanSnapshotFiles(snapshotDir, referenced);
    return new VerifySummary(total, ok, missingFile, unreadable, emptyConcepts, hashMismatch,
        unresolvableLatest, orphanFiles);
  }

  /** Snapshot {@code .sqlite} files under {@code snapshotDir} that no catalog row references (the per-
   *  ontology {@code raw/} downloads are excluded). Reported, never deleted. */
  private static int reportOrphanSnapshotFiles(Path snapshotDir, java.util.Set<Path> referenced)
      throws IOException {
    if (!Files.isDirectory(snapshotDir)) {
      return 0;
    }
    int orphans = 0;
    try (java.util.stream.Stream<Path> walk = Files.walk(snapshotDir)) {
      for (Path p : (Iterable<Path>) walk::iterator) {
        if (!Files.isRegularFile(p) || !p.getFileName().toString().endsWith(".sqlite")
            || p.toString().contains("/raw/")) {
          continue;
        }
        if (!referenced.contains(p.toAbsolutePath().normalize())) {
          orphans++;
          log.warn("ORPHAN FILE    {} ({} bytes) — on disk but no catalog row references it", p,
              Files.size(p));
        }
      }
    }
    return orphans;
  }

  /** Tally of an orphan-file prune. */
  public record PruneSummary(int orphans, long bytes, int referencedPresent, boolean applied) {}

  /**
   * Deletes — or by default only reports — snapshot {@code .sqlite} files under {@code snapshotDir} that
   * no catalog row references, the reclaimable residue of re-ingests. Read-only unless {@code apply}.
   * Guards against a path misconfiguration wiping the store: if not one catalog-referenced snapshot is
   * found on disk, every file would look orphaned, so it refuses to delete anything.
   */
  public PruneSummary pruneOrphans(CatalogStore catalog, Path snapshotDir, boolean apply)
      throws SQLException, IOException {
    java.util.Set<Path> referenced = referencedSnapshotFiles(catalog);
    java.util.List<Path> orphans = new java.util.ArrayList<>();
    long bytes = 0;
    int referencedPresent = 0;
    if (Files.isDirectory(snapshotDir)) {
      try (java.util.stream.Stream<Path> walk = Files.walk(snapshotDir)) {
        for (Path p : (Iterable<Path>) walk::iterator) {
          if (!Files.isRegularFile(p) || !p.getFileName().toString().endsWith(".sqlite")
              || p.toString().contains("/raw/")) {
            continue;
          }
          if (referenced.contains(p.toAbsolutePath().normalize())) {
            referencedPresent++;
          } else {
            orphans.add(p);
            bytes += Files.size(p);
          }
        }
      }
    }
    // Safety: if nothing on disk matches the catalog the paths are misconfigured — every file looks
    // orphaned. Refuse to delete rather than wipe the store.
    if (apply && !orphans.isEmpty() && referencedPresent == 0) {
      throw new IOException("refusing to prune: no catalog-referenced snapshot found under " + snapshotDir
          + " — check the catalog and snapshot paths");
    }
    for (Path p : orphans) {
      long sz = Files.size(p);
      if (apply) {
        Files.delete(p);
        log.warn("DELETED ORPHAN {} ({} bytes)", p, sz);
      } else {
        log.warn("WOULD DELETE   {} ({} bytes)", p, sz);
      }
    }
    return new PruneSummary(orphans.size(), bytes, referencedPresent, apply);
  }

  /** Absolute, normalized paths of every snapshot file the catalog references. */
  private static java.util.Set<Path> referencedSnapshotFiles(CatalogStore catalog) throws SQLException {
    java.util.Optional<Path> base = catalog.baseDir();
    java.util.Set<Path> referenced = new java.util.HashSet<>();
    for (CatalogStore.OntologyInfo o : catalog.listOntologies()) {
      for (CatalogStore.SnapshotInfo snap : catalog.listSnapshots(o.acronym())) {
        Path file = base.map(b -> b.resolve(snap.filePath())).orElse(Path.of(snap.filePath()));
        referenced.add(file.toAbsolutePath().normalize());
      }
    }
    return referenced;
  }

  /** Tally of a header-IRI backfill run. */
  public record HeaderIriSummary(int targets, int headerFound, int noHeader, int failed,
                                 int nowIdentified, int stillAcronymOnly) {}

  /**
   * Restores identity for acronym-only ontologies (item 6) from the {@code owl:Ontology} header. For
   * every ontology the catalog leaves without a canonical iri, downloads its latest submission, reads
   * the declared header IRI ({@link OntologyHeaderIri}) — cheaply, a file-head scan, not a full parse —
   * and sets it as the ontology's identity, also recording it in the served snapshot's {@code meta}.
   * A single de-confliction pass then settles collisions (a header IRI two content-distinct ontologies
   * both declare is still not a merge), so an ontology can end still acronym-only if it has no header or
   * its header collides. The catalog is written in place; {@code only} limits the run to given acronyms.
   */
  public HeaderIriSummary backfillHeaderIris(CatalogStore catalog, Path snapshotDir, Set<String> only)
      throws SQLException {
    List<String> targets = new ArrayList<>();
    for (CatalogStore.OntologyInfo o : catalog.listOntologies()) {
      if (!only.isEmpty() && !only.contains(o.acronym())) {
        continue;
      }
      if (catalog.ontologyIri(o.acronym()).isEmpty()) {
        targets.add(o.acronym());
      }
    }
    int headerFound = 0, noHeader = 0, failed = 0, i = 0;
    java.util.Optional<Path> base = catalog.baseDir();
    for (String acronym : targets) {
      i++;
      try {
        Submission sub = source.latestSubmission(acronym); // the header IRI is stable across versions
        Prepared prep = prepareSubmission(acronym, sub, snapshotDir.resolve(acronym));
        java.util.Optional<String> header = OntologyHeaderIri.fromFile(prep.loadable());
        if (header.isEmpty()) {
          noHeader++;
          log.info("[{}/{}] {}: no owl:Ontology header IRI", i, targets.size(), acronym);
          continue;
        }
        catalog.setOntologyIri(acronym, OntologyIri.canonical(header.get()), header.get());
        // Persist the declared IRI in the served snapshot too, so a later re-derivation keeps it.
        for (CatalogStore.SnapshotInfo snap : catalog.listSnapshots(acronym)) {
          Path file = base.map(b -> b.resolve(snap.filePath())).orElse(Path.of(snap.filePath()));
          try (SnapshotStore s = SnapshotStore.openFile(file.toString())) {
            s.setBusyTimeoutMillis(60_000);
            s.initSchema();
            s.setMeta("ontology_iri", header.get());
          }
        }
        headerFound++;
        log.info("[{}/{}] {}: header IRI {}", i, targets.size(), acronym, header.get());
      } catch (Throwable e) {
        failed++;
        log.warn("[{}/{}] {}: header backfill failed: {}", i, targets.size(), acronym, e.toString());
        System.err.printf("FAIL %s: %s%n", acronym, e);
      }
    }
    // Settle collisions among the newly-set header IRIs, and against existing identities.
    IriDeconfliction.run(catalog, true);
    int stillAcronymOnly = 0;
    for (String acronym : targets) {
      if (catalog.ontologyIri(acronym).isEmpty()) {
        stillAcronymOnly++;
      }
    }
    return new HeaderIriSummary(targets.size(), headerFound, noHeader, failed,
        targets.size() - stillAcronymOnly, stillAcronymOnly);
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
   * Usage: IngestJob &lt;catalogPath&gt; &lt;snapshotDir&gt; [--source bioportal|obofoundry] [--release
   *        &lt;date&gt;] [--all] [--valuesets] [--submission &lt;id&gt;] &lt;acronym&gt; [acronym...]
   * With {@code --all}, every submission (version) of each ontology is ingested; otherwise only the
   * latest. With {@code --valuesets}, the listed acronyms are ingested as value-set collections
   * (same content-hash mechanism, marked {@code value_set_collection} in the catalog) rather than
   * ontologies. {@code --source obofoundry} draws from the OBO Foundry PURL instead of BioPortal — the
   * current release, or the {@code --release <date>} dated release — and records the snapshot backend
   * accordingly; identity is unchanged (the content hash is source-independent), so the same release
   * from either source merges. {@code --submission} is BioPortal-only. The BioPortal API key is read
   * from {@code BIOPORTAL_API_KEY} (required only for the BioPortal source).
   */
  /**
   * The ingest source named by {@code --source}: {@code bioportal} (default; needs
   * {@code BIOPORTAL_API_KEY}) or {@code obofoundry} (public; {@code release} targets a dated PURL, or
   * null for the current release). Exits the process on an unknown name or a missing BioPortal key.
   * Package-visible for testing the selection without the network.
   */
  static SubmissionSource selectSource(String sourceName, String release) {
    if ("obofoundry".equals(sourceName)) {
      return new OboFoundrySubmissionSource(release);
    }
    if ("bioportal".equals(sourceName)) {
      String apiKey = System.getenv("BIOPORTAL_API_KEY");
      if (apiKey == null || apiKey.isBlank()) {
        System.err.println("BIOPORTAL_API_KEY environment variable is not set");
        System.exit(2);
      }
      return new BioPortalDownloader(apiKey);
    }
    System.err.println("Unknown --source '" + sourceName + "' (expected bioportal, obofoundry, or url)");
    System.exit(2);
    throw new IllegalStateException("unreachable"); // System.exit does not return
  }

  /**
   * Overload adding two source options: {@code --source url} downloads from {@code --url <URL>} with an
   * optional {@code --format} ({@code OWL} default, or {@code SKOS}) and {@code --backend} label; and a
   * {@code --base-url} pointing {@code --source bioportal} at any OntoPortal instance (AgroPortal,
   * EcoPortal, …) instead of BioPortal — same REST API, its own {@code BIOPORTAL_API_KEY}. Any other
   * combination delegates to {@link #selectSource(String, String)}.
   */
  static SubmissionSource selectSource(String sourceName, String release, String url, String format,
                                       String backend, String baseUrl) {
    if ("url".equals(sourceName)) {
      if (url == null || url.isBlank()) {
        System.err.println("--source url requires --url <URL>");
        System.exit(2);
      }
      return new DirectUrlSubmissionSource(url, format, backend);
    }
    if ("bioportal".equals(sourceName) && baseUrl != null && !baseUrl.isBlank()) {
      String apiKey = System.getenv("BIOPORTAL_API_KEY");
      if (apiKey == null || apiKey.isBlank()) {
        System.err.println("BIOPORTAL_API_KEY environment variable is not set (the OntoPortal instance's key)");
        System.exit(2);
      }
      return new BioPortalDownloader(apiKey, baseUrl);
    }
    return selectSource(sourceName, release);
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("Usage: IngestJob <catalogPath> <snapshotDir> "
          + "[--source bioportal|obofoundry|url] [--url <URL>] [--format OWL|SKOS] [--backend <name>] "
          + "[--base-url <ontoportal-api>] "
          + "[--release <date>] [--all] [--valuesets] [--submission <id>] <acronym> [acronym...]");
      System.exit(2);
    }
    Path catalogPath = Path.of(args[0]);
    Path snapshotDir = Path.of(args[1]);
    Files.createDirectories(snapshotDir);

    boolean all = false;
    boolean valuesets = false;
    boolean backfillLabels = false;
    boolean backfillLabelsFromRaw = false;
    boolean backfillDefinitionsFromRaw = false;
    boolean backfillHeaderIri = false;
    boolean verify = false;
    boolean deep = false;
    boolean pruneOrphans = false;
    boolean apply = false;
    String sourceName = "bioportal";
    String oboRelease = null;
    String url = null;
    String format = null;
    String backend = null;
    String baseUrl = null;
    List<Integer> submissionIds = new ArrayList<>();
    List<String> acronyms = new ArrayList<>();
    for (int i = 2; i < args.length; i++) {
      if ("--all".equals(args[i])) {
        all = true;
      } else if ("--valuesets".equals(args[i])) {
        valuesets = true;
      } else if ("--backfill-labels".equals(args[i])) {
        backfillLabels = true;
      } else if ("--backfill-labels-from-raw".equals(args[i])) {
        backfillLabelsFromRaw = true;
      } else if ("--backfill-definitions-from-raw".equals(args[i])) {
        backfillDefinitionsFromRaw = true;
      } else if ("--verify".equals(args[i])) {
        verify = true;
      } else if ("--deep".equals(args[i])) {
        deep = true;
      } else if ("--prune-orphans".equals(args[i])) {
        pruneOrphans = true;
      } else if ("--apply".equals(args[i])) {
        apply = true;
      } else if ("--backfill-header-iri".equals(args[i])) {
        backfillHeaderIri = true;
      } else if ("--source".equals(args[i]) && i + 1 < args.length) {
        sourceName = args[++i];
      } else if ("--release".equals(args[i]) && i + 1 < args.length) {
        oboRelease = args[++i];
      } else if ("--url".equals(args[i]) && i + 1 < args.length) {
        url = args[++i];
      } else if ("--format".equals(args[i]) && i + 1 < args.length) {
        format = args[++i];
      } else if ("--backend".equals(args[i]) && i + 1 < args.length) {
        backend = args[++i];
      } else if ("--base-url".equals(args[i]) && i + 1 < args.length) {
        baseUrl = args[++i];
      } else if ("--submission".equals(args[i]) && i + 1 < args.length) {
        submissionIds.add(Integer.parseInt(args[++i]));
      } else {
        acronyms.add(args[i]);
      }
    }

    // A raw-file backfill, the integrity check, and the orphan prune read only local files, so they need
    // no source or credentials.
    SubmissionSource source = (backfillLabelsFromRaw || verify || pruneOrphans)
        ? null : selectSource(sourceName, oboRelease, url, format, backend, baseUrl);
    IngestJob job = new IngestJob(source);
    try (CatalogStore catalog = CatalogStore.openFile(catalogPath.toString())) {
      catalog.setBusyTimeoutMillis(60_000); // wait out a live server's brief catalog read locks
      catalog.initSchema();
      if (backfillLabels) {
        IngestJob.BackfillSummary sum =
            job.backfillLabels(catalog, snapshotDir, new java.util.HashSet<>(acronyms));
        System.out.printf(
            "backfill-labels: %d filled (+%d labels), %d already labeled, %d other-backend, "
                + "%d no-submission, %d hash-mismatch, %d failed%n",
            sum.filled(), sum.labelsAdded(), sum.alreadyLabeled(), sum.otherBackend(),
            sum.noSubmission(), sum.mismatched(), sum.failed());
        return;
      }
      if (backfillLabelsFromRaw) {
        IngestJob.BackfillSummary sum =
            job.backfillLabelsFromRaw(catalog, snapshotDir, new java.util.HashSet<>(acronyms));
        System.out.printf(
            "backfill-labels-from-raw: %d filled (+%d labels; %d of them despite extractor-drift), "
                + "%d already labeled, %d no-matching-raw, %d failed%n",
            sum.filled(), sum.labelsAdded(), sum.mismatched(), sum.alreadyLabeled(),
            sum.noSubmission(), sum.failed());
        return;
      }
      if (backfillDefinitionsFromRaw) {
        IngestJob.BackfillSummary sum =
            job.backfillDefinitionsFromRaw(catalog, snapshotDir, new java.util.HashSet<>(acronyms));
        System.out.printf(
            "backfill-definitions-from-raw: %d filled (+%d definitions), %d already done, "
                + "%d assert none, %d no-matching-raw, %d failed%n",
            sum.filled(), sum.labelsAdded(), sum.alreadyLabeled(), sum.mismatched(),
            sum.noSubmission(), sum.failed());
        return;
      }
      if (verify) {
        IngestJob.VerifySummary v = job.verifyStore(catalog, snapshotDir, deep);
        System.out.printf(
            "verify%s: %d snapshots — %d ok, %d missing-file, %d unreadable, %d empty, %d hash-mismatch, "
                + "%d unresolvable-latest, %d orphan-files%n",
            deep ? " (deep)" : "", v.snapshots(), v.ok(), v.missingFile(), v.unreadable(),
            v.emptyConcepts(), v.hashMismatch(), v.unresolvableLatest(), v.orphanFiles());
        if (!v.clean()) {
          System.err.println("STORE INTEGRITY: problems found (see the tagged lines above); exit 1.");
          System.exit(1);
        }
        return;
      }
      if (pruneOrphans) {
        IngestJob.PruneSummary p = job.pruneOrphans(catalog, snapshotDir, apply);
        System.out.printf("prune-orphans %s: %d orphan files (%.1f MB), %d referenced snapshots present%n",
            p.applied() ? "APPLIED" : "dry-run (pass --apply to delete)",
            p.orphans(), p.bytes() / 1048576.0, p.referencedPresent());
        return;
      }
      if (backfillHeaderIri) {
        catalog.setBusyTimeoutMillis(60_000); // write identity while the server reads the catalog
        IngestJob.HeaderIriSummary sum =
            job.backfillHeaderIris(catalog, snapshotDir, new java.util.HashSet<>(acronyms));
        System.out.printf(
            "backfill-header-iri: %d acronym-only targets — %d header found, %d no header, %d failed; "
                + "after de-confliction: %d now identified, %d still acronym-only%n",
            sum.targets(), sum.headerFound(), sum.noHeader(), sum.failed(),
            sum.nowIdentified(), sum.stillAcronymOnly());
        return;
      }
      for (String acronym : acronyms) {
        if (!submissionIds.isEmpty()) {
          // Ingest specific historical submissions without moving the latest tag.
          List<Submission> subs = source.listSubmissions(acronym);
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
