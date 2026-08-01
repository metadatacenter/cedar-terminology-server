package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.hibernate.validator.constraints.NotEmpty;

import jakarta.validation.constraints.Pattern;

import static org.metadatacenter.cedar.terminology.util.Constants.BP_VS_COLLECTIONS_READ_REGEX;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ValueSetValueConstraint {

  @NotEmpty
  private String uri;

  @NotEmpty
  @Pattern(regexp=BP_VS_COLLECTIONS_READ_REGEX) // Checks that the vsCollection is valid
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
