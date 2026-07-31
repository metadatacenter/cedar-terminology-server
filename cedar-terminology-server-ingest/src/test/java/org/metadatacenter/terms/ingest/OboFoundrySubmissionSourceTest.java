package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No-network tests for the OBO Foundry source: PURL addressing and the open-access / single-submission
 * shape. The actual fetch + cross-source identity comparison is exercised live by
 * {@link CrossSourceIdentityCheck}, which needs the network and both authorities.
 */
public class OboFoundrySubmissionSourceTest {

  @Test
  public void currentReleaseUrlIsTheBarePurl() {
    assertEquals("http://purl.obolibrary.org/obo/doid.owl",
        new OboFoundrySubmissionSource().downloadUrl("DOID"));
  }

  @Test
  public void datedReleaseUrlTargetsTheReleasesDirectory() {
    assertEquals("http://purl.obolibrary.org/obo/doid/releases/2024-05-29/doid.owl",
        new OboFoundrySubmissionSource("2024-05-29").downloadUrl("DOID"));
  }

  @Test
  public void acronymIsLowercasedInThePurl() {
    assertEquals("http://purl.obolibrary.org/obo/go.owl",
        new OboFoundrySubmissionSource().downloadUrl("GO"));
  }

  @Test
  public void blankReleaseDateFallsBackToTheCurrentRelease() {
    assertEquals("http://purl.obolibrary.org/obo/hp.owl",
        new OboFoundrySubmissionSource("   ").downloadUrl("HP"));
  }

  @Test
  public void reportsItsBackendAndOpenAccess() {
    OboFoundrySubmissionSource source = new OboFoundrySubmissionSource();
    assertEquals("obofoundry", source.backendId());
    assertTrue(source.accessInfo("DOID").isPublic()); // OBO Foundry is open by definition
  }

  @Test
  public void reportsASingleSyntheticSubmission() {
    OboFoundrySubmissionSource source = new OboFoundrySubmissionSource("2024-05-29");
    List<Submission> subs = source.listSubmissions("DOID");
    assertEquals(1, subs.size());
    Submission only = subs.get(0);
    assertEquals("OWL", only.format());
    assertEquals("2024-05-29", only.version()); // the release the source is pointed at
    assertEquals(only.submissionId(), source.latestSubmission("DOID").submissionId());
  }
}
