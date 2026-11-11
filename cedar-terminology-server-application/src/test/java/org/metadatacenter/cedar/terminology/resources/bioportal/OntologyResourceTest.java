package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.domainObjects.Ontology;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.metadatacenter.cedar.terminology.resources.FakeBioPortal.*;

/**
 * The ontology listing. Reading one ontology, its root classes and its root properties is covered by
 * {@code BioPortalSuccessPathsTest.anOntologyIsListedAndDescribed}.
 */
public class OntologyResourceTest extends AbstractTerminologyServerResourceTest {

  @Test
  public void findAllOntologiesTest() {
    List<Ontology> ontologies = read(baseUrlBpOntologies, new GenericType<>() {});
    Map<String, String> names = ontologies.stream().collect(Collectors.toMap(Ontology::getId, Ontology::getName));
    // The registry also lists the three value-set collections, which are left out.
    Assertions.assertEquals(2 + FILLERS, ontologies.size(), "Every ontology should be listed: " + names.keySet());
    Assertions.assertEquals("Test Ontology", names.get(ONTOLOGY));
    Assertions.assertEquals("Virus Ontology", names.get(VIRUSES));
    for (String collection : List.of(CEDARVS, NLMVS, VS_COLLECTION)) {
      Assertions.assertFalse(names.containsKey(collection), "A value-set collection was listed: " + collection);
    }
  }

}
