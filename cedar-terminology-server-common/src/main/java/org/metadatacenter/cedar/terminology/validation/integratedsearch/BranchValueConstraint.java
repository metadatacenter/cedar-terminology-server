package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.metadatacenter.artifacts.model.core.fields.constraints.VersionSpec;
import org.hibernate.validator.constraints.NotEmpty;

public class BranchValueConstraint extends RejectUnknownFields {

  @NotEmpty
  private String uri;
  @NotEmpty
  private String acronym;
  private String source;
  private String name;
  private Integer maxDepth;
  private String iri;
  // Optional pinned version (a version_id or tag such as "latest"). Absent means the current version,
  // so existing constraints are unchanged; when set, the local store serves that version reproducibly.
  @JsonDeserialize(using = ConstraintVersionDeserializer.class)
  private VersionSpec version;
  // Optional source system; absent/blank means BioPortal (see OntologyValueConstraint).
  private String sourceSystem;

  public BranchValueConstraint() { }

  public String getUri() {
    return uri;
  }

  public String getAcronym() {
    return acronym;
  }

  public String getVersion() {
    return version == null ? null : version.id();
  }

  public VersionSpec versionSpec() { return version; }

  public String getSource() { return source; }

  public String getName() { return name; }

  public Integer getMaxDepth() { return maxDepth; }

  public String getIri() { return iri; }

  public String getSourceSystem() {
    return sourceSystem;
  }

}
