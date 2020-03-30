package org.metadatacenter.cedar.terminology.utils.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VCOntology {

  @NotEmpty
  private String uri;
  @NotEmpty
  private String acronym;

  public VCOntology() { }

  public String getUri() {
    return uri;
  }

  public String getAcronym() {
    return acronym;
  }

}
