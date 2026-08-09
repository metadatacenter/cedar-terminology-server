package org.metadatacenter.terms.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an ontology's self-declared IRI from its {@code owl:Ontology} header — the identity the
 * ontology asserts for itself, independent of what namespace its class IRIs happen to use.
 *
 * This restores a clean identity for the cases the class-namespace derivation cannot: an ontology
 * whose terms sit under a file/host base (NCIT on {@code Thesaurus.owl}) or under a namespace it only
 * imports, which de-confliction leaves acronym-only. The header names the ontology directly.
 *
 * Read by a scan of the file head rather than a full parse: the {@code owl:Ontology} declaration sits
 * near the top (after the prefix/namespace block), so scanning the first couple of megabytes finds it
 * without loading an 800k-class ontology into memory just to read one line. Serialization-aware across
 * the forms BioPortal serves — RDF/XML, OWL/XML, Turtle, N-Triples. Best-effort: an ontology whose
 * header this does not recognize (or an anonymous ontology with none) simply yields empty, exactly as
 * it is today.
 */
public final class OntologyHeaderIri {

  /** Bytes of the file head to scan. The header sits after the prefix block; a couple of MB is ample
   *  even for a heavily-namespaced document, and bounds the cost on a huge ontology. */
  private static final int HEAD_BYTES = 2 * 1024 * 1024;

  // RDF/XML: <owl:Ontology ... rdf:about="IRI"> (attribute order and whitespace vary; may span lines).
  private static final Pattern RDFXML =
      Pattern.compile("<owl:Ontology\\b[^>]*?\\brdf:about\\s*=\\s*([\"'])(.*?)\\1", Pattern.DOTALL);
  // OWL/XML: <Ontology ... ontologyIRI="IRI">.
  private static final Pattern OWLXML =
      Pattern.compile("<Ontology\\b[^>]*?\\bontologyIRI\\s*=\\s*([\"'])(.*?)\\1", Pattern.DOTALL);
  // Turtle / N-Triples: <IRI> a owl:Ontology  |  <IRI> rdf:type owl:Ontology  |  <IRI> <…#type> <…owl#Ontology>.
  private static final Pattern TURTLE = Pattern.compile(
      "<([^>\\s]+)>\\s+(?:a|rdf:type|<[^>]*#type>)\\s+(?:owl:Ontology|<http://www\\.w3\\.org/2002/07/owl#Ontology>)");
  // Turtle with a @base and an empty-subject ontology node: @base <IRI> … <> a owl:Ontology.
  private static final Pattern TURTLE_BASE =
      Pattern.compile("@base\\s+<([^>\\s]+)>", Pattern.CASE_INSENSITIVE);
  private static final Pattern TURTLE_EMPTY_SUBJECT =
      Pattern.compile("(?:^|\\s)<>\\s+(?:a|rdf:type)\\s+owl:Ontology");
  // xml:base on the document/root, the identity when the owl:Ontology carries rdf:about="".
  private static final Pattern XML_BASE =
      Pattern.compile("xml:base\\s*=\\s*([\"'])(.*?)\\1");
  // A DOCTYPE entity declaration and a reference to one, so an IRI written &obo;foo expands to its full
  // form (OBO/OWL files routinely define <!ENTITY obo "http://purl.obolibrary.org/obo/">).
  private static final Pattern ENTITY_DECL = Pattern.compile("<!ENTITY\\s+(\\w+)\\s+\"([^\"]*)\"\\s*>");
  private static final Pattern ENTITY_REF = Pattern.compile("&(\\w+);");

  /** The declared {@code owl:Ontology} IRI in {@code file}, or empty if none is found. */
  public static Optional<String> fromFile(Path file) throws IOException {
    String head = readHead(file);
    Matcher m = RDFXML.matcher(head);
    if (m.find()) {
      String about = m.group(2);
      if (about != null && !about.isBlank()) {
        return finish(about, head);
      }
      // rdf:about="" — the ontology names itself by the document's xml:base.
      Matcher base = XML_BASE.matcher(head);
      if (base.find()) {
        return finish(base.group(2), head);
      }
    }
    m = OWLXML.matcher(head);
    if (m.find()) {
      return finish(m.group(2), head);
    }
    m = TURTLE.matcher(head);
    if (m.find()) {
      return finish(m.group(1), head);
    }
    if (TURTLE_EMPTY_SUBJECT.matcher(head).find()) {
      Matcher base = TURTLE_BASE.matcher(head);
      if (base.find()) {
        return finish(base.group(1), head);
      }
    }
    return Optional.empty();
  }

  /** Normalizes a candidate IRI: expand DOCTYPE entity references, then reject if it is blank, a
   *  blank node, an editor-default placeholder, or still carries an undefined entity reference. */
  private static Optional<String> finish(String iri, String head) {
    if (iri == null || iri.isBlank()) {
      return Optional.empty();
    }
    String expanded = expandEntities(iri.trim(), head);
    if (expanded.isBlank() || expanded.startsWith("_:") || PLACEHOLDERS.contains(expanded)
        || ENTITY_REF.matcher(expanded).find()) { // an unresolved &entity; is not a usable IRI
      return Optional.empty();
    }
    return Optional.of(expanded);
  }

  private static String expandEntities(String iri, String head) {
    if (iri.indexOf('&') < 0) {
      return iri;
    }
    java.util.Map<String, String> entities = new java.util.HashMap<>();
    Matcher d = ENTITY_DECL.matcher(head);
    while (d.find()) {
      entities.put(d.group(1), d.group(2));
    }
    Matcher r = ENTITY_REF.matcher(iri);
    StringBuilder sb = new StringBuilder();
    while (r.find()) {
      String value = entities.get(r.group(1));
      r.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : r.group(0)));
    }
    r.appendTail(sb);
    return sb.toString();
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

  /** Editor-default header IRIs that name no ontology in particular — a fresh Protégé document's
   *  {@code unnamed.owl}/{@code ont.owl}. Shared placeholders de-confliction would reject anyway, but
   *  rejecting them here keeps one from becoming a false identity when only a single ontology carries it. */
  private static final Set<String> PLACEHOLDERS = Set.of(
      "http://www.owl-ontologies.com/unnamed.owl",
      "http://www.co-ode.org/ontologies/ont.owl",
      "http://www.semanticweb.org/ontologies/Ontology.owl");

  private OntologyHeaderIri() { }
}
