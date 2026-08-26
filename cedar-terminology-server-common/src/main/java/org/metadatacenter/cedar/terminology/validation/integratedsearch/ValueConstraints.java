package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.databind.JsonNode;

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
