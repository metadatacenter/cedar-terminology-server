package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wordnik.swagger.annotations.ApiModel;
import com.wordnik.swagger.annotations.ApiModelProperty;

@ApiModel
@JsonPropertyOrder({"id", "@id", "sourceClassId", "relationType", "targetClassId", "targetClassOntology", "created"})
public class Relation
{
  @ApiModelProperty(hidden = true)
  String id;
  @ApiModelProperty(hidden = true)
  @JsonProperty("@id")
  private String ldId;
  @ApiModelProperty(required = true)
  private String sourceClassId;
  @ApiModelProperty(required = true)
  private String relationType;
  @ApiModelProperty(required = true)
  private String targetClassId;
  @ApiModelProperty(required = true)
  private String targetClassOntology;
  @ApiModelProperty(hidden = true)
  private String created;

  // The default constructor is used by Jackson for deserialization
  public Relation() {}

  public Relation(String id, String ldId, String sourceClassId, String relationType, String targetClassId, String
      targetClassOntology, String created) {
    this.id = id;
    this.ldId = ldId;
    this.sourceClassId = sourceClassId;
    this.relationType = relationType;
    this.targetClassId = targetClassId;
    this.targetClassOntology = targetClassOntology;
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

  public String getSourceClassId() {
    return sourceClassId;
  }

  public void setSourceClassId(String sourceClassId) {
    this.sourceClassId = sourceClassId;
  }

  public String getRelationType() {
    return relationType;
  }

  public void setRelationType(String relationType) {
    this.relationType = relationType;
  }

  public String getTargetClassId() {
    return targetClassId;
  }

  public void setTargetClassId(String targetClassId) {
    this.targetClassId = targetClassId;
  }

  public String getTargetClassOntology() {
    return targetClassOntology;
  }

  public void setTargetClassOntology(String targetClassOntology) {
    this.targetClassOntology = targetClassOntology;
  }

  public String getCreated() {
    return created;
  }

  public void setCreated(String created) {
    this.created = created;
  }
}
