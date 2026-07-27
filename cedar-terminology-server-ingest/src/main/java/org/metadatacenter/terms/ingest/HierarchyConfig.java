package org.metadatacenter.terms.ingest;

import org.semanticweb.owlapi.model.IRI;

import java.util.Set;

/**
 * Which RDF predicates encode the hierarchy for a relation-based vocabulary, and in which
 * direction. Used by {@link RelationHierarchyExtractor} to extract from vocabularies whose
 * hierarchy is expressed as plain triples between resources (annotation assertions with IRI
 * values) rather than {@code rdfs:subClassOf} axioms.
 *
 * {@code broaderPredicates} point from a concept to a broader one (child to parent);
 * {@code narrowerPredicates} are their inverse (parent to child). This covers SKOS
 * ({@code skos:broader}/{@code narrower}) and UMLS-style vocabularies whose backbone is an
 * {@code isa} object property (e.g. RxNorm), which BioPortal serializes the same way.
 */
public record HierarchyConfig(
    Set<IRI> broaderPredicates,
    Set<IRI> narrowerPredicates,
    IRI labelPredicate,
    String hierarchyStatus,
    boolean retainRelations) {

  private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";

  /** Standard SKOS: skos:broader / broaderTransitive (up), skos:narrower (down), skos:prefLabel. */
  public static HierarchyConfig skos() {
    return new HierarchyConfig(
        Set.of(IRI.create(SKOS + "broader"), IRI.create(SKOS + "broaderTransitive")),
        Set.of(IRI.create(SKOS + "narrower")),
        IRI.create(SKOS + "prefLabel"),
        "subsumption",
        false);
  }
}
