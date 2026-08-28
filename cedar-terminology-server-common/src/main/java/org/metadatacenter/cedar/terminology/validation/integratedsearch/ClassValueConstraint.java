package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.metadatacenter.artifacts.model.core.fields.constraints.VersionSpec;
import org.hibernate.validator.constraints.NotEmpty;

public class ClassValueConstraint extends RejectUnknownFields {

  @NotEmpty
  private String uri;
  @NotEmpty
  private String prefLabel;
  @NotEmpty
  private String type;
  private String label; // Optional
  @NotEmpty
  private String source;
  private String iri;
  private String sourceSystem;
  @JsonDeserialize(using = ConstraintVersionDeserializer.class)
  private VersionSpec version;

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

  public String getIri() { return iri; }

  public String getSourceSystem() { return sourceSystem; }

  public String getVersion() { return version == null ? null : version.id(); }

  public VersionSpec versionSpec() { return version; }

}
