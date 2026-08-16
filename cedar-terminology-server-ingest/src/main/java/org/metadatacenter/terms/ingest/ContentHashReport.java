package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Measurement for the hash-basis decision (VERSIONING-ROADMAP "The Model" §4.3), read-only: computes the
 * normalized content hash — both structure-only and structure+labels — alongside the raw-file
 * {@code version_id} for every snapshot of the chosen ontologies, and reports where they diverge.
 *
 * Two questions it answers with data, before any cutover:
 * <ul>
 *   <li><b>Does raw-byte identity over-split?</b> Distinct raw version_ids that share a normalized
 *       hash are byte-different but content-identical (a re-serialization, e.g. OBO→OWL): today they
 *       are two versions; normalized identity would merge them into one.</li>
 *   <li><b>How much do labels move on their own?</b> The gap between the count of distinct
 *       structure-only hashes and distinct full hashes is the number of states that differ only in
 *       labels — the cost of the "include labels" knob.</li>
 * </ul>
 *
 * Usage: {@code ContentHashReport <catalogPath> [acronym...]}. With no acronyms, every multi-version
 * ontology (more than one snapshot) is reported.
 */
public final class ContentHashReport {

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: ContentHashReport <catalogPath> [acronym...]");
      System.exit(2);
    }
    Path catalogPath = Path.of(args[0]);
    java.util.Set<String> only = new java.util.HashSet<>(java.util.Arrays.asList(args).subList(1, args.length));

    try (CatalogStore catalog = CatalogStore.openFile(catalogPath.toString())) {
      List<String> acronyms = new ArrayList<>();
      for (CatalogStore.OntologyInfo o : catalog.listOntologies()) {
        List<CatalogStore.SnapshotInfo> snaps = catalog.listSnapshots(o.acronym());
        boolean wanted = only.isEmpty() ? snaps.size() > 1 : only.contains(o.acronym());
        if (wanted) {
          acronyms.add(o.acronym());
        }
      }

      System.out.printf("%-12s %5s %5s %6s %6s %5s   %s%n",
          "ONTOLOGY", "vers", "raw", "struct", "full", "merge", "note");
      long totVers = 0, totOverSplit = 0;
      for (String acronym : acronyms) {
        List<CatalogStore.SnapshotInfo> snaps = catalog.listSnapshots(acronym);
        java.util.Set<String> raw = new java.util.HashSet<>();
        Map<String, List<String>> byStruct = new LinkedHashMap<>();
        Map<String, List<String>> byFull = new LinkedHashMap<>();
        int failed = 0;
        for (CatalogStore.SnapshotInfo s : snaps) {
          raw.add(s.versionId());
          try (SnapshotStore store = SnapshotStore.openFile(s.filePath())) {
            byStruct.computeIfAbsent(store.normalizedContentHash(false), k -> new ArrayList<>()).add(s.versionId());
            byFull.computeIfAbsent(store.normalizedContentHash(true), k -> new ArrayList<>()).add(s.versionId());
          } catch (Exception e) {
            failed++;
          }
        }
        // Over-split: raw version_ids that collapse to one full-content hash (byte-different, content
        // identical). Counted as "raw snapshots that would merge away" = sum over groups of (size-1).
        int mergeAway = byFull.values().stream().mapToInt(g -> g.size() - 1).sum();
        String note = mergeAway > 0
            ? mergeAway + " raw snapshot(s) are re-serializations of another (would merge)"
            : (failed > 0 ? failed + " snapshot(s) unreadable" : "");
        System.out.printf("%-12s %5d %5d %6d %6d %5d   %s%n",
            acronym, snaps.size(), raw.size(), byStruct.size(), byFull.size(), mergeAway, note);
        totVers += snaps.size();
        totOverSplit += mergeAway;
      }
      System.out.printf("%nTotals: %d snapshots across %d ontologies; %d raw over-splits that "
          + "normalized (full) identity would merge.%n", totVers, acronyms.size(), totOverSplit);
      System.out.println("Columns: raw=distinct raw version_ids, struct=distinct structure-only "
          + "hashes, full=distinct structure+label hashes. full-struct = states differing only in labels.");
    }
  }

  private ContentHashReport() {}
}
