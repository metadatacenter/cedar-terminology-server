package org.metadatacenter.terms.domainObjects;

import java.util.List;

public class Value
{
  private String id;
  private String label;
  private String creator;
  private String vsId;
  private String vsCollection;
  private List<String> definitions;
  private List<String> synonyms;
  private List<Relation> relations;
  private boolean provisional;
  private String created;

  // The default constructor is used by Jackson for deserialization
  public Value() {}

  public Value(String id, String label, String creator, String vsId, String vsCollection, List<String> definitions,
    List<String> synonyms, List<Relation> relations, boolean provisional, String created)
  {
    this.id = id;
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

  public String getCreated()
  {
    return created;
  }

  public void setCreated(String created)
  {
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

  public String getLabel()
  {
    return label;
  }

  public void setLabel(String label)
  {
    this.label = label;
  }

  public String getCreator()
  {
    return creator;
  }

  public void setCreator(String creator)
  {
    this.creator = creator;
  }

  public String getVsId()
  {
    return vsId;
  }

  public void setVsId(String vsId)
  {
    this.vsId = vsId;
  }

  public String getVsCollection()
  {
    return vsCollection;
  }

  public void setVsCollection(String vsCollection)
  {
    this.vsCollection = vsCollection;
  }

  public List<String> getDefinitions()
  {
    return definitions;
  }

  public void setDefinitions(List<String> definitions)
  {
    this.definitions = definitions;
  }

  public List<String> getSynonyms()
  {
    return synonyms;
  }

  public void setSynonyms(List<String> synonyms)
  {
    this.synonyms = synonyms;
  }

  public List<Relation> getRelations()
  {
    return relations;
  }

  public void setRelations(List<Relation> relations)
  {
    this.relations = relations;
  }

  public boolean isProvisional()
  {
    return provisional;
  }

  public void setProvisional(boolean provisional)
  {
    this.provisional = provisional;
  }
}
