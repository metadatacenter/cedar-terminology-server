package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourcePresentationTest {
  @Test void onlyTheBioPortalAdapterSuppliesBioPortalPublicPages() {
    var profile = new BioPortalDownloader("test-key").presentation("EX");
    assertEquals(BioPortalDownloader.publicLinks(), profile);
    for (String type : java.util.List.of("ontology", "valueSet", "class", "branch")) {
      assertTrue(profile.links().get(type).landingPage().startsWith("https://bioportal.bioontology.org/ontologies/{sourceAcronym}"));
      assertEquals("{serviceBase}/resources/icons/bioportal.png", profile.links().get(type).icon());
    }
    assertNull(profile.links().get("property"));
    assertTrue(new BioPortalDownloader("test-key", "https://new-api.example")
        .presentation("EX").links().isEmpty());
    assertTrue(new DirectUrlSubmissionSource("https://new-api.example/vocabulary.owl", "OWL", "future")
        .presentation("EX").links().isEmpty());
  }

  @Test void epaDeclaresTwoIndependentListsAndNoInventedUnionPage() {
    var profile = EpaPfasSource.presentation();
    assertEquals("epa", profile.sourceSystem());
    for (String id : java.util.List.of("477", "475")) {
      String iri = "https://cdxapps.epa.gov/oms-substance-registry-services/substance-list-details/" + id;
      assertEquals(iri, profile.resourceLinks().get(iri).landingPage());
    }
    assertNull(profile.resourceLinks().get(EpaPfasSource.COMBINED_IRI).landingPage());
    assertEquals("{termIriRaw}", profile.links().get("class").landingPage());
    assertNull(profile.links().get("class").icon());
  }
}
