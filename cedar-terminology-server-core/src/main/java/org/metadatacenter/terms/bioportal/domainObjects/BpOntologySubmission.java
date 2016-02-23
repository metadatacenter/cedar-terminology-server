package org.metadatacenter.terms.bioportal.domainObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpOntologySubmission {

  private String hasOntologyLanguage;
  private String released;
  private String creationDate;
  private String homepage;
  private String publication;
  private String documentation;
  private String version;
  private String description;

  public BpOntologySubmission() {
  }

  public BpOntologySubmission(String hasOntologyLanguage, String released, String creationDate, String homepage,
                              String publication, String documentation, String version, String description) {
    this.hasOntologyLanguage = hasOntologyLanguage;
    this.released = released;
    this.creationDate = creationDate;
    this.homepage = homepage;
    this.publication = publication;
    this.documentation = documentation;
    this.version = version;
    this.description = description;
  }

  public String getHasOntologyLanguage() {
    return hasOntologyLanguage;
  }

  public void setHasOntologyLanguage(String hasOntologyLanguage) {
    this.hasOntologyLanguage = hasOntologyLanguage;
  }

  public String getReleased() {
    return released;
  }

  public void setReleased(String released) {
    this.released = released;
  }

  public String getCreationDate() {
    return creationDate;
  }

  public void setCreationDate(String creationDate) {
    this.creationDate = creationDate;
  }

  public String getHomepage() {
    return homepage;
  }

  public void setHomepage(String homepage) {
    this.homepage = homepage;
  }

  public String getPublication() {
    return publication;
  }

  public void setPublication(String publication) {
    this.publication = publication;
  }

  public String getDocumentation() {
    return documentation;
  }

  public void setDocumentation(String documentation) {
    this.documentation = documentation;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
