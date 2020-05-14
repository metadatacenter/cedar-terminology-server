package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BranchValueConstraint {

  @NotEmpty
  private String uri;
  @NotEmpty
  private String acronym;

  public BranchValueConstraint() { }

  public String getUri() {
    return uri;
  }

  public String getAcronym() {
    return acronym;
  }

}