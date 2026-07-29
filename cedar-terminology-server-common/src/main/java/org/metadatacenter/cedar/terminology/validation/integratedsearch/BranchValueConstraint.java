package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BranchValueConstraint {

  @NotEmpty
  private String uri;
  @NotEmpty
  private String acronym;
  // Optional pinned version (a version_id or tag such as "latest"). Absent means the current version,
  // so existing constraints are unchanged; when set, the local store serves that version reproducibly.
  private String version;

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

}