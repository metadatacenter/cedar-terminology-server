package org.metadatacenter.terms.bioportal.domainObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpProperty {
  @JsonProperty("@id")
  private String id;
  @JsonProperty("@type")
  private String type;
  private String propertyType;
  private String ontologyType;
  private List<String> label;
  private List<String> labelGenerated;
  private List<String> definition;
  private BpLinks links;

  public BpProperty() {
  }

  public BpProperty(String id, String type, String propertyType, String ontologyType, List<String> label,
                    List<String> labelGenerated, List<String> definition, BpLinks links) {
    this.id = id;
    this.type = type;
    this.propertyType = propertyType;
    this.ontologyType = ontologyType;
    this.label = label;
    this.labelGenerated = labelGenerated;
    this.definition = definition;
    this.links = links;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

  public String getOntologyType() {
    return ontologyType;
  }

  public void setOntologyType(String ontologyType) {
    this.ontologyType = ontologyType;
  }

  public List<String> getLabel() {
    return label;
  }

  public void setLabel(List<String> label) {
    this.label = label;
  }

  public List<String> getLabelGenerated() {
    return labelGenerated;
  }

  public void setLabelGenerated(List<String> labelGenerated) {
    this.labelGenerated = labelGenerated;
  }

  public List<String> getDefinition() { return definition; }

  public void setDefinition(List<String> definition) { this.definition = definition; }

  public BpLinks getLinks() {
    return links;
  }

  public void setLinks(BpLinks links) {
    this.links = links;
  }

  @Override
  public String toString() {
    return "BpProperty{" +
        "id='" + id + '\'' +
        ", type='" + type + '\'' +
        ", propertyType='" + propertyType + '\'' +
        ", ontologyType='" + ontologyType + '\'' +
        ", label=" + label +
        ", labelGenerated=" + labelGenerated +
        ", definition=" + definition +
        ", links=" + links +
        '}';
  }
}
