package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectUrlSubmissionSourceTest {

  @Test
  public void defaults_owlFormatAndUrlBackend() {
    DirectUrlSubmissionSource s = new DirectUrlSubmissionSource("https://ex.org/hp.owl");
    assertEquals("url", s.backendId());
    assertTrue(s.accessInfo("HP").isPublic());
    List<Submission> subs = s.listSubmissions("HP");
    assertEquals(1, subs.size());
    assertEquals("OWL", subs.get(0).format());
    assertEquals(DirectUrlSubmissionSource.SYNTHETIC_SUBMISSION_ID, subs.get(0).submissionId());
  }

  @Test
  public void carriesFormatAndBackend() {
    DirectUrlSubmissionSource s =
        new DirectUrlSubmissionSource("https://ex.org/agrovoc.ttl", "SKOS", "agroportal");
    assertEquals("agroportal", s.backendId());
    assertEquals("SKOS", s.latestSubmission("AGROVOC").format());
  }

  @Test
  public void fileSuffix_preservesSerializationAndCompressionExtension() {
    assertEquals(".owl", new DirectUrlSubmissionSource("https://ex.org/a/hp.owl").fileSuffix());
    assertEquals(".obo", new DirectUrlSubmissionSource("https://ex.org/go.obo").fileSuffix());
    assertEquals(".ttl", new DirectUrlSubmissionSource("https://ex.org/x.ttl").fileSuffix());
    assertEquals(".owl.gz", new DirectUrlSubmissionSource("https://ex.org/hp.owl.gz").fileSuffix());
    // No file extension -> default .owl; OWLAPI detects the real serialization from the content.
    assertEquals(".owl", new DirectUrlSubmissionSource("https://ex.org/download").fileSuffix());
  }

  @Test
  public void blankUrl_rejected() {
    assertThrows(IllegalArgumentException.class, () -> new DirectUrlSubmissionSource("  "));
  }
}
