package org.metadatacenter.terms.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.metadatacenter.artifacts.model.core.fields.constraints.ConstraintLinks;
import io.swagger.v3.oas.annotations.media.Schema;

/** CEDAR constraint identity plus provider-independent public links. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ResourceDescription(Reference constraint, ConstraintLinks links) {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "ConstraintReference", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
  public record Reference(
      @Schema(allowableValues = {"class", "branch", "ontology", "valueSet", "property"}) String type,
      String sourceSystem, String sourceAcronym, String sourceIri,
      String termIri, String termBaseIri, SearchResponse.VersionInfo version) {}

}
