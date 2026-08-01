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

  public OntologyValueConstraint() { }

  public String getVersion() {
    return version;
  }

  public String getAcronym() {
    return acronym;
  }

}
