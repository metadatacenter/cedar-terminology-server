package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wordnik.swagger.annotations.ApiModel;

import static org.metadatacenter.terms.util.Constants.BP_TYPE_BASE;
import static org.metadatacenter.terms.util.Constants.BP_TYPE_ONTOLOGY;

import java.io.Serializable;

@ApiModel
@JsonPropertyOrder({"id", "@id", "@type", "type", "name", "flat", "details"})
public class Ontology implements Serializable {
  private String id;
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String ldType = BP_TYPE_BASE + BP_TYPE_ONTOLOGY;
  private String type = BP_TYPE_ONTOLOGY;
  private String name;
  @JsonProperty("flat")
  private boolean isFlat;
  private OntologyDetails details;

  public Ontology() {
  }

  public Ontology(String id, String ldId, String name, boolean isFlat, OntologyDetails details) {
    this.id = id;
    this.ldId = ldId;
    this.name = name;
    this.isFlat = isFlat;
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

  public boolean getIsFlat() {
    return isFlat;
  }

  public void setIsFlat(boolean isFlat) {
    this.isFlat = isFlat;
  }

  public OntologyDetails getDetails() {
    return details;
  }

  public void setDetails(OntologyDetails details) {
    this.details = details;
  }

  @Override
  public String toString() {
    return "Ontology{" +
        "id='" + id + '\'' +
        ", ldId='" + ldId + '\'' +
        ", ldType='" + ldType + '\'' +
        ", type='" + type + '\'' +
        ", name='" + name + '\'' +
        ", isFlat=" + isFlat +
        ", details=" + details +
        '}';
  }
}