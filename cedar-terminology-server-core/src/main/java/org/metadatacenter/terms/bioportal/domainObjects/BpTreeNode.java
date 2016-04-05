package org.metadatacenter.terms.bioportal.domainObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpTreeNode {

  @JsonProperty("@id")
  private String id;
  @JsonProperty("@type")
  private String type;
  private String prefLabel;
  private boolean hasChildren;
  private List<BpTreeNode> children;
  private BpLinks links;
  private boolean obsolete;

  public BpTreeNode() {}

  public BpTreeNode(String id, String type, String prefLabel, boolean hasChildren, List<BpTreeNode> children, BpLinks
      links, boolean obsolete) {
    this.id = id;
    this.type = type;
    this.prefLabel = prefLabel;
    this.hasChildren = hasChildren;
    this.children = children;
    this.links = links;
    this.obsolete = obsolete;
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

  public boolean getHasChildren() {
    return hasChildren;
  }

  public void setHasChildren(boolean hasChildren) {
    this.hasChildren = hasChildren;
  }

  public List<BpTreeNode> getChildren() {
    return children;
  }

  public void setChildren(List<BpTreeNode> children) {
    this.children = children;
  }

  public BpLinks getLinks() {
    return links;
  }

  public void setLinks(BpLinks links) {
    this.links = links;
  }

  public boolean isObsolete() {
    return obsolete;
  }

  public void setObsolete(boolean obsolete) {
    this.obsolete = obsolete;
  }

  @Override
  public String toString() {
    return "BpTreeNode{" +
        "id='" + id + '\'' +
        ", type='" + type + '\'' +
        ", prefLabel='" + prefLabel + '\'' +
        ", hasChildren=" + hasChildren +
        ", children=" + children +
        ", links=" + links +
        ", obsolete=" + obsolete +
        '}';
  }
}
