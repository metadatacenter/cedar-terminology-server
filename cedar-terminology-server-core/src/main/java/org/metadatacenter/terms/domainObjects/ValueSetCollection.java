package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.wordnik.swagger.annotations.ApiModel;

import static org.metadatacenter.terms.util.Constants.BP_TYPE_BASE;
import static org.metadatacenter.terms.util.Constants.BP_TYPE_VS_COLLECTION;

@ApiModel
@JsonPropertyOrder({"id", "@id", "@type", "type", "name", "details"})
public class ValueSetCollection {
  private String id;
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String ldType = BP_TYPE_BASE + BP_TYPE_VS_COLLECTION;
  private String type = BP_TYPE_VS_COLLECTION;
  private String name;
  private ValueSetCollectionDetails details;

  public ValueSetCollection() {
  }

  public ValueSetCollection(String id, String ldId, String name, ValueSetCollectionDetails details) {
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

  public ValueSetCollectionDetails getDetails() {
    return details;
  }

  public void setDetails(ValueSetCollectionDetails details) {
    this.details = details;
  }
}