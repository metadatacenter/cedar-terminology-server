package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.CatalogStore;

import java.nio.file.Path;

/**
 * One-off backfill for the display/audit-only provenance columns, offline (no BioPortal calls, no
 * content fetch).
 *
 * {@code backend} needs no work — its column default sets every existing row to {@code bioportal}.
 * {@code source_date} is derived here from each snapshot's declared-version string (the version's
 * self-claimed date, VERSIONING-DESIGN §2), which is already in the catalog. {@code submission_id}
 * is left untouched: BioPortal's per-upload id is not reconstructable from anything on disk, so it is
 * captured at ingest going forward and stays null for snapshots ingested before that capture existed
 * (recoverable later only from live BioPortal submission metadata, which this offline backfill
 * declines to depend on).
 *
 * Usage: {@code ProvenanceBackfill <catalogPath>}.
 */
public final class ProvenanceBackfill {

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: ProvenanceBackfill <catalogPath>");
      System.exit(2);
    }
    Path catalogPath = Path.of(args[0]);

    int snapshots = 0;
    int dated = 0;
    try (CatalogStore catalog = CatalogStore.openFile(catalogPath.toString())) {
      catalog.initSchema(); // ensures the provenance columns exist (and defaults backend)
      for (CatalogStore.OntologyInfo o : catalog.listOntologies()) {
        for (CatalogStore.SnapshotInfo s : catalog.listSnapshots(o.acronym())) {
          snapshots++;
          String sourceDate = CatalogStore.SnapshotProvenance.sourceDateFromDeclaredVersion(s.declaredVersion());
          if (sourceDate != null) {
            catalog.setSnapshotProvenance(s.versionId(), s.acronym(), null, sourceDate);
            dated++;
          }
        }
      }
    }
    System.out.printf("provenance backfill done: %d snapshots, %d given a source_date from their "
        + "version string (backend defaulted to bioportal for all; submission_id left for ingest)%n",
        snapshots, dated);
  }

  private ProvenanceBackfill() {}
}
