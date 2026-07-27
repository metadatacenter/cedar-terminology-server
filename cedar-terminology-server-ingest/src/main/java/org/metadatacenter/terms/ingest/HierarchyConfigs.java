package org.metadatacenter.terms.ingest;

import org.semanticweb.owlapi.model.IRI;

import java.util.Optional;
import java.util.Set;

/**
 * Per-ontology hierarchy overrides. Some ontologies do not express their hierarchy the way their
 * format's default would suggest: RxNorm (format UMLS) has almost no {@code rdfs:subClassOf}; its
 * backbone is an {@code isa} object property with ~107k edges. An entry here maps such an ontology
 * to the {@link HierarchyConfig} that recovers its real hierarchy.
 *
 * An ontology with no entry falls back to the default for its format (SKOS -> skos:broader;
 * OWL/OBO/UMLS -> rdfs:subClassOf).
 */
public final class HierarchyConfigs {

  private static final IRI SKOS_PREF_LABEL = IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel");
  private static final IRI RXNORM_ISA = IRI.create("http://purl.bioontology.org/ontology/RXNORM/isa");

  private HierarchyConfigs() {
  }

  public static Optional<HierarchyConfig> forOntology(String acronym) {
    if ("RXNORM".equalsIgnoreCase(acronym)) {
      // RxNorm's hierarchy is the isa relation, not subClassOf; labels are skos:prefLabel. Retain
      // its other IRI-valued relations (has_ingredient, has_dose_form, tradename_of, ...) as Level-1
      // typed relations so ingredients/products (which have no isa parent) remain reachable.
      return Optional.of(new HierarchyConfig(Set.of(RXNORM_ISA), Set.of(), SKOS_PREF_LABEL, "subsumption", true));
    }
    return Optional.empty();
  }
}
