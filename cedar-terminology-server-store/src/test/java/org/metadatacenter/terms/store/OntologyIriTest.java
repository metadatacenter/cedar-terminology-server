package org.metadatacenter.terms.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Canonical-IRI normalization (VERSIONING-DESIGN §6.4). Cases mirror the design's worked table:
 * DOID, OBI, MESH, EFO, NIFDYS.
 */
public class OntologyIriTest {

  @Test
  public void oboTermPrefix_dropsUnderscoreAndLowercasesId() {
    assertEquals("http://purl.obolibrary.org/obo/doid",
        OntologyIri.canonical("http://purl.obolibrary.org/obo/DOID_"));
    assertEquals("http://purl.obolibrary.org/obo/obi",
        OntologyIri.canonical("http://purl.obolibrary.org/obo/OBI_"));
    // A mixed-case OBO prefix (NCBITaxon_) still folds to all-lowercase.
    assertEquals("http://purl.obolibrary.org/obo/ncbitaxon",
        OntologyIri.canonical("http://purl.obolibrary.org/obo/NCBITaxon_"));
  }

  @Test
  public void nonOboNamespace_stripsTrailingSeparatorPreservingCase() {
    assertEquals("http://purl.bioontology.org/ontology/MESH",
        OntologyIri.canonical("http://purl.bioontology.org/ontology/MESH/"));
    assertEquals("http://www.ebi.ac.uk/efo",
        OntologyIri.canonical("http://www.ebi.ac.uk/efo/"));
    assertEquals("http://uri.neuinfo.org/nif/nifstd",
        OntologyIri.canonical("http://uri.neuinfo.org/nif/nifstd/"));
  }

  @Test
  public void hashNamespace_stripsTrailingHash() {
    assertEquals("http://www.co-ode.org/ontologies/pizza/pizza.owl",
        OntologyIri.canonical("http://www.co-ode.org/ontologies/pizza/pizza.owl#"));
  }

  @Test
  public void alreadyCleanOrEdgeInputsPassThrough() {
    assertEquals("http://www.ebi.ac.uk/efo", OntologyIri.canonical("http://www.ebi.ac.uk/efo")); // no separator
    assertEquals("", OntologyIri.canonical(""));
    assertNull(OntologyIri.canonical(null));
  }
}
