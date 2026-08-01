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
 * A {@link SubmissionSource} that downloads an ontology from an arbitrary URL — any repository or host
 * that serves a downloadable ontology file: EBI OLS {@code fileLocation}s, OntoPortal instances
 * (AgroPortal, EcoPortal, …), Linked Open Vocabularies, a W3C or community vocabulary, a GitHub raw
 * release, a SKOS thesaurus dump, and so on. It generalizes {@link OboFoundrySubmissionSource} (which
 * hardcodes the OBO PURL pattern) to any URL, so ingestion is not tied to BioPortal or OBO Foundry
 * (roadmap: ingest ontologies from more sources).
 *
 * Like the OBO Foundry source it is not BioPortal-shaped: a URL names one release, so it reports a
 * single synthetic submission and treats the content as public. Identity is unaffected — the
 * {@code version_id} is the normalized content hash, so the same release fetched from a different host
 * (or in a different serialization) merges with an existing snapshot rather than duplicating it.
 *
 * The {@code format} hint is the ingest format passed to the extractor: {@code OWL} (default; the
 * OWLAPI extractor auto-detects RDF/XML, OBO, Turtle, OWL/XML, and functional syntax from the file
 * content) or {@code SKOS} (the SKOS-relations extractor). The downloaded file keeps the URL's
 * extension so downstream {@code .gz}/{@code .zip} decompression behaves as for any other source.
 */
public class DirectUrlSubmissionSource implements SubmissionSource {

  /** Every direct-URL fetch reports this synthetic submission id (display-only provenance). */
  static final int SYNTHETIC_SUBMISSION_ID = 1;

  private final String url;
  private final String format;
  private final String backendId;
  private final HttpClient http;

  /** Fetches {@code url} as an {@code OWL}-family file, recording the backend as {@code "url"}. */
  public DirectUrlSubmissionSource(String url) {
    this(url, "OWL", "url");
  }

  /**
   * @param url       where to download the ontology; redirects are followed
   * @param format    the ingest format hint ({@code OWL} default, or {@code SKOS}); selects the extractor
   * @param backendId the authority label recorded on the snapshot as audit provenance (e.g. {@code ols},
   *                  {@code agroportal}, {@code lov}); does not affect identity
   */
  public DirectUrlSubmissionSource(String url, String format, String backendId) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("DirectUrlSubmissionSource requires a non-blank url");
    }
    this.url = url.trim();
    this.format = format == null || format.isBlank() ? "OWL" : format.trim();
    this.backendId = backendId == null || backendId.isBlank() ? "url" : backendId.trim();
    this.http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();
  }

  @Override
  public String backendId() {
    return backendId;
  }

  /** An arbitrary URL carries no access API; the caller vouches the content is redistributable. */
  @Override
  public OntologyAccess accessInfo(String acronym) {
    return new OntologyAccess("public", null);
  }

  /** One synthetic submission: the single release this URL points at. */
  @Override
  public List<Submission> listSubmissions(String acronym) {
    return List.of(new Submission(SYNTHETIC_SUBMISSION_ID, null, null, format));
  }

  @Override
  public Submission latestSubmission(String acronym) {
    return listSubmissions(acronym).get(0);
  }

  @Override
  public Path download(String acronym, int submissionId, Path targetDir)
      throws IOException, InterruptedException {
    HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build();
    HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
    int status = resp.statusCode();
    if (status / 100 != 2) {
      throw new IOException("HTTP " + status + " downloading " + acronym + " from " + url);
    }
    Files.createDirectories(targetDir);
    Path target = targetDir.resolve(acronym.toLowerCase(Locale.ROOT) + fileSuffix());
    try (InputStream in = resp.body()) {
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
    return target;
  }

  /**
   * The download filename suffix, preserving the URL's serialization/compression extension (e.g.
   * {@code .owl}, {@code .obo}, {@code .ttl}, {@code .owl.gz}) so the ingest's decompression and format
   * detection behave the same as for a BioPortal or OBO Foundry download. Falls back to {@code .owl}
   * when the URL has no file extension (OWLAPI detects the real serialization from the content anyway).
   */
  String fileSuffix() {
    String path = URI.create(url).getPath();
    String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
    int dot = name.indexOf('.');
    return dot >= 0 ? name.substring(dot) : ".owl";
  }
}
