package org.metadatacenter.terminology.services.bioportal.domainObjects;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.metadatacenter.terminology.services.bioportal.deserializers.OntologyClassDeserializer;

import java.util.List;

@JsonDeserialize(using = OntologyClassDeserializer.class)
public class OntologyClass
{
  private String id;
  private String label;
  private String creator;
  private String ontology;
  private List<String> definitions;
  private List<String> synonyms;
  private String subclassOf;
  private List<Relation> relations;
  private boolean provisional;
  private String created;

  public OntologyClass(String id, String label, String creator, String ontology, List<String> definitions,
    List<String> synonyms, String subclassOf, List<Relation> relations, boolean provisional, String created)
  {
    this.id = id;
    this.label = label;
    this.creator = creator;
    this.ontology = ontology;
    this.definitions = definitions;
    this.synonyms = synonyms;
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

  public String getSubclassOf()
  {
    return subclassOf;
  }

  public void setSubclassOf(String subclassOf)
  {
    this.subclassOf = subclassOf;
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

  public String getCreated()
  {
    return created;
  }

  public void setCreated(String created)
  {
    this.created = created;
  }
}
