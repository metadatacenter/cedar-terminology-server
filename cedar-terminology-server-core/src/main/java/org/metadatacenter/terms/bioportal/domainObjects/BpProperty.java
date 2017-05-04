package org.metadatacenter.terms.bioportal.domainObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.metadatacenter.terms.bioportal.domainObjects.jackson.BpPropertyDeserializer;
import org.metadatacenter.terms.bioportal.domainObjects.jackson.BpTreeNodeDeserializer;

import java.util.List;

@JsonDeserialize(using = BpPropertyDeserializer.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BpProperty {
  private String id;
  private String type;
  private String propertyType;
  private String ontologyType;
  private String prefLabel;
  private List<String> label;
  private List<String> definition;
  private BpLinks links;
  private boolean hasChildren;

  public BpProperty() {
  }

  public BpProperty(String id, String type, String propertyType, String ontologyType, String prefLabel, List<String>
      label, List<String> definition, BpLinks links, boolean hasChildren) {
    this.id = id;
    this.type = type;
    this.propertyType = propertyType;
    this.ontologyType = ontologyType;
    this.prefLabel = prefLabel;
    this.label = label;
    this.definition = definition;
    this.links = links;
    this.hasChildren = hasChildren;
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

  public String getPrefLabel() {
    return prefLabel;
  }

  public void setPrefLabel(String prefLabel) {
    this.prefLabel = prefLabel;
  }

  public List<String> getLabel() {
    return label;
  }

  public void setLabel(List<String> label) {
    this.label = label;
  }

  public List<String> getDefinition() {
    return definition;
  }

  public void setDefinition(List<String> definition) {
    this.definition = definition;
  }

  public BpLinks getLinks() {
    return links;
  }

  public void setLinks(BpLinks links) {
    this.links = links;
  }

  public boolean getHasChildren() {
    return hasChildren;
  }

  public void setHasChildren(boolean hasChildren) {
    this.hasChildren = hasChildren;
  }

  @Override
  public String toString() {
    return "BpProperty{" +
        "id='" + id + '\'' +
        ", type='" + type + '\'' +
        ", propertyType='" + propertyType + '\'' +
        ", ontologyType='" + ontologyType + '\'' +
        ", prefLabel='" + prefLabel + '\'' +
        ", label=" + label +
        ", definition=" + definition +
        ", links=" + links +
        ", hasChildren=" + hasChildren +
        '}';
  }
}
