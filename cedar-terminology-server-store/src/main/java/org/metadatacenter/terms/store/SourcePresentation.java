package org.metadatacenter.terms.store;

import java.util.List;
import java.util.Map;

/** Adapter-owned public presentation metadata. It is not part of a resource's semantic identity. */
public record SourcePresentation(String sourceSystem, String authorityLabel,
                                 Map<String, List<LinkPattern>> links,
                                 Map<String, List<LinkPattern>> resourceLinks) {
  public static final String META_KEY = "resource_presentation_v1";

  /** Templates are expanded only by the server; browsers receive complete, validated URLs. */
  public record LinkPattern(String rel, String href, String label, String versionScope, String icon) {
    public LinkPattern(String rel, String href, String label, String versionScope) { this(rel, href, label, versionScope, null); }
  }

  /** Compatibility profile for snapshots created before adapters recorded presentation metadata. */
  public static SourcePresentation legacyBioPortal() {
    String base = "https://bioportal.bioontology.org/ontologies/{sourceAcronym}";
    var overview = List.of(new LinkPattern("landing", base, "BioPortal", "current", "bioportal"));
    var concept = List.of(new LinkPattern("landing", base + "?p=classes&conceptid={termIri}", "BioPortal", "current", "bioportal"));
    return new SourcePresentation("bioportal", "BioPortal",
        Map.of("ontology", overview, "valueSet", overview, "class", concept, "branch", concept,
            "property", List.of()),
        Map.of());
  }
}
