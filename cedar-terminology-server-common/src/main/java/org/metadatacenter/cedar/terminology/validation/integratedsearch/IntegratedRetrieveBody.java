package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class IntegratedRetrieveBody
{

  @Valid
  @NotNull
  private ValueConstraints valueConstraints;
  /**
   * The withdrawn page-number paging, still read because published clients send {@code page} 1 with
   * every request. Absent or 1, it is ignored; any other page is refused.
   */
  private Integer page;
  /** Withdrawn with {@code page}, and ignored. */
  private Integer pageSize;
  /** How many results to return. */
  private Integer limit;
  /** How many results to skip. */
  private Integer offset;

  public IntegratedRetrieveBody() { }

  public ValueConstraints getValueConstraints() {
    return valueConstraints;
  }

  public Integer getPage() {
    return page;
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public Integer getLimit() {
    return limit;
  }

  public Integer getOffset() {
    return offset;
  }

}