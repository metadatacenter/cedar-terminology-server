package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class IntegratedRetrieveBody
{

  @Valid
  @NotNull
  private ValueConstraints valueConstraints;
  private int page;
  private int pageSize;

  public IntegratedRetrieveBody() { }

  public ValueConstraints getValueConstraints() {
    return valueConstraints;
  }

  public int getPage() {
    return page;
  }

  public int getPageSize() {
    return pageSize;
  }

}