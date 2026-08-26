package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.metadatacenter.artifacts.model.core.fields.constraints.VersionSpec;
import org.hibernate.validator.constraints.NotEmpty;

public class OntologyValueConstraint extends RejectUnknownFields {

  private String uri;
  @NotEmpty
  private String acronym;
  private String name;
  private Integer numTerms;
  private String iri;
  // Optional pinned version (version_id or tag); absent means the current version.
  @JsonDeserialize(using = ConstraintVersionDeserializer.class)
  private VersionSpec version;
  // Optional source system the terms come from; absent/blank means BioPortal. A non-BioPortal source is
  // served from the local store or not at all — it is never proxied to BioPortal.
  private String sourceSystem;

  public OntologyValueConstraint() { }

  public String getVersion() {
    return version == null ? null : version.id();
  }

  public VersionSpec versionSpec() { return version; }

  public String getUri() { return uri; }

  public String getAcronym() {
    return acronym;
  }

  public String getName() { return name; }

  public Integer getNumTerms() { return numTerms; }

  public String getIri() { return iri; }

  public String getSourceSystem() {
    return sourceSystem;
  }

}
