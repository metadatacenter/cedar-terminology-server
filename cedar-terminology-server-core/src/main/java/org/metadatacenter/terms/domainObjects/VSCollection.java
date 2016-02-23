package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wordnik.swagger.annotations.ApiModel;

@ApiModel
@JsonPropertyOrder({"id", "@id", "@type", "name", "details"})
public class VSCollection {
  private String id;
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String type;
  private String name;
  private VSCollectionDetails details;

  public VSCollection() {
  }

  public VSCollection(String id, String ldId, String type, String name, VSCollectionDetails details) {
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

  public VSCollectionDetails getDetails() {
    return details;
  }

  public void setDetails(VSCollectionDetails details) {
    this.details = details;
  }
}