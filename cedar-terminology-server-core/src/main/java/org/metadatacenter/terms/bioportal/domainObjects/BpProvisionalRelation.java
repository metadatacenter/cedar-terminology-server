package org.metadatacenter.terms.bioportal.domainObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpProvisionalRelation
{
  @JsonProperty("@id")
  String id;
  private String source;
  private String relationType;
  private String targetClassId;
  private String targetClassOntology;
  private String created;
  private String creator;

  public BpProvisionalRelation() {}

  public BpProvisionalRelation(String id, String source, String relationType, String targetClassId, String
      targetClassOntology, String created, String creator) {
    this.id = id;
    this.source = source;
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

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
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
