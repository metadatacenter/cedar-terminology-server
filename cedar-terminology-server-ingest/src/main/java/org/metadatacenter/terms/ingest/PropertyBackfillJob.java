package org.metadatacenter.terms.ingest;

import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;

/**
 * Offline enrichment of the same ontology snapshot model. Existing pinned files are never modified.
 */
public final class PropertyBackfillJob {
  static String sha256(Path file) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    // retainByHash compresses originally plain downloads as <source-hash>.gz. A .raw file
    // instead holds an already-compressed download, whose compressed bytes were hashed.
    try (InputStream original = Files.newInputStream(file);
        InputStream in =
            file.getFileName().toString().endsWith(".gz")
                ? new java.util.zip.GZIPInputStream(original)
                : original) {
      byte[] b = new byte[65536];
      int n;
      while ((n = in.read(b)) != -1) md.update(b, 0, n);
    }
    return HexFormat.of().formatHex(md.digest());
  }

  static Path raw(Path directory, String hash) throws Exception {
    if (hash == null || !Files.isDirectory(directory)) return null;
    try (var files = Files.list(directory)) {
      for (Path p :
          files
              .filter(Files::isRegularFile)
              .filter(
                  p -> !p.toString().contains(".expanded") && !p.toString().contains(".stripped"))
              .sorted()
              .toList()) {
        if (p.getFileName().toString().startsWith(hash) && sha256(p).equals(hash)) return p;
      }
    }
    return null;
  }

  public static String enrich(CatalogStore catalog, CatalogStore.SnapshotInfo old, Path raw)
      throws Exception {
    if (!sha256(raw).equals(old.fileHash()))
      throw new IllegalArgumentException("Archive hash does not match the catalog release");
    Path base = catalog.baseDir().orElseThrow();
    Path original = base.resolve(old.filePath());
    if (!Files.isRegularFile(original))
      throw new IllegalArgumentException("Snapshot file missing: " + original);
    Path temp = Files.createTempFile(original.getParent(), "property-enrichment-", ".sqlite.tmp");
    try {
      Files.copy(original, temp, StandardCopyOption.REPLACE_EXISTING);
      String version;
      try (SnapshotStore snapshot = SnapshotStore.openFile(temp.toString())) {
        snapshot.initSchema();
        snapshot
            .properties()
            .write(
                new PropertyExtractor()
                    .extract(IngestJob.stripOboImports(IngestJob.decompress(raw))));
        snapshot.setMeta("properties_from_version", old.versionId());
        version = snapshot.normalizedContentHash(true);
      }
      Path enriched = original.resolveSibling(version + ".sqlite");
      if (!Files.exists(enriched)) Files.move(temp, enriched);
      // File first, then catalog + latest atomically. An interrupted run can leave only an orphan
      // file.
      catalog.inTransaction(
          () -> {
            catalog.addSnapshot(
                new CatalogStore.SnapshotInfo(
                    version,
                    old.acronym(),
                    old.declaredVersion(),
                    old.releasedAt(),
                    Instant.now().toString(),
                    old.format(),
                    old.hierarchyStatus(),
                    old.classCount(),
                    old.edgeCount(),
                    base.relativize(enriched).toString(),
                    old.fileHash(),
                    old.licenseTier()));
            var provenance = catalog.snapshotProvenance(old.versionId(), old.acronym());
            if (provenance.isPresent()) {
              var p = provenance.get();
              catalog.setSnapshotProvenance(
                  version, old.acronym(), p.submissionId(), p.sourceDate());
              catalog.setSnapshotBackend(version, old.acronym(), p.backend());
            }
            catalog.recordPropertyEnrichment(old.acronym(), old.versionId(), version);
            var current = catalog.resolveLatest(old.acronym());
            if (current.isPresent() && current.get().versionId().equals(old.versionId()))
              catalog.setTag(old.acronym(), CatalogStore.TAG_LATEST, version);
          });
      return version;
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 1)
      throw new IllegalArgumentException(
          "Usage: PropertyBackfillJob catalog.sqlite [--apply] [ACRONYM ...]");
    boolean apply = Arrays.asList(args).contains("--apply");
    Set<String> only = new HashSet<>(Arrays.asList(args).subList(1, args.length));
    only.remove("--apply");
    Path catalogPath = Path.of(args[0]).toAbsolutePath();
    int found = 0, missing = 0, done = 0, skipped = 0, failed = 0;
    try (CatalogStore catalog = CatalogStore.openForRead(catalogPath.toString())) {
      for (CatalogStore.OntologyInfo ontology : catalog.listOntologies()) {
        String acronym = ontology.acronym();
        if (!only.isEmpty() && !only.contains(acronym)) continue;
        // Materialize before writing: enriched versions must not be processed again in this run.
        for (CatalogStore.SnapshotInfo snapshot : catalog.listSnapshots(acronym)) {
          try {
            if (catalog.propertyEnrichment(acronym, snapshot.versionId()).isPresent()) {
              skipped++;
              continue;
            }
            Path file = catalogPath.getParent().resolve(snapshot.filePath());
            if (!Files.isRegularFile(file))
              throw new IllegalArgumentException("Snapshot file missing: " + file);
            try (SnapshotStore existing = SnapshotStore.openForRead(file.toString())) {
              if (existing.properties().available()) {
                skipped++;
                continue;
              }
            }
            Path raw =
                raw(
                    catalogPath.getParent().resolve("snapshots").resolve(acronym).resolve("raw"),
                    snapshot.fileHash());
            if (raw == null) {
              missing++;
              System.out.println("MISSING\t" + acronym + "\t" + snapshot.versionId());
              continue;
            }
            found++;
            if (!apply) {
              System.out.println("AVAILABLE\t" + acronym + "\t" + snapshot.versionId());
              continue;
            }
            String version = enrich(catalog, snapshot, raw);
            done++;
            System.out.println("STORED\t" + acronym + "\t" + snapshot.versionId() + "\t" + version);
          } catch (Exception e) {
            failed++;
            System.err.println("FAILED\t" + acronym + "\t" + snapshot.versionId() + "\t" + e);
          }
        }
      }
    }
    System.out.printf(
        "SUMMARY available=%d missing=%d stored=%d skipped=%d failed=%d%n",
        found, missing, done, skipped, failed);
    if (failed > 0) System.exit(1);
  }
}
