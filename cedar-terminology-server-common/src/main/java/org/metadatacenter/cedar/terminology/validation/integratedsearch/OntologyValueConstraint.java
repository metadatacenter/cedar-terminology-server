package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.hibernate.validator.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OntologyValueConstraint {

  @NotEmpty
  private String acronym;
  // Optional pinned version (version_id or tag); absent means the current version.
  @JsonDeserialize(using = ConstraintVersionDeserializer.class)
  private String version;
  // Optional source system the terms come from; absent/blank means BioPortal. A non-BioPortal source is
  // served from the local store or not at all — it is never proxied to BioPortal.
  private String sourceSystem;

  public OntologyValueConstraint() { }

  public String getVersion() {
    return version;
  }

  public String getAcronym() {
    return acronym;
  }

  public String getSourceSystem() {
    return sourceSystem;
  }

}
