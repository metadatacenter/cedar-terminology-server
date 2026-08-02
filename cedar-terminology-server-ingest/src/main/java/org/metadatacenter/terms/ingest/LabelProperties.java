package org.metadatacenter.terms.ingest;

import org.semanticweb.owlapi.model.IRI;

import java.util.Map;

/**
 * The annotation properties whose literal values are a concept's names: its labels and its
 * synonyms. Each maps to a compact CURIE recorded alongside the literal (and its language tag) in a
 * snapshot's {@code label} table, so a multilingual ontology's labels are preserved rather than
 * collapsed to the single served {@code pref_label}. This is exactly BioPortal's label surface: the
 * default {@code prefLabel}/{@code synonym} come from these properties, and {@code lang=all} exposes
 * every language variant.
 *
 * The set is the label proper ({@code rdfs:label}, {@code skos:prefLabel}) plus the synonym
 * properties BioPortal serves: SKOS {@code altLabel}/{@code hiddenLabel} and the OBO-in-OWL synonym
 * scopes. Capturing is additive and never changes which literal becomes {@code pref_label} — that
 * selection (and therefore content identity) is unchanged.
 */
final class LabelProperties {

  private static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";
  private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
  private static final String OBO_IN_OWL = "http://www.geneontology.org/formats/oboInOwl#";

  /** Property IRI -> CURIE, for every literal-valued name property captured into the label table. */
  private static final Map<IRI, String> CURIES = Map.ofEntries(
      Map.entry(IRI.create(RDFS + "label"), "rdfs:label"),
      Map.entry(IRI.create(SKOS + "prefLabel"), "skos:prefLabel"),
      Map.entry(IRI.create(SKOS + "altLabel"), "skos:altLabel"),
      Map.entry(IRI.create(SKOS + "hiddenLabel"), "skos:hiddenLabel"),
      Map.entry(IRI.create(OBO_IN_OWL + "hasExactSynonym"), "oboInOwl:hasExactSynonym"),
      Map.entry(IRI.create(OBO_IN_OWL + "hasRelatedSynonym"), "oboInOwl:hasRelatedSynonym"),
      Map.entry(IRI.create(OBO_IN_OWL + "hasBroadSynonym"), "oboInOwl:hasBroadSynonym"),
      Map.entry(IRI.create(OBO_IN_OWL + "hasNarrowSynonym"), "oboInOwl:hasNarrowSynonym"),
      Map.entry(IRI.create(OBO_IN_OWL + "hasSynonym"), "oboInOwl:hasSynonym"));

  private LabelProperties() { }

  /** The CURIE for a captured name property, or {@code null} if this property is not one. */
  static String curieFor(IRI property) {
    return CURIES.get(property);
  }
}
