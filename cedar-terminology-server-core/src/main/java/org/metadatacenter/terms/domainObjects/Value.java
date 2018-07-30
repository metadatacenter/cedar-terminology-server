package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
//import com.wordnik.swagger.annotations.ApiModel;
//import com.wordnik.swagger.annotations.ApiModelProperty;

import static org.metadatacenter.terms.util.Constants.BP_TYPE_BASE;
import static org.metadatacenter.terms.util.Constants.BP_TYPE_VALUE;

import java.util.List;

//@ApiModel
@JsonPropertyOrder({"id", "@id", "@type", "type", "prefLabel", "notation", "relatedMatch", "creator", "vsId", "vsCollection", "definitions", "synonyms", "relations", "provisional", "created"})
public class Value
{
  //@ApiModelProperty(hidden = true)
  private String id;
  //@ApiModelProperty(hidden = true)
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String ldType = BP_TYPE_BASE + BP_TYPE_VALUE;
  private String type = BP_TYPE_VALUE;
  //@ApiModelProperty(required = true)
  private String prefLabel;
  private String notation; // Used when retrieving values from the CADSR value sets ontology. This attribute
  // stores the VALIDVALUE, which is the value that needs to be stored in JSON-LD, while the prefLabel is the label
  // that needs to be shown in the UI
  private String relatedMatch; // Used when retrieving value sets from the CADSR value sets ontology. This attribute
  // stores the URI of the concept associated to the value (MEANINGCONCEPTS xml property), which is the id that
  // needs to be stored in JSON-LD as @id
  //@ApiModelProperty(required = true)
  private String creator;
  //@ApiModelProperty(required = true)
  private String vsId;
  //@ApiModelProperty(required = true)
  private String vsCollection;
  //@ApiModelProperty(required = false)
  private List<String> definitions;
  //@ApiModelProperty(required = false)
  private List<String> synonyms;
  //@ApiModelProperty(required = false)
  private List<Relation> relations;
  //@ApiModelProperty(hidden = true)
  private boolean provisional;
  //@ApiModelProperty(hidden = true)
  private String created;

  // The default constructor is used by Jackson for deserialization
  public Value() {}

  public Value(String id, String ldId, String prefLabel, String notation, String relatedMatch,
               String creator, String vsId, String vsCollection, List<String>
      definitions, List<String> synonyms, List<Relation> relations, boolean provisional, String created) {
    this.id = id;
    this.ldId = ldId;
    this.prefLabel = prefLabel;
    this.notation = notation;
    this.relatedMatch = relatedMatch;
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

  public String getNotation() { return notation; }

  public void setNotation(String notation) { this.notation = notation; }

  public String getRelatedMatch() { return relatedMatch; }

  public void setRelatedMatch(String relatedMatch) { this.relatedMatch = relatedMatch; }

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
