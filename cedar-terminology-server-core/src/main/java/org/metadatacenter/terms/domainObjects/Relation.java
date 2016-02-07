package org.metadatacenter.terms.domainObjects;

public class Relation
{
  String id;
  private String sourceClassId;
  private String relationType;
  private String targetClassId;
  private String targetClassOntology;
  private String created;

  // The default constructor is used by Jackson for deserialization
  public Relation() {}

  public Relation(String id, String sourceClassId, String relationType, String targetClassId,
    String targetClassOntology, String created)
  {
    this.id = id;
    this.sourceClassId = sourceClassId;
    this.relationType = relationType;
    this.targetClassId = targetClassId;
    this.targetClassOntology = targetClassOntology;
    this.created = created;
  }

  public String getId()
  {
    return id;
  }

  public void setId(String id)
  {
    this.id = id;
  }

  public String getSourceClassId()
  {
    return sourceClassId;
  }

  public void setSourceClassId(String sourceClassId)
  {
    this.sourceClassId = sourceClassId;
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

  public String getCreated()
  {
    return created;
  }

  public void setCreated(String created)
  {
    this.created = created;
  }

}
