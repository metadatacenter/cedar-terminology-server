package org.metadatacenter.terms.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** The same public description for terms, branches, sets, properties and whole vocabularies. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceDescription(Reference ref, String label, String description, String language,
                                  String status, String reason, String replacedBy, String authorityLabel,
                                  List<Link> links, List<String> capabilities) {
  public record Reference(String kind, String sourceSystem, String sourceAcronym, String sourceIri,
                          String resourceIri, String versionId) {}
  public record Link(String rel, String href, String label, String versionScope, String icon) {}
}
