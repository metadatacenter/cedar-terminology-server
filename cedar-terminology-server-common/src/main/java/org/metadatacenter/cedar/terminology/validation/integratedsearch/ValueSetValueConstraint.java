package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Pattern;

import static org.metadatacenter.cedar.terminology.util.Constants.BP_VS_COLLECTIONS_READ_REGEX;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ValueSetValueConstraint {

  @NotEmpty
  private String uri;

  @NotEmpty
  @Pattern(regexp=BP_VS_COLLECTIONS_READ_REGEX) // Checks that the vsCollection is valid
  private String vsCollection;

  public ValueSetValueConstraint() { }

  public String getUri() {
    return uri;
  }

  public String getVsCollection() {
    return vsCollection;
  }

}
