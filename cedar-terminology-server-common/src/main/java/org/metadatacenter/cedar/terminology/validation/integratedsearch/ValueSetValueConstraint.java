package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.metadatacenter.artifacts.model.core.fields.constraints.VersionSpec;
import org.hibernate.validator.constraints.NotEmpty;

public class ValueSetValueConstraint extends RejectUnknownFields {

  @NotEmpty
  private String uri;

  // Any value-set collection may be constrained against. The historical CEDARVS/NLMVS/CADSR-VS
  // allow-list was removed — a value set from any collection resolves like any other snapshot.
  @NotEmpty
  private String vsCollection;
  private String name;
  private Integer numTerms;
  private String iri;
  // Optional pinned version (version_id or tag) of the collection; absent means the current version.
  @JsonDeserialize(using = ConstraintVersionDeserializer.class)
  private VersionSpec version;
  // Optional source system; absent/blank means BioPortal (see OntologyValueConstraint).
  private String sourceSystem;

  public ValueSetValueConstraint() { }

  public String getUri() {
    return uri;
  }

  public String getVersion() {
    return version == null ? null : version.id();
  }

  public VersionSpec versionSpec() { return version; }

  public String getVsCollection() {
    return vsCollection;
  }

  public String getName() { return name; }

  public Integer getNumTerms() { return numTerms; }

  public String getIri() { return iri; }

  public String getSourceSystem() {
    return sourceSystem;
  }

}
