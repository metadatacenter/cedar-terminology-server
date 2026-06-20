package org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

/**
 * Documentation-only model for the request body of the integrated-retrieve endpoint.
 *
 * <p>This thin bean exists purely to reproduce the {@code IntegratedRetrieveRequestBody} schema that
 * the hand-authored spec exposed. It mirrors that schema's structure: the CEDAR value constraints
 * and the user-supplied input text, plus optional pagination fields.</p>
 */
@ApiModel(value = "IntegratedRetrieveRequestBody", description = "Object that encapsulates the information needed to " +
    "run the integrated-retrieve query.")
public class IntegratedRetrieveRequestBody {

  @ApiModelProperty(value = "The value constraints field specification. Based on CEDAR's '_valueConstraints' field.",
      required = true)
  private ValueConstraints valueConstraints;

  @ApiModelProperty(value = "The user-supplied initial characters used to filter the conforming values.")
  private String inputText;

  @ApiModelProperty(value = "Page to be returned. Example: 7.")
  private Integer page;

  @ApiModelProperty(value = "Number of results per page. Example: 10.")
  private Integer pageSize;

  public ValueConstraints getValueConstraints() {
    return valueConstraints;
  }

  public void setValueConstraints(ValueConstraints valueConstraints) {
    this.valueConstraints = valueConstraints;
  }

  public String getInputText() {
    return inputText;
  }

  public void setInputText(String inputText) {
    this.inputText = inputText;
  }

  public Integer getPage() {
    return page;
  }

  public void setPage(Integer page) {
    this.page = page;
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public void setPageSize(Integer pageSize) {
    this.pageSize = pageSize;
  }

  @ApiModel(value = "IntegratedRetrieveValueConstraints")
  public static class ValueConstraints {

    @ApiModelProperty(value = "List of ontology classes used to constrain the values.", required = true)
    private List<ConstraintClass> classes;

    @ApiModelProperty(value = "List of ontologies used to constrain the values.", required = true)
    private List<ConstraintOntology> ontologies;

    @ApiModelProperty(value = "List of ontology branches used to constrain the values.", required = true)
    private List<ConstraintBranch> branches;

    @ApiModelProperty(value = "List of value sets used to constrain the values.", required = true)
    private List<ConstraintValueSet> valueSets;

    @ApiModelProperty(value = "List of actions applied to the value constraints.")
    private List<ConstraintAction> actions;

    public List<ConstraintClass> getClasses() {
      return classes;
    }

    public void setClasses(List<ConstraintClass> classes) {
      this.classes = classes;
    }

    public List<ConstraintOntology> getOntologies() {
      return ontologies;
    }

    public void setOntologies(List<ConstraintOntology> ontologies) {
      this.ontologies = ontologies;
    }

    public List<ConstraintBranch> getBranches() {
      return branches;
    }

    public void setBranches(List<ConstraintBranch> branches) {
      this.branches = branches;
    }

    public List<ConstraintValueSet> getValueSets() {
      return valueSets;
    }

    public void setValueSets(List<ConstraintValueSet> valueSets) {
      this.valueSets = valueSets;
    }

    public List<ConstraintAction> getActions() {
      return actions;
    }

    public void setActions(List<ConstraintAction> actions) {
      this.actions = actions;
    }
  }

  @ApiModel(value = "IntegratedRetrieveConstraintClass")
  public static class ConstraintClass {

    @ApiModelProperty(value = "Class URI.", required = true)
    private String uri;

    @ApiModelProperty(value = "Preferred label of the class.", required = true)
    private String prefLabel;

    @ApiModelProperty(value = "Type of the class.", required = true)
    private String type;

    @ApiModelProperty(value = "Source of the class.", required = true)
    private String source;

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    public String getPrefLabel() {
      return prefLabel;
    }

    public void setPrefLabel(String prefLabel) {
      this.prefLabel = prefLabel;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }
  }

  @ApiModel(value = "IntegratedRetrieveConstraintOntology")
  public static class ConstraintOntology {

    @ApiModelProperty(value = "Ontology acronym.", required = true)
    private String acronym;

    public String getAcronym() {
      return acronym;
    }

    public void setAcronym(String acronym) {
      this.acronym = acronym;
    }
  }

  @ApiModel(value = "IntegratedRetrieveConstraintBranch")
  public static class ConstraintBranch {

    @ApiModelProperty(value = "Branch URI.", required = true)
    private String uri;

    @ApiModelProperty(value = "Ontology acronym.", required = true)
    private String acronym;

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    public String getAcronym() {
      return acronym;
    }

    public void setAcronym(String acronym) {
      this.acronym = acronym;
    }
  }

  @ApiModel(value = "IntegratedRetrieveConstraintValueSet")
  public static class ConstraintValueSet {

    @ApiModelProperty(value = "Value set URI.", required = true)
    private String uri;

    @ApiModelProperty(value = "Value set collection.", required = true)
    private String vsCollection;

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    public String getVsCollection() {
      return vsCollection;
    }

    public void setVsCollection(String vsCollection) {
      this.vsCollection = vsCollection;
    }
  }

  @ApiModel(value = "IntegratedRetrieveConstraintAction")
  public static class ConstraintAction {

    @ApiModelProperty(value = "Action to be applied.", required = true)
    private String action;

    @ApiModelProperty(value = "Term URI the action applies to.", required = true)
    private String termUri;

    @ApiModelProperty(value = "Type of the term.", required = true)
    private String type;

    @ApiModelProperty(value = "Source of the term.", required = true)
    private String source;

    public String getAction() {
      return action;
    }

    public void setAction(String action) {
      this.action = action;
    }

    public String getTermUri() {
      return termUri;
    }

    public void setTermUri(String termUri) {
      this.termUri = termUri;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }
  }
}
