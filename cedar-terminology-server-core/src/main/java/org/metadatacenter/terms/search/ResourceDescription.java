package org.metadatacenter.terms.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** CEDAR constraint identity plus separate, provider-independent presentation. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ResourceDescription(Reference constraint, Presentation presentation) {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "ConstraintReference", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Reference(
      @Schema(allowableValues = {"class", "branch", "ontology", "valueSet", "property"}) String type,
      String sourceSystem, String sourceAcronym, String sourceIri,
      String termIri, String termBaseIri, SearchResponse.VersionInfo version) {}

  /** One landing page, or an empty object. No provider-specific properties. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "TerminologyPresentation", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Presentation(String href, String label,
      @Schema(allowableValues = {"current", "exact"}) String versionScope,
      @Schema(allowableValues = {"bioportal", "external"}) String icon) {
    public static Presentation empty() { return new Presentation(null, null, null, null); }
  }
}
