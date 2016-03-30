package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.List;

@ApiModel
@JsonPropertyOrder({"id", "@id", "@type", "type", "label", "creator", "ontology", "definitions", "synonyms", "subclassOf",
    "relations", "provisional", "created", "hasChildren"})
public class OntologyClass {
  @ApiModelProperty(hidden = true)
  private String id;
  @ApiModelProperty(hidden = true)
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String ldType = "http://data.bioontology.org/metadata/OntologyClass";
  private String type = "OntologyClass";
  @ApiModelProperty(required = true)
  private String label;
  @ApiModelProperty(required = true)
  private String creator;
  @ApiModelProperty(required = true)
  private String ontology;
  @ApiModelProperty(required = false)
  private List<String> definitions;
  @ApiModelProperty(required = false)
  private List<String> synonyms;
  @ApiModelProperty(required = false)
  private String subclassOf;
  @ApiModelProperty(required = false)
  private List<Relation> relations;
  @ApiModelProperty(hidden = true)
  private Boolean provisional;
  @ApiModelProperty(hidden = true)
  private String created;
  @ApiModelProperty(required = false)
  private boolean hasChildren;

  // The default constructor is used by Jackson for deserialization
  public OntologyClass() {
  }

  public OntologyClass(String id, String ldId, String label, String creator, String ontology, List<String>
      definitions, List<String> synonyms, String subclassOf, List<Relation> relations, boolean provisional, String
                           created, Boolean hasChildren) {
    this.id = id;
    this.ldId = ldId;
    this.label = label;
    this.creator = creator;
    this.ontology = ontology;
    this.definitions = definitions;
    this.synonyms = synonyms;
    this.subclassOf = subclassOf;
    this.relations = relations;
    this.provisional = provisional;
    this.created = created;
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

  public String getOntology() {
    return ontology;
  }

  public void setOntology(String ontology) {
    this.ontology = ontology;
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

  public String getSubclassOf() {
    return subclassOf;
  }

  public void setSubclassOf(String subclassOf) {
    this.subclassOf = subclassOf;
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

  public boolean getHasChildren() {
    return hasChildren;
  }

  public void setHasChildren(boolean hasChildren) {
    this.hasChildren = hasChildren;
  }
}
