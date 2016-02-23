package org.metadatacenter.terms.bioportal.domainObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpOntologyCategory {

  private String id;
  private String acronym;
  private String name;
  private String description;
  private String created;
  private String parentCategory;

  public BpOntologyCategory() {}

  public BpOntologyCategory(String id, String acronym, String name, String description, String created, String
      parentCategory) {
    this.id = id;
    this.acronym = acronym;
    this.name = name;
    this.description = description;
    this.created = created;
    this.parentCategory = parentCategory;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getAcronym() {
    return acronym;
  }

  public void setAcronym(String acronym) {
    this.acronym = acronym;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCreated() {
    return created;
  }

  public void setCreated(String created) {
    this.created = created;
  }

  public String getParentCategory() {
    return parentCategory;
  }

  public void setParentCategory(String parentCategory) {
    this.parentCategory = parentCategory;
  }
}
