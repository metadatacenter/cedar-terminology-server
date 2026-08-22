package org.metadatacenter.terms.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Downloads ontology submissions from the BioPortal REST API.
 *
 * The API key is sent in the {@code Authorization} header ({@code apikey token=...}), never in the
 * URL, so it does not leak into logs or query strings. Submission metadata is fetched as JSON;
 * the raw ontology file for a submission is streamed to disk (custody of the source of truth), and
 * the caller hashes it to derive a reproducible version id.
 */
public class BioPortalDownloader implements SubmissionSource {

  private static final String DEFAULT_BASE_URL = "https://data.bioontology.org";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final String apiKey;
  private final String baseUrl;
  private final HttpClient http;

  public BioPortalDownloader(String apiKey) {
    this(apiKey, DEFAULT_BASE_URL);
  }

  public BioPortalDownloader(String apiKey, String baseUrl) {
    this.apiKey = apiKey;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();
  }

  private HttpRequest.Builder request(String url) {
    return HttpRequest.newBuilder(URI.create(url)).header("Authorization", "apikey token=" + apiKey);
  }

  @Override
  public OntologyAccess accessInfo(String acronym) throws IOException, InterruptedException {
    String url = baseUrl + "/ontologies/" + acronym + "?display=viewingRestriction,hasLicense,name";
    HttpRequest req = request(url).timeout(Duration.ofSeconds(60)).GET().build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() / 100 != 2) {
      throw new IOException("HTTP " + resp.statusCode() + " fetching access info for " + acronym);
    }
    JsonNode n = MAPPER.readTree(resp.body());
    return new OntologyAccess(textOrNull(n, "viewingRestriction"), textOrNull(n, "hasLicense"),
        textOrNull(n, "name"));
  }

  @Override
  public List<Submission> listSubmissions(String acronym) throws IOException, InterruptedException {
    String url = baseUrl + "/ontologies/" + acronym
        + "/submissions?display=submissionId,version,released,hasOntologyLanguage";
    HttpRequest req = request(url).timeout(Duration.ofSeconds(60)).GET().build();
    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() / 100 != 2) {
      throw new IOException("HTTP " + resp.statusCode() + " listing submissions for " + acronym);
    }
    return parseSubmissions(resp.body());
  }

  @Override
  public Submission latestSubmission(String acronym) throws IOException, InterruptedException {
    return listSubmissions(acronym).stream()
        .max(Comparator.comparingInt(Submission::submissionId))
        .orElseThrow(() -> new IOException("No submissions for ontology " + acronym));
  }

  @Override
  public Path download(String acronym, int submissionId, Path targetDir)
      throws IOException, InterruptedException {
    String url = baseUrl + "/ontologies/" + acronym + "/submissions/" + submissionId + "/download";
    HttpRequest req = request(url).GET().build();
    HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
    int status = resp.statusCode();
    if (status == 403) {
      throw new IOException("Download not permitted for " + acronym
          + " submission " + submissionId + " (licensed ontology?)");
    }
    if (status / 100 != 2) {
      throw new IOException("HTTP " + status + " downloading " + acronym + " submission " + submissionId);
    }
    String filename = resp.headers().firstValue("Content-Disposition")
        .map(BioPortalDownloader::filenameFrom)
        .filter(f -> !f.isBlank())
        .orElse(acronym + "_sub" + submissionId);
    Files.createDirectories(targetDir);
    Path target = targetDir.resolve(filename);
    try (InputStream in = resp.body()) {
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }
    return target;
  }

  /** Parses a BioPortal submissions JSON array into {@link Submission} records. Package-visible for tests. */
  static List<Submission> parseSubmissions(String json) throws IOException {
    JsonNode root = MAPPER.readTree(json);
    List<Submission> out = new ArrayList<>();
    if (root.isArray()) {
      for (JsonNode n : root) {
        out.add(new Submission(
            n.path("submissionId").asInt(),
            textOrNull(n, "version"),
            textOrNull(n, "released"),
            textOrNull(n, "hasOntologyLanguage")));
      }
    }
    return out;
  }

  private static String textOrNull(JsonNode n, String field) {
    JsonNode v = n.get(field);
    return v == null || v.isNull() ? null : v.asText();
  }

  private static String filenameFrom(String contentDisposition) {
    for (String part : contentDisposition.split(";")) {
      String p = part.trim();
      if (p.startsWith("filename=")) {
        return p.substring("filename=".length()).replace("\"", "").trim();
      }
    }
    return "";
  }
}
