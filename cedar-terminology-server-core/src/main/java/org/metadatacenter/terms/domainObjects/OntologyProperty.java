package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"id", "@id", "@type", "type", "prefLabel", "labels", "definitions", "ontology", "hasChildren"})
public class OntologyProperty {
  private String id;
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String ldType;
  private String type;
  private String prefLabel;
  private List<String> labels;
  private List<String> definitions;
  private String ontology;
  private Boolean hasChildren;

  // The default constructor is used by Jackson for deserialization
  public OntologyProperty() {}

  public OntologyProperty(String id, String ldId, String ldType, String type, String prefLabel, List<String> labels,
                          List<String> definitions, String ontology, Boolean hasChildren) {
    this.id = id;
    this.ldId = ldId;
    this.ldType = ldType;
    this.type = type;
    this.prefLabel = prefLabel;
    this.labels = labels;
    this.definitions = definitions;
    this.ontology = ontology;
    this.hasChildren = hasChildren;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLdId() {
    return ldId;
  }

  public void setLdId(String ldId) {
    this.ldId = ldId;
  }

  public String getLdType() {
    return ldType;
  }

  public void setLdType(String ldType) {
    this.ldType = ldType;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getPrefLabel() {
    return prefLabel;
  }

  public void setPrefLabel(String prefLabel) {
    this.prefLabel = prefLabel;
  }

  public List<String> getLabels() {
    return labels;
  }

  public void setLabels(List<String> labels) {
    this.labels = labels;
  }

  public List<String> getDefinitions() {
    return definitions;
  }

  public void setDefinitions(List<String> definitions) {
    this.definitions = definitions;
  }

  public String getOntology() {
    return ontology;
  }

  public void setOntology(String ontology) {
    this.ontology = ontology;
  }

  public Boolean getHasChildren() {
    return hasChildren;
  }

  public void setHasChildren(Boolean hasChildren) {
    this.hasChildren = hasChildren;
  }

  @Override
  public String toString() {
    return "OntologyProperty{" +
        "id='" + id + '\'' +
        ", ldId='" + ldId + '\'' +
        ", ldType='" + ldType + '\'' +
        ", type='" + type + '\'' +
        ", prefLabel='" + prefLabel + '\'' +
        ", labels=" + labels +
        ", definitions=" + definitions +
        ", ontology='" + ontology + '\'' +
        ", hasChildren=" + hasChildren +
        '}';
  }
}
