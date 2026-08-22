package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.hibernate.validator.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BranchValueConstraint {

  @NotEmpty
  private String uri;
  @NotEmpty
  private String acronym;
  // Optional pinned version (a version_id or tag such as "latest"). Absent means the current version,
  // so existing constraints are unchanged; when set, the local store serves that version reproducibly.
  @JsonDeserialize(using = ConstraintVersionDeserializer.class)
  private String version;
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
    return version;
  }

  public String getSourceSystem() {
    return sourceSystem;
  }

}
