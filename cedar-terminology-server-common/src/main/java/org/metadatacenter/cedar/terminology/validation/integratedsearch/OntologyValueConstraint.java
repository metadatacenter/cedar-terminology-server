package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OntologyValueConstraint {

  @NotEmpty
  private String acronym;

  public OntologyValueConstraint() { }

  public String getAcronym() {
    return acronym;
  }

}
