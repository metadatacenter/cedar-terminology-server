package org.metadatacenter.terms.bioportal.domainObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpProvisionalClass
{
  @JsonProperty("@id")
  private String id;
  @JsonProperty("@type")
  private String type;
  private String label;
  private String creator;
  private String ontology;
  private List<String> definition;
  private List<String> synonym;
  private String subclassOf;
  private List<BpProvisionalRelation> relations;
  private boolean provisional;
  private String created;

  public BpProvisionalClass() {}

  public BpProvisionalClass(String id, String type, String label, String creator, String ontology,
    List<String> definition, List<String> synonym, String subclassOf, List<BpProvisionalRelation> relations, boolean provisional,
    String created)
  {
    this.id = id;
    this.type = type;
    this.label = label;
    this.creator = creator;
    this.ontology = ontology;
    this.definition = definition;
    this.synonym = synonym;
    this.subclassOf = subclassOf;
    this.relations = relations;
    this.provisional = provisional;
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

  public String getType()
  {
    return type;
  }

  public void setType(String type)
  {
    this.type = type;
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

  public String getOntology()
  {
    return ontology;
  }

  public void setOntology(String ontology)
  {
    this.ontology = ontology;
  }

  public List<String> getDefinition()
  {
    return definition;
  }

  public void setDefinition(List<String> definition)
  {
    this.definition = definition;
  }

  public List<String> getSynonym()
  {
    return synonym;
  }

  public void setSynonym(List<String> synonym)
  {
    this.synonym = synonym;
  }

  public String getSubclassOf()
  {
    return subclassOf;
  }

  public void setSubclassOf(String subclassOf)
  {
    this.subclassOf = subclassOf;
  }

  public List<BpProvisionalRelation> getRelations()
  {
    return relations;
  }

  public void setRelations(List<BpProvisionalRelation> relations)
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

  public String getCreated()
  {
    return created;
  }

  public void setCreated(String created)
  {
    this.created = created;
  }

}
