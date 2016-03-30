package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wordnik.swagger.annotations.ApiModel;

import java.io.Serializable;

@ApiModel
@JsonPropertyOrder({"id", "@id", "@type", "type", "name", "details"})
public class Ontology implements Serializable {
  private String id;
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String ldType = "http://data.bioontology.org/metadata/Ontology";
  private String type = "Ontology";
  private String name;
  private OntologyDetails details;

  public Ontology() {
  }

  public Ontology(String id, String ldId, String name, OntologyDetails details) {
    this.id = id;
    this.ldId = ldId;
    this.name = name;
    this.details = details;
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public OntologyDetails getDetails() {
    return details;
  }

  public void setDetails(OntologyDetails details) {
    this.details = details;
  }
}