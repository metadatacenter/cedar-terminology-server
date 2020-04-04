package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

import static org.metadatacenter.cedar.terminology.util.Constants.*;

@JsonPropertyOrder({"id", "@id", "@type", "type", "sourceClassId", "relationType", "targetClassId", "targetClassOntology", "created"})
public class Relation implements Serializable
{
  String id;
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String ldType = BP_TYPE_BASE + BP_TYPE_RELATION;
  private String type = BP_TYPE_RELATION;
  private String sourceClassId;
  private String relationType;
  private String targetClassId;
  private String targetClassOntology;
  private String created;
  private String creator;

  // The default constructor is used by Jackson for deserialization
  public Relation() {}

  public Relation(String id, String ldId, String sourceClassId, String relationType,
                  String targetClassId, String targetClassOntology, String created, String creator) {
    this.id = id;
    this.ldId = ldId;
    this.sourceClassId = sourceClassId;
    this.relationType = relationType;
    this.targetClassId = targetClassId;
    this.targetClassOntology = targetClassOntology;
    this.created = created;
    this.creator = creator;
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

  public String getLdType() {
    return ldType;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
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

  public String getCreator() {
    return creator;
  }

  public void setCreator(String creator) {
    this.creator = creator;
  }
}
