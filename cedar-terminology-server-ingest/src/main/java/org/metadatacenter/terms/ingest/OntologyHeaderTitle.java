package org.metadatacenter.terms.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an ontology's self-declared title from its {@code owl:Ontology} header — the human-readable
 * name it asserts for itself, as Dublin Core {@code title} or, failing that, {@code rdfs:label}. This
 * is the display name the ontology picker shows; without it the catalog falls back to the acronym,
 * which reads as "DOID (DOID)".
 *
 * Complements {@link OntologyHeaderIri}. BioPortal hands a title over in its submission metadata, but a
 * direct-URL or OBO Foundry source does not, so the title has to come from the file itself. Read the
 * same cheap way — a bounded scan of the file head — and, crucially, <b>anchored to the
 * {@code owl:Ontology} node</b>: only title/label properties on the ontology itself count, so a class
 * label or a dataset description elsewhere in the document is never mistaken for the ontology's title.
 * A header that declares no title (a bare {@code <owl:Ontology/>}, or one carrying only project
 * metadata) yields empty, and the ingest keeps the acronym. Best-effort and serialization-aware across
 * the RDF/XML and Turtle/N-Triples forms these sources use.
 */
public final class OntologyHeaderTitle {

  /** Bytes of the file head to scan — the header sits at the top, and this bounds cost on a huge file. */
  private static final int HEAD_BYTES = 2 * 1024 * 1024;
  /** Cap on the Turtle ontology-subject block, so a giant single-line document cannot pull in the
   *  class statements that follow the header. */
  private static final int TURTLE_BLOCK_MAX = 8192;

  // Locate the owl:Ontology node so the title search can be anchored to it (mirrors OntologyHeaderIri).
  private static final Pattern RDFXML_OPEN = Pattern.compile("<owl:Ontology\\b[^>]*>", Pattern.DOTALL);
  private static final Pattern RDFXML_CLOSE = Pattern.compile("</owl:Ontology\\s*>");
  private static final Pattern TURTLE_SUBJECT =
      Pattern.compile("(?:<[^>\\s]*>|<>|\\S+)\\s+(?:a|rdf:type)\\s+owl:Ontology\\b");
  // Turtle statement terminator: a '.' set off by whitespace (not a '.' inside an IRI or a number).
  private static final Pattern TURTLE_END = Pattern.compile("\\s\\.(?:\\s|$)");

  // Title/label as RDF/XML elements — any prefix, matched by local name; title preferred over label.
  // Group 1 captures the attributes (to read xml:lang), group 2 the element content.
  private static final Pattern XML_TITLE =
      Pattern.compile("<(?:[\\w.-]+:)?title\\b([^>]*)>(.*?)</(?:[\\w.-]+:)?title>", Pattern.DOTALL);
  private static final Pattern XML_LABEL =
      Pattern.compile("<(?:[\\w.-]+:)?label\\b([^>]*)>(.*?)</(?:[\\w.-]+:)?label>", Pattern.DOTALL);
  private static final Pattern XML_LANG = Pattern.compile("xml:lang\\s*=\\s*[\"']([\\w-]+)[\"']");
  // Title/label as Turtle literals, in priority order (Dublin Core title, then rdfs:label / schema:name).
  private static final Pattern[] TTL_TITLE = {
      ttlLiteral("(?:dcterms|dct|dc|terms):title"),
      ttlLiteral("<http://purl\\.org/dc/terms/title>"),
      ttlLiteral("<http://purl\\.org/dc/elements/1\\.1/title>"),
  };
  private static final Pattern[] TTL_LABEL = {
      ttlLiteral("rdfs:label"),
      ttlLiteral("<http://www\\.w3\\.org/2000/01/rdf-schema#label>"),
      ttlLiteral("schema:name"),
  };

  private static Pattern ttlLiteral(String predicate) {
    // predicate "value"(@lang)? — group 1 is the literal, group 2 the optional language tag.
    return Pattern.compile(predicate + "\\s+\"((?:[^\"\\\\]|\\\\.)*)\"(?:@([\\w-]+))?", Pattern.DOTALL);
  }

  /** The title declared on the {@code owl:Ontology} header of {@code file}, or empty if none. */
  public static Optional<String> fromFile(Path file) throws IOException {
    String block = ontologyBlock(readHead(file));
    if (block == null || block.isBlank()) {
      return Optional.empty();
    }
    return firstXml(block, XML_TITLE)
        .or(() -> firstTurtle(block, TTL_TITLE))
        .or(() -> firstXml(block, XML_LABEL))
        .or(() -> firstTurtle(block, TTL_LABEL));
  }

  /** The region belonging to the {@code owl:Ontology} node: the RDF/XML element body, or the Turtle
   *  subject's statement. A self-closing {@code <owl:Ontology/>} has no body, so returns empty. */
  private static String ontologyBlock(String head) {
    Matcher open = RDFXML_OPEN.matcher(head);
    if (open.find()) {
      Matcher close = RDFXML_CLOSE.matcher(head);
      return close.find(open.end()) ? head.substring(open.end(), close.start()) : "";
    }
    Matcher subj = TURTLE_SUBJECT.matcher(head);
    if (subj.find()) {
      Matcher end = TURTLE_END.matcher(head);
      int to = end.find(subj.end()) ? end.start() : head.length();
      return head.substring(subj.start(), Math.min(to, subj.start() + TURTLE_BLOCK_MAX));
    }
    return null;
  }

  /** First usable title/label element, preferring English or an untagged {@code xml:lang}. */
  private static Optional<String> firstXml(String block, Pattern p) {
    Matcher m = p.matcher(block);
    String otherLang = null;
    while (m.find()) {
      String v = clean(m.group(2));
      if (v.isBlank()) {
        continue;
      }
      Matcher lang = XML_LANG.matcher(m.group(1));
      boolean english = !lang.find() || lang.group(1).toLowerCase().startsWith("en");
      if (english) {
        return Optional.of(v);
      }
      if (otherLang == null) {
        otherLang = v;
      }
    }
    return Optional.ofNullable(otherLang);
  }

  /** First usable literal for the highest-priority predicate present, preferring English/untagged. */
  private static Optional<String> firstTurtle(String block, Pattern[] patterns) {
    for (Pattern p : patterns) {
      Matcher m = p.matcher(block);
      String otherLang = null;
      while (m.find()) {
        String v = clean(unescape(m.group(1)));
        if (v.isBlank()) {
          continue;
        }
        String lang = m.group(2);
        if (lang == null || lang.toLowerCase().startsWith("en")) {
          return Optional.of(v);
        }
        if (otherLang == null) {
          otherLang = v;
        }
      }
      if (otherLang != null) {
        return Optional.of(otherLang);
      }
    }
    return Optional.empty();
  }

  private static String unescape(String s) {
    return s.replace("\\\"", "\"").replace("\\n", " ").replace("\\t", " ").replace("\\\\", "\\");
  }

  /** Strip stray inline markup and the common XML entities, then collapse whitespace. */
  private static String clean(String s) {
    String v = s.replaceAll("<[^>]+>", " ")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'");
    return v.replaceAll("\\s+", " ").trim();
  }

  private static String readHead(Path file) throws IOException {
    byte[] buf = new byte[HEAD_BYTES];
    int total = 0;
    try (InputStream in = Files.newInputStream(file)) {
      int n;
      while (total < buf.length && (n = in.read(buf, total, buf.length - total)) > 0) {
        total += n;
      }
    }
    return new String(buf, 0, total, StandardCharsets.UTF_8);
  }

  private OntologyHeaderTitle() {
  }
}
