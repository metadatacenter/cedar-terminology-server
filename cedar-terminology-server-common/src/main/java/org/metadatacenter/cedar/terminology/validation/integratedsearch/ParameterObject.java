package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class ParameterObject {

  @Valid
  @NotNull
  private ValueConstraints valueConstraints;

  private String inputText; // Note that inputText can be empty or null

  public ParameterObject() { }

  public ValueConstraints getValueConstraints() {
    return valueConstraints;
  }

  public String getInputText() {
    return inputText;
  }
}
