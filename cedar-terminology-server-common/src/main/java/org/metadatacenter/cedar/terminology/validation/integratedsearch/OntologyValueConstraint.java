package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OntologyValueConstraint {

  @NotEmpty
  private String acronym;
  // Optional pinned version (version_id or tag); absent means the current version.
  private String version;

  public OntologyValueConstraint() { }

  public String getVersion() {
    return version;
  }

  public String getAcronym() {
    return acronym;
  }

}
