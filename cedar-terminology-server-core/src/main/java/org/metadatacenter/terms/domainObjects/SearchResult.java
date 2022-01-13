package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
//import com.wordnik.swagger.annotations.ApiModel;
//import com.wordnik.swagger.annotations.ApiModelProperty;

import java.util.List;

//@ApiModel
@JsonPropertyOrder({"id", "@id", "@type", "type", "prefLabel", "notation", "definition", "source"})
public class SearchResult {
  private String id;
  @JsonProperty("@id")
  private String ldId;
  @JsonProperty("@type")
  private String ldType;
  private String type;
  private String prefLabel;
  private String notation;
  private String definition;
  private String source;
  private String matchType;
  private List<String> matchedSynonyms;

  // The default constructor is used by Jackson for deserialization
  public SearchResult() {
  }

  public SearchResult(String id, String ldId, String ldType, String type, String prefLabel, String notation,
                      String definition, String source, String matchType, List<String> matchedSynonyms) {
    this.id = id;
    this.ldId = ldId;
    this.ldType = ldType;
    this.type = type;
    this.prefLabel = prefLabel;
    this.notation = notation;
    this.definition = definition;
    this.source = source;
    this.matchType = matchType;
    this.matchedSynonyms = matchedSynonyms;
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

  public String getPrefLabel() {
    return prefLabel;
  }

  public void setPrefLabel(String prefLabel) {
    this.prefLabel = prefLabel;
  }

  public String getNotation() {
    return notation;
  }

  public void setNotation(String notation) {
    this.notation = notation;
  }

  public String getDefinition() {
    return definition;
  }

  public void setDefinition(String definition) {
    this.definition = definition;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getMatchType() { return matchType; }

  public void setMatchType(String matchType) { this.matchType = matchType; }

  public List<String> getMatchedSynonyms() { return matchedSynonyms; }

  public void setMatchedSynonyms(List<String> matchedSynonyms) { this.matchedSynonyms = matchedSynonyms; }
}
