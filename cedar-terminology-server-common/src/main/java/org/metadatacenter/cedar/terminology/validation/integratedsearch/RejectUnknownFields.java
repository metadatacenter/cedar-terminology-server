package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Makes this wire-model boundary fail loud even when the surrounding Dropwizard mapper is configured
 * to ignore unknown properties. The artifact-library drift test identifies which field must be added.
 */
abstract class RejectUnknownFields {

  @JsonAnySetter
  public void rejectUnknownField(String field, JsonNode value) {
    throw new IllegalArgumentException("Unknown controlled-term value-constraint field: " + field);
  }
}
