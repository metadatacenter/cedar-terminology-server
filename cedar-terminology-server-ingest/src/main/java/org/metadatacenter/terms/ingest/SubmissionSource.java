package org.metadatacenter.terms.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A source of ontology submissions and their raw files. Implemented by {@link BioPortalDownloader}
 * against the BioPortal REST API; abstracted so ingestion can be driven from a stub in tests.
 */
public interface SubmissionSource {

  /**
   * The backend this source ingests from, recorded on each snapshot as audit provenance (the
   * {@code snapshot.backend} column). BioPortal is the default; a second backend overrides it so the
   * catalog records which authority a snapshot's bytes came from. It does not affect identity — the
   * {@code version_id} is the normalized content hash, source-independent by design.
   */
  default String backendId() {
    return "bioportal";
  }

  /** Access metadata (viewing restriction / license) for an ontology, for the licensing guard. */
  OntologyAccess accessInfo(String acronym) throws IOException, InterruptedException;

  /** All submissions (versions) for an ontology, in the source's order. */
  List<Submission> listSubmissions(String acronym) throws IOException, InterruptedException;

  /** The latest submission (highest submission id) for an ontology. */
  Submission latestSubmission(String acronym) throws IOException, InterruptedException;

  /** Downloads a submission's raw file into {@code targetDir} and returns its path. */
  Path download(String acronym, int submissionId, Path targetDir) throws IOException, InterruptedException;
}
