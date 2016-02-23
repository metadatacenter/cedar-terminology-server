package org.metadatacenter.terms.bioportal.domainObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpOntologyMetrics {

  private String id;
  private String created;
  private int classes;
  private int individuals;
  private int properties;
  private int maxDepth;
  private int maxChildCount;
  private int averageChildCount;
  private int classesWithOneChild;
  private int classesWithMoreThan25Children;
  private int classesWithNoDefinition;

  public BpOntologyMetrics() {
  }

  public BpOntologyMetrics(String id, String created, int classes, int individuals, int properties, int maxDepth, int
      maxChildCount, int averageChildCount, int classesWithOneChild, int classesWithMoreThan25Children, int
                               classesWithNoDefinition) {
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

  public int getClasses() {
    return classes;
  }

  public void setClasses(int classes) {
    this.classes = classes;
  }

  public int getIndividuals() {
    return individuals;
  }

  public void setIndividuals(int individuals) {
    this.individuals = individuals;
  }

  public int getProperties() {
    return properties;
  }

  public void setProperties(int properties) {
    this.properties = properties;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public void setMaxDepth(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public int getMaxChildCount() {
    return maxChildCount;
  }

  public void setMaxChildCount(int maxChildCount) {
    this.maxChildCount = maxChildCount;
  }

  public int getAverageChildCount() {
    return averageChildCount;
  }

  public void setAverageChildCount(int averageChildCount) {
    this.averageChildCount = averageChildCount;
  }

  public int getClassesWithOneChild() {
    return classesWithOneChild;
  }

  public void setClassesWithOneChild(int classesWithOneChild) {
    this.classesWithOneChild = classesWithOneChild;
  }

  public int getClassesWithMoreThan25Children() {
    return classesWithMoreThan25Children;
  }

  public void setClassesWithMoreThan25Children(int classesWithMoreThan25Children) {
    this.classesWithMoreThan25Children = classesWithMoreThan25Children;
  }

  public int getClassesWithNoDefinition() {
    return classesWithNoDefinition;
  }

  public void setClassesWithNoDefinition(int classesWithNoDefinition) {
    this.classesWithNoDefinition = classesWithNoDefinition;
  }
}
