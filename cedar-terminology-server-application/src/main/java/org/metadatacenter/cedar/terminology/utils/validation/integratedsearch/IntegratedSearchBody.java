package org.metadatacenter.cedar.terminology.utils.validation.integratedsearch;

import org.hibernate.validator.constraints.NotEmpty;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class IntegratedSearchBody {

  @Valid
  @NotNull
  private ParameterObject parameterObject;
  private int page;
  private int pageSize;
//  @NotEmpty
//  private String cedarAPIKey;

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

//  public String getCedarAPIKey() {
//    return cedarAPIKey;
//  }
}