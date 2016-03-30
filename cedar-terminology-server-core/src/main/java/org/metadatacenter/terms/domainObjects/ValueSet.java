package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.List;

@ApiModel
@JsonPropertyOrder({"id", "@id", "@type", "type", "type", "prefLabel", "creator", "ontology", "definitions", "synonyms", "subclassOf",
    "relations", "provisional", "created"})
public class ValueSet implements Serializable
{
  @ApiModelProperty(hidden = true)
  private String id;
  @ApiModelProperty(hidden = true)
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String ldType = "http://data.bioontology.org/metadata/ValueSet";
  private String type = "ValueSet";
  @ApiModelProperty(required = true)
  private String prefLabel;
  @ApiModelProperty(required = true)
  private String creator;
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
  public ValueSet() {}

  public ValueSet(String id, String ldId, String prefLabel, String creator, String vsCollection, List<String>
      definitions, List<String> synonyms, List<Relation> relations, boolean provisional, String created) {
    this.id = id;
    this.ldId = ldId;
    this.prefLabel = prefLabel;
    this.creator = creator;
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

  public String getCreator() {
    return creator;
  }

  public void setCreator(String creator) {
    this.creator = creator;
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
