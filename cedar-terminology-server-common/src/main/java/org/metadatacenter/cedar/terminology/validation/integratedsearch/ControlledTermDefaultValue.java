package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Documentation-only model for the default value a controlled-term field carries.
 *
 * <p>{@link ValueConstraints} keeps the key as a Jackson tree, because this server accepts a whole
 * posted {@code _valueConstraints} object and never reads the default value out of it. A tree
 * introspects into an object with no properties and describes nothing, so the documented schema
 * comes from this class instead: the term to pre-select, and the label to show for it.</p>
 */
@Schema(name = "ControlledTermDefaultValue",
    description = "The default value of a controlled-term field: the term to pre-select, and the label to "
        + "show for it. Accepted as part of a posted value-constraints object and not read.")
public class ControlledTermDefaultValue {

  @Schema(description = "IRI of the term to pre-select.", format = "uri",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String termUri;

  @Schema(name = "rdfs:label", description = "Label to show for the pre-selected term.",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String label;

  public String getTermUri() {
    return termUri;
  }

  public void setTermUri(String termUri) {
    this.termUri = termUri;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }
}
