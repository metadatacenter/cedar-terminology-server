package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.validator.constraints.NotEmpty;

// Tolerate the additive value-constraint spec fields (iri / sourceSystem / version) a frozen template
// carries on a class entry: an enumerated class is self-describing (its uri + prefLabel are used as-is,
// no snapshot lookup), so those fields are not consumed here, but the request must still deserialize.
// The other three constraint kinds already declare this; matching them makes the tolerance explicit
// rather than relying on the Dropwizard mapper's fail-on-unknown being off.
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassValueConstraint {

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
