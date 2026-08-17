package org.metadatacenter.terms.ingest;

import org.semanticweb.owlapi.model.IRI;

import java.util.Map;

/**
 * The properties under which a source states what a concept means.
 *
 * A definition is the thing that tells two identically-labelled terms apart, and the corpus is full
 * of them: GENEPIO offers "disease" three times over from three upstream vocabularies, and nothing
 * in a row distinguishes them but an accession. Measured over a sample of the store, about half the
 * ontologies assert one.
 *
 * Four properties do most of the work — the OBO definition annotation, SKOS, Dublin Core's
 * description, and the OBO flat-file {@code def:}, which obo2owl renders as the first of these. They
 * are not enough alone: a large vocabulary often mints its own, and NCIT's {@code DEFINITION} and
 * {@code ALT_DEFINITION} carry 46,000 definitions between them. Those are listed beside the standard
 * set, the way {@link HierarchyConfig} lists a vocabulary's own hierarchy predicates.
 *
 * {@code rdfs:comment} is deliberately absent. It is commoner than any of these — two files in five
 * carry one — and it is an editorial note rather than a definition, so taking it would fill an
 * author's panel with remarks addressed to curators.
 */
final class DefinitionProperties {

  private static final String OBO = "http://purl.obolibrary.org/obo/";
  private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
  private static final String DCTERMS = "http://purl.org/dc/terms/";
  private static final String DC = "http://purl.org/dc/elements/1.1/";
  private static final String NCIT = "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#";

  /** Property IRI -> CURIE, for every literal-valued definition property captured. */
  private static final Map<IRI, String> CURIES = Map.ofEntries(
      // The OBO definition annotation, and what obo2owl renders a flat file's `def:` as.
      Map.entry(IRI.create(OBO + "IAO_0000115"), "IAO:0000115"),
      Map.entry(IRI.create(SKOS + "definition"), "skos:definition"),
      Map.entry(IRI.create(DCTERMS + "description"), "dcterms:description"),
      Map.entry(IRI.create(DC + "description"), "dc:description"),
      // NCIT's own, which no standard property covers.
      Map.entry(IRI.create(NCIT + "DEFINITION"), "NCIT:DEFINITION"),
      Map.entry(IRI.create(NCIT + "ALT_DEFINITION"), "NCIT:ALT_DEFINITION"));

  private DefinitionProperties() {
  }

  /** The CURIE for a captured definition property, or {@code null} if this property is not one. */
  static String curieFor(IRI property) {
    return CURIES.get(property);
  }
}
