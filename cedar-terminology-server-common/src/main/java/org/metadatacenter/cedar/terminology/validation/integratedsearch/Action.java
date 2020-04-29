package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Pattern;

import static org.metadatacenter.cedar.terminology.util.Constants.CEDAR_VALUE_ARRANGEMENTS_ACTIONS_REGEX;

/**
 * See the validation schema at cedar-model-validation-library/schema/valueConstraintsActionsFieldItemContent.json
 * for a reference on how the original schema must look like
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Action {

  private Integer to; // Optional
  @NotEmpty
  @Pattern(regexp = CEDAR_VALUE_ARRANGEMENTS_ACTIONS_REGEX) // Checks that the action is valid
  private String action;
  @NotEmpty
  private String termUri;
  @NotEmpty
  private String type;
  @NotEmpty
  private String source;
  private String sourceUri; // Optional

  public Action() {
  }

  public Integer getTo() {
    return to;
  }

  public String getAction() {
    return action;
  }

  public String getTermUri() {
    return termUri;
  }

  public String getType() {
    return type;
  }

  public String getSource() {
    return source;
  }

  public String getSourceUri() {
    return sourceUri;
  }
}