package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourcePresentationTest {
  @Test void onlyTheBioPortalAdapterInheritsBioPortalPublicPages() {
    assertFalse(new BioPortalDownloader("test-key").presentation("EX").links().isEmpty());
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
      assertEquals(iri, profile.resourceLinks().get(iri).get(0).href());
    }
    assertTrue(profile.resourceLinks().get(EpaPfasSource.COMBINED_IRI).isEmpty());
    assertEquals("CompTox", profile.links().get("term").get(0).label());
    assertEquals("current", profile.links().get("term").get(0).versionScope());
  }
}
