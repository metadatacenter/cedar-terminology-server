package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.List;

@ApiModel
@JsonPropertyOrder({"id", "@id", "label", "creator", "vsId", "vsCollection", "definitions", "synonyms",
    "relations", "provisional", "created"})
public class Value
{
  @ApiModelProperty(hidden = true)
  private String id;
  @ApiModelProperty(hidden = true)
  @JsonProperty("@id")
  private String ldId;
  @ApiModelProperty(required = true)
  private String label;
  @ApiModelProperty(required = true)
  private String creator;
  @ApiModelProperty(required = true)
  private String vsId;
  @ApiModelProperty(required = true)
  private String vsCollection;
  @ApiModelProperty(required = false)
  private List<String> definitions;
  @ApiModelProperty(required = false)
  private List<String> synonyms;
  @ApiModelProperty(required = false)
  private List<Relation> relations;
  @ApiModelProperty(hidden = true)
  private boolean provisional;
  @ApiModelProperty(hidden = true)
  private String created;

  // The default constructor is used by Jackson for deserialization
  public Value() {}

  public Value(String id, String ldId, String label, String creator, String vsId, String vsCollection, List<String>
      definitions, List<String> synonyms, List<Relation> relations, boolean provisional, String created) {
    this.id = id;
    this.ldId = ldId;
    this.label = label;
    this.creator = creator;
    this.vsId = vsId;
    this.vsCollection = vsCollection;
    this.definitions = definitions;
    this.synonyms = synonyms;
    this.relations = relations;
    this.provisional = provisional;
    this.created = created;
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

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getCreator() {
    return creator;
  }

  public void setCreator(String creator) {
    this.creator = creator;
  }

  public String getVsId() {
    return vsId;
  }

  public void setVsId(String vsId) {
    this.vsId = vsId;
  }

  public String getVsCollection() {
    return vsCollection;
  }

  public void setVsCollection(String vsCollection) {
    this.vsCollection = vsCollection;
  }

  public List<String> getDefinitions() {
    return definitions;
  }

  public void setDefinitions(List<String> definitions) {
    this.definitions = definitions;
  }

  public List<String> getSynonyms() {
    return synonyms;
  }

  public void setSynonyms(List<String> synonyms) {
    this.synonyms = synonyms;
  }

  public List<Relation> getRelations() {
    return relations;
  }

  public void setRelations(List<Relation> relations) {
    this.relations = relations;
  }

  public boolean isProvisional() {
    return provisional;
  }

  public void setProvisional(boolean provisional) {
    this.provisional = provisional;
  }

  public String getCreated() {
    return created;
  }

  public void setCreated(String created) {
    this.created = created;
  }
}
