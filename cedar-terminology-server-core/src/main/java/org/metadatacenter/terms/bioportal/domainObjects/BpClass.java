package org.metadatacenter.terms.bioportal.domainObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpClass {
  @JsonProperty("@id")
  private String id;
  @JsonProperty("@type")
  private String type;
  private String prefLabel;
  // Here we use List<Object> instead of List<String> because some BioPortal classes have objects in their definition
  // field, not just string. An example is the Thing class from schema.org. Using List<String> causes a
  // deserialization problem
  private List<Object> definition;
  private List<String> synonym;
  private boolean provisional;
  private BpLinks links;
  private boolean hasChildren;

  public BpClass() {
  }

  public BpClass(String id, String type, String prefLabel, List<Object> definition, List<String> synonym,
                 boolean provisional, BpLinks links, boolean hasChildren) {
    this.id = id;
    this.type = type;
    this.prefLabel = prefLabel;
    this.definition = definition;
    this.synonym = synonym;
    this.provisional = provisional;
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

  public String getPrefLabel() {
    return prefLabel;
  }

  public void setPrefLabel(String prefLabel) {
    this.prefLabel = prefLabel;
  }

  public List<Object> getDefinition() {
    return definition;
  }

  public void setDefinition(List<Object> definition) {
    this.definition = definition;
  }

  public List<String> getSynonym() {
    return synonym;
  }

  public void setSynonym(List<String> synonym) {
    this.synonym = synonym;
  }

  public boolean isProvisional() {
    return provisional;
  }

  public void setProvisional(boolean provisional) {
    this.provisional = provisional;
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
    return "BpClass{" +
        "id='" + id + '\'' +
        ", type='" + type + '\'' +
        ", prefLabel='" + prefLabel + '\'' +
        ", definition=" + definition +
        ", synonym=" + synonym +
        ", provisional=" + provisional +
        ", links=" + links +
        ", hasChildren=" + hasChildren +
        '}';
  }
}
