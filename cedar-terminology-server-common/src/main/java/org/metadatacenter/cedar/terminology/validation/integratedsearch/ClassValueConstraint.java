package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import org.hibernate.validator.constraints.NotEmpty;

public class ClassValueConstraint {
  // Enumerated classes will be returned to the client as they are, all the attributes are required, and we will not
  // allow any extra attributes

  @NotEmpty
  private String uri;
  @NotEmpty
  private String prefLabel;
  @NotEmpty
  private String type;
  private String label; // Optional
  @NotEmpty
  private String source;

  public ClassValueConstraint() { }

  public String getUri() {
    return uri;
  }

  public String getPrefLabel() {
    return prefLabel;
  }

  public String getType() {
    return type;
  }

  public String getLabel() {
    return label;
  }

  public String getSource() {
    return source;
  }

}
