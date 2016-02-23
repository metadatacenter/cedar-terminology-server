package org.metadatacenter.terms.bioportal.domainObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpOntology {

  @JsonProperty("@id")
  private String id;
  @JsonProperty("@type")
  private String ldType;
  @JsonProperty("ontologyType")
  private String type;
  private String acronym;
  private String name;

  public BpOntology() {}

  public BpOntology(String id, String ldType, String type, String acronym, String name) {
    this.id = id;
    this.ldType = ldType;
    this.type = type;
    this.acronym = acronym;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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
}
