package org.metadatacenter.terms.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.store.SourcePresentation;
import org.metadatacenter.terms.store.SnapshotStore;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;

/** Resolves presentation independently of selection and membership. Never substitutes a missing pin. */
public final class ResourceDescriptionService {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final CatalogSnapshotProvider provider;

  public ResourceDescriptionService(CatalogSnapshotProvider provider) { this.provider = provider; }

  public static SourcePresentation presentation(SnapshotStore store) throws SQLException {
    var encoded = store.getMeta(SourcePresentation.META_KEY);
    if (encoded.isEmpty()) return SourcePresentation.legacyBioPortal();
    try { return JSON.readValue(encoded.get(), SourcePresentation.class); }
    catch (java.io.IOException e) { throw new SQLException("Invalid stored resource presentation", e); }
  }

  public ResourceDescription describe(ResourceDescription.Reference requested, String language) throws SQLException {
    String kind = switch (requested.kind()) {
      case "class", "value" -> "term";
      case "ontology" -> "vocabulary";
      case "term", "branch", "valueSet", "vocabulary", "property" -> requested.kind();
      default -> throw new IllegalArgumentException("Unknown resource kind");
    };
    String acronym = requested.sourceAcronym();
    if (acronym == null || acronym.isBlank()) throw new IllegalArgumentException("sourceAcronym is required");
    String iri = requested.resourceIri();
    if (!kind.equals("vocabulary") && (iri == null || iri.isBlank()))
      throw new IllegalArgumentException("resourceIri is required");
    String version = requested.versionId();
    boolean pinned = version != null && !version.isBlank() && !"latest".equals(version);
    var store = provider == null ? Optional.<SnapshotStore>empty() : provider.forOntology(acronym, version);
    if (store.isEmpty()) {
      // Existing, unpinned BioPortal constraints can still link out while that source is proxied.
      // This is a description only, explicitly unverified; it does not assert membership or availability.
      if (!pinned && (requested.sourceSystem() == null || "bioportal".equals(requested.sourceSystem()))) {
        var profile = SourcePresentation.legacyBioPortal();
        return new ResourceDescription(requested, null, null, language, "unverified", "notHeldLocally", null,
            profile.authorityLabel(), links(profile, kind, acronym, iri, null), List.of());
      }
      return unavailable(requested, pinned ? "versionNotHeld" : "sourceNotServed");
    }
    SnapshotStore snapshot = store.get();
    SourcePresentation profile = presentation(snapshot);
    String sourceIri = provider.catalog().ontologyIri(acronym).orElse(null);
    if (requested.sourceSystem() != null && !requested.sourceSystem().equals(profile.sourceSystem()))
      return unavailable(requested, "sourceMismatch");
    if (requested.sourceIri() != null && !requested.sourceIri().equals(sourceIri))
      return unavailable(requested, "sourceMismatch");
    String resolvedVersion = provider.snapshotInfo(acronym, version).orElseThrow().versionId();
    var ref = new ResourceDescription.Reference(kind, profile.sourceSystem(), acronym, sourceIri,
        kind.equals("vocabulary") ? sourceIri : iri, resolvedVersion);
    String label, definition = null, replacement = null, servedLanguage = null;
    boolean obsolete = false;
    if (kind.equals("vocabulary")) {
      label = provider.catalog().ontologyName(acronym);
    } else if (kind.equals("property")) {
      if (!snapshot.properties().available()) return unavailable(ref, "propertyNotHeld");
      org.metadatacenter.terms.store.SnapshotProperties.Property property = null;
      for (String propertyKind : List.of("object", "datatype", "annotation")) {
        var found = snapshot.properties().property(iri, propertyKind);
        if (found.isPresent()) { property = found.get(); break; }
      }
      if (property == null) return unavailable(ref, "resourceNotHeld");
      label = property.label(); obsolete = property.obsolete();
    } else {
      var concept = snapshot.get(iri);
      if (concept.isEmpty()) return unavailable(ref, "resourceNotHeld");
      label = language == null ? concept.get().prefLabel()
          : snapshot.labelInLang(iri, language).orElse(concept.get().prefLabel());
      final String servedLabel = label;
      servedLanguage = snapshot.labels(iri).stream().filter(n -> n.value().equals(servedLabel) && !n.lang().isBlank())
          .map(SnapshotStore.LabelEntry::lang).findFirst().orElse(null);
      definition = SnapshotStore.servedDefinition(snapshot.definitions(iri));
      obsolete = concept.get().obsolete();
      replacement = snapshot.conceptMeta(iri).map(SnapshotStore.ConceptMeta::replacedBy).orElse(null);
    }
    return new ResourceDescription(ref, label, definition, servedLanguage, obsolete ? "deprecated" : "available", null,
        replacement, profile.authorityLabel(), links(profile, kind, acronym, iri, resolvedVersion),
        profile.capabilities() == null ? List.of() : profile.capabilities().getOrDefault(kind, List.of()));
  }

  private static ResourceDescription unavailable(ResourceDescription.Reference ref, String reason) {
    return new ResourceDescription(ref, null, null, null, "unavailable", reason, null, null, List.of(), List.of());
  }

  static List<ResourceDescription.Link> links(SourcePresentation profile, String kind, String acronym,
                                              String iri, String version) {
    List<SourcePresentation.LinkPattern> patterns = iri != null && profile.resourceLinks() != null && profile.resourceLinks().containsKey(iri)
        ? profile.resourceLinks().get(iri)
        : profile.links() == null ? List.of() : profile.links().getOrDefault(kind, List.of());
    List<ResourceDescription.Link> out = new ArrayList<>();
    for (var pattern : patterns) {
      if (pattern.href() == null || (!"current".equals(pattern.versionScope()) && !"exact".equals(pattern.versionScope()))) continue;
      if ("exact".equals(pattern.versionScope()) && version == null) continue;
      String href = pattern.href().replace("{sourceAcronym}", encode(acronym))
          .replace("{resourceIri}", encode(iri)).replace("{resourceIriRaw}", iri == null ? "" : iri)
          .replace("{versionId}", encode(version));
      try {
        URI uri = URI.create(href);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null || uri.getUserInfo() != null) continue;
        out.add(new ResourceDescription.Link(pattern.rel(), href, pattern.label(), pattern.versionScope(), pattern.icon()));
      } catch (IllegalArgumentException ignored) { /* No link is safer than a malformed or unresolved template. */ }
    }
    return List.copyOf(out);
  }

  private static String encode(String text) {
    return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
