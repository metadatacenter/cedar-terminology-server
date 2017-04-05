package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"id", "@id", "@type", "type", "propertyType", "labels", "labelsGenerated", "definitions", "source"})
public class OntologyProperty {
  private String id;
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String ldType;
  private String type;
  private String propertyType;
  private List<String> labels;
  private List<String> labelsGenerated;
  private List<String> definitions;
  private String source;

  // The default constructor is used by Jackson for deserialization
  public OntologyProperty() {
  }

  public OntologyProperty(String id, String ldId, String ldType, String type, String propertyType, List<String>
      labels, List<String> labelsGenerated, List<String> definitions, String source) {
    this.id = id;
    this.ldId = ldId;
    this.ldType = ldType;
    this.type = type;
    this.propertyType = propertyType;
    this.labels = labels;
    this.labelsGenerated = labelsGenerated;
    this.definitions = definitions;
    this.source = source;
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

  public String getPropertyType() {
    return propertyType;
  }

  public void setPropertyType(String propertyType) {
    this.propertyType = propertyType;
  }

  public List<String> getLabels() {
    return labels;
  }

  public void setLabels(List<String> labels) {
    this.labels = labels;
  }

  public List<String> getLabelsGenerated() { return labelsGenerated; }

  public void setLabelsGenerated(List<String> labelsGenerated) {
    this.labelsGenerated = labelsGenerated;
  }

  public List<String> getDefinitions() { return definitions; }

  public void setDefinitions(List<String> definitions) { this.definitions = definitions; }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  @Override
  public String toString() {
    return "OntologyPropertySearchResult{" +
        "id='" + id + '\'' +
        ", ldId='" + ldId + '\'' +
        ", ldType='" + ldType + '\'' +
        ", type='" + type + '\'' +
        ", propertyType='" + propertyType + '\'' +
        ", labels=" + labels +
        ", labelsGenerated=" + labelsGenerated +
        ", definitions=" + definitions +
        ", source='" + source + '\'' +
        '}';
  }
}
