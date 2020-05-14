package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class IntegratedSearchBody {

  @Valid
  @NotNull
  private ParameterObject parameterObject;
  private int page;
  private int pageSize;

  public IntegratedSearchBody() { }

  public ParameterObject getParameterObject() {
    return parameterObject;
  }

  public int getPage() {
    return page;
  }

  public int getPageSize() {
    return pageSize;
  }

}