package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wordnik.swagger.annotations.ApiModel;

import java.util.List;

import static org.metadatacenter.terms.util.Constants.BP_TYPE_CLASS;

@ApiModel
@JsonPropertyOrder({"id", "@id", "@type", "prefLabel", "ontology", "hasChildren", "children", "obsolete"})
public class TreeNode {
  private String id;
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String ldtype;
  private String type = BP_TYPE_CLASS;
  private String prefLabel;
  private String ontology;
  private boolean hasChildren;
  private List<TreeNode> children;
  private boolean obsolete;

  // The default constructor is used by Jackson for deserialization
  public TreeNode() {
  }

  public TreeNode(String id, String ldId, String ldtype, String prefLabel, String ontology, boolean hasChildren,
                  List<TreeNode> children, boolean obsolete) {
    this.id = id;
    this.ldId = ldId;
    this.ldtype = ldtype;
    this.prefLabel = prefLabel;
    this.ontology = ontology;
    this.hasChildren = hasChildren;
    this.children = children;
    this.obsolete = obsolete;
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

  public String getLdtype() {
    return ldtype;
  }

  public void setLdtype(String ldtype) {
    this.ldtype = ldtype;
  }

  public String getType() {
    return type;
  }

  public String getPrefLabel() {
    return prefLabel;
  }

  public void setPrefLabel(String prefLabel) {
    this.prefLabel = prefLabel;
  }

  public String getOntology() {
    return ontology;
  }

  public void setOntology(String ontology) {
    this.ontology = ontology;
  }

  public boolean isHasChildren() {
    return hasChildren;
  }

  public void setHasChildren(boolean hasChildren) {
    this.hasChildren = hasChildren;
  }

  public List<TreeNode> getChildren() {
    return children;
  }

  public void setChildren(List<TreeNode> children) {
    this.children = children;
  }

  public boolean isObsolete() {
    return obsolete;
  }

  public void setObsolete(boolean obsolete) {
    this.obsolete = obsolete;
  }
}
