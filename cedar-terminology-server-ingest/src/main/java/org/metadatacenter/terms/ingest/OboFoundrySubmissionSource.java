package org.metadatacenter.terms.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * A {@link SubmissionSource} that fetches an ontology directly from its OBO Foundry PURL, the
 * community's canonical distribution — a different authority than BioPortal and often a different
 * serialization of the same release.
 *
 * Its reason to exist is the source-independence proof (VERSIONING-DESIGN §4.3, roadmap D2): identity
 * is the normalized content hash of the extracted model, so the <i>same release</i> pulled from OBO
 * Foundry and from BioPortal must produce the <i>same</i> {@code version_id} even though the bytes and
 * serialization differ. This source lets an ingest draw from OBO Foundry so that claim can be tested
 * against a real second authority, not asserted.
 *
 * <b>Release addressing.</b> OBO Foundry serves the current release at {@code
 * http://purl.obolibrary.org/obo/<lc>.owl} and every dated release at {@code
 * http://purl.obolibrary.org/obo/<lc>/releases/<date>/<lc>.owl}. Constructing the source with an
 * explicit release date targets that dated PURL, so a specific BioPortal submission can be matched
 * exactly; with no date it takes the current release. The PURL 302-redirects to the backing store
 * (usually GitHub raw), which the client follows.
 *
 * <b>Not BioPortal-shaped.</b> OBO Foundry exposes no submission-list or access API, so this source
 * reports a single synthetic submission and treats all content as public (OBO Foundry is open by
 * definition). {@code submissionId} is a constant — it is display-only provenance; identity does not
 * depend on it. This is the point at which the BioPortal-shaped {@link SubmissionSource} interface
 * shows its seams (roadmap D1: generalize the interface); D2 works within it.
 */
public class OboFoundrySubmissionSource implements SubmissionSource {

  private static final String PURL_BASE = "http://purl.obolibrary.org/obo/";

  /** Every OBO Foundry PURL fetch reports this synthetic submission id (display-only provenance). */
  static final int SYNTHETIC_SUBMISSION_ID = 1;

  private final String releaseDate; // an ISO date targeting a dated release PURL, or null for current
  private final HttpClient http;

  /** Fetches the current release of each requested ontology. */
  public OboFoundrySubmissionSource() {
    this(null);
  }

  /**
   * Fetches a specific dated release (e.g. {@code "2024-05-29"}) of each requested ontology, so it can
   * be matched byte-for-content against a BioPortal submission of the same release. A null or blank
   * date falls back to the current release.
   */
  public OboFoundrySubmissionSource(String releaseDate) {
    this.releaseDate = releaseDate == null || releaseDate.isBlank() ? null : releaseDate.trim();
    this.http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL) // the PURL redirects to the backing store
        .connectTimeout(Duration.ofSeconds(30))
        .build();
  }

  @Override
  public String backendId() {
    return "obofoundry";
  }

  /** OBO Foundry ontologies are open by definition; there is no per-ontology access API to consult. */
  @Override
  public OntologyAccess accessInfo(String acronym) {
    return new OntologyAccess("public", null);
  }

  /**
   * One synthetic submission: the release this source is pointed at (dated or current). OBO Foundry
   * has no submission-list API, so history cannot be enumerated here — a single release is served.
   */
  @Override
  public List<Submission> listSubmissions(String acronym) {
    return List.of(new Submission(SYNTHETIC_SUBMISSION_ID, releaseDate, releaseDate, "OWL"));
  }

  @Override
  public Submission latestSubmission(String acronym) {
    return listSubmissions(acronym).get(0);
  }

  @Override
  public Path download(String acronym, int submissionId, Path targetDir)
      throws IOException, InterruptedException {
    String url = downloadUrl(acronym);
    HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build();
    HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
    int status = resp.statusCode();
    if (status / 100 != 2) {
      throw new IOException("HTTP " + status + " downloading " + acronym + " from OBO Foundry (" + url + ")");
    }
    Files.createDirectories(targetDir);
    Path target = targetDir.resolve(acronym.toLowerCase(Locale.ROOT)
        + (releaseDate == null ? "" : "-" + releaseDate) + ".owl");
    try (InputStream in = resp.body()) {
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
    return target;
  }

  /**
   * The OBO Foundry download PURL for an ontology: the dated release PURL when a release date was
   * given, otherwise the current-release PURL. Package-visible so the addressing can be unit-tested
   * without the network.
   */
  String downloadUrl(String acronym) {
    String lc = acronym.toLowerCase(Locale.ROOT);
    return releaseDate == null
        ? PURL_BASE + lc + ".owl"
        : PURL_BASE + lc + "/releases/" + releaseDate + "/" + lc + ".owl";
  }
}
