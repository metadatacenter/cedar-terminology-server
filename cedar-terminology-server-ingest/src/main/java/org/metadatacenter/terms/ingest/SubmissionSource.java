package org.metadatacenter.terms.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A source of ontology submissions and their raw files. Implemented by {@link BioPortalDownloader}
 * against the BioPortal REST API; abstracted so ingestion can be driven from a stub in tests.
 */
public interface SubmissionSource {

  /** Access metadata (viewing restriction / license) for an ontology, for the licensing guard. */
  OntologyAccess accessInfo(String acronym) throws IOException, InterruptedException;

  /** All submissions (versions) for an ontology, in the source's order. */
  List<Submission> listSubmissions(String acronym) throws IOException, InterruptedException;

  /** The latest submission (highest submission id) for an ontology. */
  Submission latestSubmission(String acronym) throws IOException, InterruptedException;

  /** Downloads a submission's raw file into {@code targetDir} and returns its path. */
  Path download(String acronym, int submissionId, Path targetDir) throws IOException, InterruptedException;
}
