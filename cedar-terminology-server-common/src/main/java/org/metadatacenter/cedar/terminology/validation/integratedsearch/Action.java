package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Pattern;

import static org.metadatacenter.cedar.terminology.util.Constants.CEDAR_VALUE_ARRANGEMENTS_ACTIONS_REGEX;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Action {

  private Integer to;
  @NotEmpty
  @Pattern(regexp=CEDAR_VALUE_ARRANGEMENTS_ACTIONS_REGEX) // Checks that the action is valid
  private String action;
  @NotEmpty
  private String termUri;

  public Action() { }

  public int getTo() {
    return to;
  }

  public String getAction() {
    return action;
  }

  public String getTermUri() {
    return termUri;
  }
}