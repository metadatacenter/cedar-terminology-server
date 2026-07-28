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
  private static final IRI PART_OF = IRI.create("http://purl.obolibrary.org/obo/BFO_0000050");
  private static final IRI DEVELOPS_FROM = IRI.create("http://purl.obolibrary.org/obo/RO_0002202");
  private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
  private static final IRI SKOS_BROADER = IRI.create(SKOS + "broader");
  private static final IRI SKOS_BROADER_TRANSITIVE = IRI.create(SKOS + "broaderTransitive");
  private static final IRI SKOS_NARROWER = IRI.create(SKOS + "narrower");
  private static final IRI SKOS_IN_SCHEME = IRI.create(SKOS + "inScheme");
  private static final IRI SKOS_TOP_CONCEPT_OF = IRI.create(SKOS + "topConceptOf");
  private static final IRI SKOS_PREF_LABEL_IRI = IRI.create(SKOS + "prefLabel");

  private HierarchyConfigs() {
  }

  public static Optional<HierarchyConfig> forOntology(String acronym) {
    if ("RXNORM".equalsIgnoreCase(acronym)) {
      // RxNorm's hierarchy is the isa relation, not subClassOf; labels are skos:prefLabel. Retain
      // its other IRI-valued relations (has_ingredient, has_dose_form, tradename_of, ...) as Level-1
      // typed relations so ingredients/products (which have no isa parent) remain reachable.
      return Optional.of(new HierarchyConfig(Set.of(RXNORM_ISA), Set.of(), SKOS_PREF_LABEL, "subsumption", true));
    }
    if ("GDMT".equalsIgnoreCase(acronym)) {
      // GDMT organises value lists as skos:ConceptSchemes whose members attach by skos:inScheme /
      // topConceptOf, not skos:broader — so browsing a scheme (ContributorRole, MIMEType, …) needs
      // those as broader-style edges (member -> scheme) on top of the usual SKOS broader/narrower.
      return Optional.of(new HierarchyConfig(
          Set.of(SKOS_BROADER, SKOS_BROADER_TRANSITIVE, SKOS_IN_SCHEME, SKOS_TOP_CONCEPT_OF),
          Set.of(SKOS_NARROWER), SKOS_PREF_LABEL_IRI, "subsumption", false));
    }
    return Optional.empty();
  }

  /**
   * Object properties whose {@code some Filler} restrictions the OWL extractor should treat as
   * hierarchy edges, in addition to {@code rdfs:subClassOf}, for a partonomy ontology. BTO (BRENDA
   * Tissue) is one: its {@code is_a} tree is nearly empty and BioPortal's browse is built from
   * {@code part_of} + {@code develops_from} (verified — those two plus is_a reproduce BioPortal's
   * descendant counts exactly). An ontology with no entry uses subsumption only.
   */
  public static Optional<Set<IRI>> owlHierarchyProperties(String acronym) {
    if ("BTO".equalsIgnoreCase(acronym)) {
      return Optional.of(Set.of(PART_OF, DEVELOPS_FROM));
    }
    if ("MS".equalsIgnoreCase(acronym)) {
      // PSI-MS is a partonomy too: its instrument/acquisition branches hang off part_of, not is_a
      // (verified — is_a + part_of reproduces BioPortal's descendant counts exactly).
      return Optional.of(Set.of(PART_OF));
    }
    return Optional.empty();
  }
}
