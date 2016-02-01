package org.metadatacenter.terminology.services.bioportal.domainObjects;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.metadatacenter.terminology.services.bioportal.deserializers.RelationDeserializer;

@JsonDeserialize(using = RelationDeserializer.class)
public class Relation
{
  private String relationType;
  private String targetClassId;
  private String targetClassOntology;

  public Relation(String relationType, String targetClassId, String targetClassOntology)
  {
    this.relationType = relationType;
    this.targetClassId = targetClassId;
    this.targetClassOntology = targetClassOntology;
  }

  public String getRelationType()
  {
    return relationType;
  }

  public void setRelationType(String relationType)
  {
    this.relationType = relationType;
  }

  public String getTargetClassId()
  {
    return targetClassId;
  }

  public void setTargetClassId(String targetClassId)
  {
    this.targetClassId = targetClassId;
  }

  public String getTargetClassOntology()
  {
    return targetClassOntology;
  }

  public void setTargetClassOntology(String targetClassOntology)
  {
    this.targetClassOntology = targetClassOntology;
  }
}
