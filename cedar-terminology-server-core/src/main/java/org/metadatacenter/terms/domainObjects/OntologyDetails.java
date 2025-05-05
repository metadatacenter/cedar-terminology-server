package org.metadatacenter.terms.domainObjects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.util.List;

@JsonPropertyOrder({"description", "numberOfClasses", "categories", "hasOntologyLanguage", "released",
    "creationDate", "homepage", "publication", "documentation", "version", "metricsAvailable", "hasSubmissions"})
public class OntologyDetails implements Serializable {

  private String description;
  private int numberOfClasses;
  private List<String> categories;
  private String hasOntologyLanguage;
  private String released;
  private String creationDate;
  private String homepage;
  private List<String> publication;
  private String documentation;
  private String version;
  private boolean metricsAvailable;
  private boolean hasSubmissions;

  public OntologyDetails() {
  }

  public OntologyDetails(String description, int numberOfClasses, List<String> categories, String
      hasOntologyLanguage, String released, String creationDate, String homepage, List<String> publication, String
                             documentation, String version, boolean metricsAvailable, boolean hasSubmissions) {
    this.description = description;
    this.numberOfClasses = numberOfClasses;
    this.categories = categories;
    this.hasOntologyLanguage = hasOntologyLanguage;
    this.released = released;
    this.creationDate = creationDate;
    this.homepage = homepage;
    this.publication = publication;
    this.documentation = documentation;
    this.version = version;
    this.metricsAvailable = metricsAvailable;
    this.hasSubmissions = hasSubmissions;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getNumberOfClasses() {
    return numberOfClasses;
  }

  public void setNumberOfClasses(int numberOfClasses) {
    this.numberOfClasses = numberOfClasses;
  }

  public List<String> getCategories() {
    return categories;
  }

  public void setCategories(List<String> categories) {
    this.categories = categories;
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

  public List<String> getPublication() {
    return publication;
  }

  public void setPublication(List<String> publication) {
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

  public boolean getMetricsAvailable() { return metricsAvailable; }

  public void setMetricsAvailable(boolean metricsAvailable) { this.metricsAvailable = metricsAvailable; }

  public boolean getHasSubmissions() { return hasSubmissions; }

  public void setHasSubmissions(boolean hasSubmissions) { this.hasSubmissions = hasSubmissions; }

}