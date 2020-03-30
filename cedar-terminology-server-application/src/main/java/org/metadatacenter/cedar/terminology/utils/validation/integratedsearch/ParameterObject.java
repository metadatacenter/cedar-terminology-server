package org.metadatacenter.cedar.terminology.utils.validation.integratedsearch;

import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class ParameterObject {

  @Valid
  @NotNull
  private ValueConstraints valueConstraints;

  @NotEmpty
  private String inputText;

  public ParameterObject() { }

  public ValueConstraints getValueConstraints() {
    return valueConstraints;
  }

  public String getInputText() {
    return inputText;
  }
}