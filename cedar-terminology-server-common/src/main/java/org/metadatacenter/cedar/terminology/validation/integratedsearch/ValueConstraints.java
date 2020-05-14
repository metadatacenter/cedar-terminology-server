package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ValueConstraints {

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
}
