package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class ValueConstraints extends RejectUnknownFields {

  @Valid
  @NotNull
  private List<OntologyValueConstraint> ontologies;
  @Valid
  @NotNull
  private List<BranchValueConstraint> branches;
  @Valid
  @NotNull
  private List<ValueSetValueConstraint> valueSets;
  @Valid
  @NotNull
  private List<ClassValueConstraint> classes;
  @Valid
  private List<Action> actions;
  /**
   * The field's default value, kept as a tree because this server never reads it.
   *
   * A caller posts the whole {@code _valueConstraints} object, and refusing a key it carries is not
   * this endpoint's job; a tree node accepts one shape as readily as another. For the documented
   * schema that means naming the shape a controlled-term field's default value takes, since a
   * Jackson tree introspects into an object with no properties and describes nothing.
   */
  @Schema(implementation = ControlledTermDefaultValue.class)
  private JsonNode defaultValue;
  private boolean requiredValue;
  private boolean recommendedValue;
  private boolean multipleChoice;

  public ValueConstraints() { }

  public List<OntologyValueConstraint> getOntologies() {
    return ontologies;
  }

  public List<BranchValueConstraint> getBranches() {
    return branches;
  }

  public List<ValueSetValueConstraint> getValueSets() { return valueSets; }

  public List<ClassValueConstraint> getClasses() { return classes; }

  public List<Action> getActions() { return actions; }

  public JsonNode getDefaultValue() { return defaultValue; }

  public boolean isRequiredValue() { return requiredValue; }

  public boolean isRecommendedValue() { return recommendedValue; }

  public boolean isMultipleChoice() { return multipleChoice; }
}
