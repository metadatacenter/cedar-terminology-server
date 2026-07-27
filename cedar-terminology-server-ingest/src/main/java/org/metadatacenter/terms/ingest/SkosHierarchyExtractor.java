package org.metadatacenter.terms.ingest;

/**
 * SKOS preset of {@link RelationHierarchyExtractor}: hierarchy from {@code skos:broader}/
 * {@code narrower}, labels from {@code skos:prefLabel}. Retained for convenience and readability at
 * call sites; equivalent to {@code new RelationHierarchyExtractor(HierarchyConfig.skos())}.
 */
public class SkosHierarchyExtractor extends RelationHierarchyExtractor {

  public SkosHierarchyExtractor() {
    super(HierarchyConfig.skos());
  }
}
