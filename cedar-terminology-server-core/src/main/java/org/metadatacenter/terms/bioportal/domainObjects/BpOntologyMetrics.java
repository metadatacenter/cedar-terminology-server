package org.metadatacenter.terms.bioportal.domainObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpOntologyMetrics {

  private String id;
  private String created;
  private String classes;
  private String individuals;
  private String properties;
  private String maxDepth;
  private String maxChildCount;
  private String averageChildCount;
  private String classesWithOneChild;
  private String classesWithMoreThan25Children;
  private String classesWithNoDefinition;

  public BpOntologyMetrics() {
  }

  public BpOntologyMetrics(String id, String created, String classes, String individuals, String properties, String
      maxDepth, String maxChildCount, String averageChildCount, String classesWithOneChild, String
      classesWithMoreThan25Children, String classesWithNoDefinition) {
    this.id = id;
    this.created = created;
    this.classes = classes;
    this.individuals = individuals;
    this.properties = properties;
    this.maxDepth = maxDepth;
    this.maxChildCount = maxChildCount;
    this.averageChildCount = averageChildCount;
    this.classesWithOneChild = classesWithOneChild;
    this.classesWithMoreThan25Children = classesWithMoreThan25Children;
    this.classesWithNoDefinition = classesWithNoDefinition;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCreated() {
    return created;
  }

  public void setCreated(String created) {
    this.created = created;
  }

  public String getClasses() {
    return classes;
  }

  public void setClasses(String classes) {
    this.classes = classes;
  }

  public String getIndividuals() {
    return individuals;
  }

  public void setIndividuals(String individuals) {
    this.individuals = individuals;
  }

  public String getProperties() {
    return properties;
  }

  public void setProperties(String properties) {
    this.properties = properties;
  }

  public String getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(String maxDepth) {
    this.maxDepth = maxDepth;
  }

  public String getMaxChildCount() {
    return maxChildCount;
  }

  public void setMaxChildCount(String maxChildCount) {
    this.maxChildCount = maxChildCount;
  }

  public String getAverageChildCount() {
    return averageChildCount;
  }

  public void setAverageChildCount(String averageChildCount) {
    this.averageChildCount = averageChildCount;
  }

  public String getClassesWithOneChild() {
    return classesWithOneChild;
  }

  public void setClassesWithOneChild(String classesWithOneChild) {
    this.classesWithOneChild = classesWithOneChild;
  }

  public String getClassesWithMoreThan25Children() {
    return classesWithMoreThan25Children;
  }

  public void setClassesWithMoreThan25Children(String classesWithMoreThan25Children) {
    this.classesWithMoreThan25Children = classesWithMoreThan25Children;
  }

  public String getClassesWithNoDefinition() {
    return classesWithNoDefinition;
  }

  public void setClassesWithNoDefinition(String classesWithNoDefinition) {
    this.classesWithNoDefinition = classesWithNoDefinition;
  }

  @Override
  public String toString() {
    return "BpOntologyMetrics{" +
        "id='" + id + '\'' +
        ", created='" + created + '\'' +
        ", classes='" + classes + '\'' +
        ", individuals='" + individuals + '\'' +
        ", properties='" + properties + '\'' +
        ", maxDepth='" + maxDepth + '\'' +
        ", maxChildCount='" + maxChildCount + '\'' +
        ", averageChildCount='" + averageChildCount + '\'' +
        ", classesWithOneChild='" + classesWithOneChild + '\'' +
        ", classesWithMoreThan25Children='" + classesWithMoreThan25Children + '\'' +
        ", classesWithNoDefinition='" + classesWithNoDefinition + '\'' +
        '}';
  }
}
