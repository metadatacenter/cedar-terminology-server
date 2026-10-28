package org.metadatacenter.terms.store;

import java.util.Map;

/** Adapter-owned link templates, excluded from vocabulary identity. */
public record SourcePresentation(String sourceSystem, String authorityLabel,
                                 Map<String, LinkPattern> links, Map<String, LinkPattern> resourceLinks) {
  public static final String META_KEY = "resource_presentation_v1";
  /** Only the server expands placeholders; clients receive complete URLs. */
  public record LinkPattern(String landingPage, String icon) {}
}
