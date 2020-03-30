package org.metadatacenter.cedar.terminology.utils.validation.integratedsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.validation.Valid;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ValueConstraints {

  @Valid
  private List<VCOntology> ontologies;
//  private List<VCValueSet> valueSets;
//  private List<VCClass> classes;
//  private List<VCBranch> branches;
//
//  private boolean requiredValue;
//  private boolean multipleChoice;

  public ValueConstraints() {
  }

  public List<VCOntology> getOntologies() {
    return ontologies;
  }
}
