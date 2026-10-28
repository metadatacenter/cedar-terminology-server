package org.metadatacenter.terms.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.store.SourcePresentation;
import org.metadatacenter.terms.store.SnapshotStore;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

/** Public presentation from CEDAR constraint identity. Never substitutes a missing pin. */
public final class ResourceDescriptionService {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final CatalogSnapshotProvider provider;

  public ResourceDescriptionService(CatalogSnapshotProvider provider) { this.provider = provider; }

  public static SourcePresentation presentation(SnapshotStore store) throws SQLException {
    var encoded = store.getMeta(SourcePresentation.META_KEY);
    if (encoded.isEmpty()) return SourcePresentation.legacyBioPortal();
    try {
      ObjectNode node = (ObjectNode) JSON.readTree(encoded.get());
      node.remove("capabilities"); // v1 snapshot compatibility, never exposed as presentation
      return JSON.treeToValue(node, SourcePresentation.class);
    } catch (java.io.IOException | ClassCastException e) {
      throw new SQLException("Invalid stored resource presentation", e);
    }
  }

  public ResourceDescription describe(ResourceDescription.Reference requested) throws SQLException {
    String type = requested.type();
    if (type == null || !Set.of("class", "branch", "ontology", "valueSet", "property").contains(type))
      throw new IllegalArgumentException("Unknown constraint type");
    String acronym = requested.sourceAcronym();
    if (acronym == null || acronym.isBlank()) throw new IllegalArgumentException("sourceAcronym is required");
    String iri = switch (type) {
      case "ontology" -> requested.sourceIri();
      case "branch", "valueSet" -> requested.termBaseIri();
      default -> requested.termIri();
    };
    if (!type.equals("ontology") && (iri == null || iri.isBlank()))
      throw new IllegalArgumentException("The constraint needs its termIri or termBaseIri");
    String version = requested.version() == null ? null : requested.version().id();
    boolean pinned = version != null && !version.isBlank() && !"latest".equalsIgnoreCase(version.trim());
    var store = provider == null ? Optional.<SnapshotStore>empty() : provider.forOntology(acronym, version);
    if (store.isEmpty()) {
      // Existing unpinned, proxied BioPortal references keep their public links. This describes
      // presentation only; it never asserts membership or remote availability.
      if (!pinned && (requested.sourceSystem() == null || "bioportal".equals(requested.sourceSystem())))
        return new ResourceDescription(requested, landingPage(SourcePresentation.legacyBioPortal(), type, acronym, iri, null));
      throw new NotFoundException(pinned ? "The requested version is not held" : "The source is not served");
    }
    SnapshotStore snapshot = store.get();
    SourcePresentation profile = presentation(snapshot);
    String sourceIri = provider.catalog().ontologyIri(acronym).orElse(null);
    if (requested.sourceSystem() != null && !requested.sourceSystem().equals(profile.sourceSystem()))
      throw new NotFoundException("The source identity does not match");
    if (requested.sourceIri() != null && !requested.sourceIri().equals(sourceIri))
      throw new NotFoundException("The source identity does not match");
    var info = provider.snapshotInfo(acronym, version).orElseThrow();
    var resolved = new SearchResponse.VersionInfo(info.versionId(), info.releasedAt(), info.declaredVersion());
    var constraint = new ResourceDescription.Reference(type, profile.sourceSystem(), acronym, sourceIri,
        requested.termIri(), requested.termBaseIri(), resolved);
    if (type.equals("property")) {
      boolean found = false;
      if (snapshot.properties().available()) {
        for (String propertyKind : List.of("object", "datatype", "annotation"))
          if (snapshot.properties().property(iri, propertyKind).isPresent()) { found = true; break; }
      }
      if (!found) throw new NotFoundException("The property is not held in this version");
    } else if (!type.equals("ontology") && snapshot.get(iri).isEmpty()) {
      throw new NotFoundException("The resource is not held in this version");
    }
    return new ResourceDescription(constraint, landingPage(profile, type, acronym, iri, info.versionId()));
  }

  /** Optional enrichment must not turn an otherwise usable search result into a failed search. */
  public ResourceDescription.Presentation presentationFor(ResourceDescription.Reference reference) throws SQLException {
    try { return describe(reference).presentation(); }
    catch (NotFoundException e) { return ResourceDescription.Presentation.empty(); }
  }

  public static final class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
  }

  static ResourceDescription.Presentation landingPage(SourcePresentation profile, String type, String acronym,
                                                       String iri, String version) {
    // Old snapshot profiles are read at this boundary. Public references and new profiles use
    // CEDAR's class/ontology names; no second set of type aliases crosses the API.
    String legacyType = "class".equals(type) ? "term" : "ontology".equals(type) ? "vocabulary" : type;
    var defaults = profile.links() == null ? List.<SourcePresentation.LinkPattern>of()
        : profile.links().getOrDefault(type, profile.links().getOrDefault(legacyType, List.of()));
    var patterns = iri != null && profile.resourceLinks() != null && profile.resourceLinks().containsKey(iri)
        ? profile.resourceLinks().get(iri) : defaults;
    for (var pattern : patterns) {
      if (!"landing".equals(pattern.rel()) || pattern.href() == null || pattern.label() == null || pattern.label().isBlank()
          || (!"current".equals(pattern.versionScope()) && !"exact".equals(pattern.versionScope()))) continue;
      if ("exact".equals(pattern.versionScope()) && version == null) continue;
      String href = pattern.href().replace("{sourceAcronym}", encode(acronym))
          .replace("{sourceIri}", "ontology".equals(type) ? encode(iri) : "")
          .replace("{termIri}", encode(iri)).replace("{termBaseIri}", encode(iri))
          .replace("{termIriRaw}", iri == null ? "" : iri).replace("{version.id}", encode(version))
          .replace("{resourceIri}", encode(iri)).replace("{resourceIriRaw}", iri == null ? "" : iri)
          .replace("{versionId}", encode(version));
      try {
        URI uri = URI.create(href);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null || uri.getUserInfo() != null) continue;
        String icon = Set.of("bioportal", "external").contains(Objects.toString(pattern.icon(), "")) ? pattern.icon() : null;
        return new ResourceDescription.Presentation(uri.toASCIIString(), pattern.label(), pattern.versionScope(), icon);
      } catch (IllegalArgumentException ignored) { /* A malformed template produces no page. */ }
    }
    return ResourceDescription.Presentation.empty();
  }

  private static String encode(String text) {
    return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
