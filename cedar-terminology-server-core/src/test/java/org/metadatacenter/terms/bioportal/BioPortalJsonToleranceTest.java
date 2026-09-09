package org.metadatacenter.terms.bioportal;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.bioportal.domainObjects.BpClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpOntology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.metadatacenter.util.json.JsonMapper.STRICT_MAPPER;
import static org.metadatacenter.util.json.JsonMapper.TOLERANT_MAPPER;

/**
 * Where this service's tolerance for BioPortal's payloads comes from.
 *
 * <p>BioPortal owns these shapes and adds fields to them without warning. Every model used to
 * carry {@code @JsonIgnoreProperties(ignoreUnknown = true)}, which made tolerance a property of
 * the class: a model nobody remembered to annotate was strict by accident. The reads already
 * named the tolerant mapper, so the annotations said nothing the read did not. These tests hold
 * the tolerance where it belongs and would fail if a read moved to the strict policy.
 */
class BioPortalJsonToleranceTest {

  private static final String CLASS_WITH_A_NEW_FIELD = """
      {"@id": "http://purl.obolibrary.org/obo/DOID_4", "prefLabel": "disease",
       "fieldBioPortalAddedLater": "and did not tell us"}
      """;

  @Test
  void aClassCarryingAFieldThisModelDoesNotKnowStillReads() throws Exception {
    BpClass read = TOLERANT_MAPPER.readValue(CLASS_WITH_A_NEW_FIELD, BpClass.class);

    assertEquals("disease", read.getPrefLabel());
  }

  @Test
  void theSameBytesAreRefusedByTheStrictPolicy() {
    assertThrows(UnrecognizedPropertyException.class,
        () -> STRICT_MAPPER.readValue(CLASS_WITH_A_NEW_FIELD, BpClass.class),
        "the tolerance must come from the mapper, or this test proves nothing");
  }

  @Test
  void anOntologyCarryingANewFieldStillReads() throws Exception {
    String ontology = """
        {"@id": "http://data.bioontology.org/ontologies/DOID", "acronym": "DOID",
         "name": "Human Disease Ontology", "fieldBioPortalAddedLater": "and did not tell us"}
        """;

    BpOntology read = TOLERANT_MAPPER.readValue(ontology, BpOntology.class);

    assertEquals("DOID", read.getAcronym());
  }
}
