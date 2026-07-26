package org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Documentation-only model for the request body of the integrated-search endpoint.
 *
 * <p>This thin bean exists purely to reproduce the {@code IntegratedSearchRequestBody} schema that
 * the hand-authored spec exposed. It mirrors that schema's structure: a required
 * {@code parameterObject} (which itself wraps the CEDAR value constraints and the user-supplied
 * input text) plus optional pagination fields.</p>
 */
@Schema(name = "IntegratedSearchRequestBody", description = "Object that encapsulates the information needed to " +
    "run the integrated-search query.")
public class IntegratedSearchRequestBody {

  @Schema(description = "Object that encapsulates the value constraints and the input text used to run the " +
      "search query.", requiredMode = Schema.RequiredMode.REQUIRED)
  private ParameterObject parameterObject;

  @Schema(description = "Page to be returned. Example: 7.")
  private Integer page;

  @Schema(description = "Number of results per page. Example: 10.")
  private Integer pageSize;

  public ParameterObject getParameterObject() {
    return parameterObject;
  }

  public void setParameterObject(ParameterObject parameterObject) {
    this.parameterObject = parameterObject;
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

  @Schema(name = "IntegratedSearchParameterObject")
  public static class ParameterObject {

    @Schema(description = "The value constraints field specification. Based on CEDAR's '_valueConstraints' " +
        "field.", requiredMode = Schema.RequiredMode.REQUIRED)
    private ValueConstraints valueConstraints;

    @Schema(description = "The user-supplied initial characters used to filter the conforming values.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inputText;

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
  }

  @Schema(name = "IntegratedSearchValueConstraints")
  public static class ValueConstraints {

    @Schema(description = "List of ontology classes used to constrain the values.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ConstraintClass> classes;

    @Schema(description = "List of ontologies used to constrain the values.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ConstraintOntology> ontologies;

    @Schema(description = "List of ontology branches used to constrain the values.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ConstraintBranch> branches;

    @Schema(description = "List of value sets used to constrain the values.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ConstraintValueSet> valueSets;

    @Schema(description = "List of actions applied to the value constraints.")
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

  @Schema(name = "IntegratedSearchConstraintClass")
  public static class ConstraintClass {

    @Schema(description = "Class URI.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String uri;

    @Schema(description = "Preferred label of the class.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prefLabel;

    @Schema(description = "Type of the class.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "Source of the class.", requiredMode = Schema.RequiredMode.REQUIRED)
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

  @Schema(name = "IntegratedSearchConstraintOntology")
  public static class ConstraintOntology {

    @Schema(description = "Ontology acronym.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String acronym;

    public String getAcronym() {
      return acronym;
    }

    public void setAcronym(String acronym) {
      this.acronym = acronym;
    }
  }

  @Schema(name = "IntegratedSearchConstraintBranch")
  public static class ConstraintBranch {

    @Schema(description = "Branch URI.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String uri;

    @Schema(description = "Ontology acronym.", requiredMode = Schema.RequiredMode.REQUIRED)
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

  @Schema(name = "IntegratedSearchConstraintValueSet")
  public static class ConstraintValueSet {

    @Schema(description = "Value set URI.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String uri;

    @Schema(description = "Value set collection.", requiredMode = Schema.RequiredMode.REQUIRED)
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

  @Schema(name = "IntegratedSearchConstraintAction")
  public static class ConstraintAction {

    @Schema(description = "Action to be applied.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Schema(description = "Term URI the action applies to.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String termUri;

    @Schema(description = "Type of the term.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "Source of the term.", requiredMode = Schema.RequiredMode.REQUIRED)
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
