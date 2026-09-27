package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class IntegratedSearchBody {

  @Valid
  @NotNull
  private ParameterObject parameterObject;
  private int page;
  private int pageSize;
  /** How many results to return; sent instead of page and pageSize. */
  private Integer limit;
  /** How many results to skip; sent instead of page and pageSize. */
  private Integer offset;

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

  public Integer getLimit() {
    return limit;
  }

  public Integer getOffset() {
    return offset;
  }

}