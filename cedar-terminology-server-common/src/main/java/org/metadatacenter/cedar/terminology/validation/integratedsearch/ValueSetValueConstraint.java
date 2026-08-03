package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.hibernate.validator.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ValueSetValueConstraint {

  @NotEmpty
  private String uri;

  // Any value-set collection may be constrained against. The historical CEDARVS/NLMVS/CADSR-VS
  // allow-list was removed — a value set from any collection resolves like any other snapshot.
  @NotEmpty
  private String vsCollection;
  // Optional pinned version (version_id or tag) of the collection; absent means the current version.
  @JsonDeserialize(using = ConstraintVersionDeserializer.class)
  private String version;
  // Optional source system; absent/blank means BioPortal (see OntologyValueConstraint).
  private String sourceSystem;

  public ValueSetValueConstraint() { }

  public String getUri() {
    return uri;
  }

  public String getVersion() {
    return version;
  }

  public String getVsCollection() {
    return vsCollection;
  }

  public String getSourceSystem() {
    return sourceSystem;
  }

}
