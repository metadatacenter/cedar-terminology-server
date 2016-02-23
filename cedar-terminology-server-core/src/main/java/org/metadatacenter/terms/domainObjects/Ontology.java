package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wordnik.swagger.annotations.ApiModel;

import java.util.List;

@ApiModel
@JsonPropertyOrder({"id", "@id", "@type", "name", "details"})
public class Ontology {
  private String id;
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String type;
  private String name;
  private OntologyDetails details;

  public Ontology() {
  }

  public Ontology(String id, String ldId, String type, String name, OntologyDetails details) {
    this.id = id;
    this.ldId = ldId;
    this.type = type;
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