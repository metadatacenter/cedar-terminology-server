package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BioPortalDownloaderParseTest {

  @Test
  public void parsesSubmissionArray() throws Exception {
    String json = """
        [ {"submissionId":673,"version":"2026-06-30","released":"2026-07-01","hasOntologyLanguage":"OWL"},
          {"submissionId":560,"version":"releases/2016-01-07","released":"2016-01-14","hasOntologyLanguage":"OBO"} ]""";
    List<Submission> subs = BioPortalDownloader.parseSubmissions(json);
    assertEquals(2, subs.size());
    assertEquals(673, subs.get(0).submissionId());
    assertEquals("OWL", subs.get(0).format());
    assertEquals("releases/2016-01-07", subs.get(1).version());
    assertEquals("OBO", subs.get(1).format());
  }

  @Test
  public void toleratesMissingFields() throws Exception {
    String json = """
        [ {"submissionId":5} ]""";
    List<Submission> subs = BioPortalDownloader.parseSubmissions(json);
    assertEquals(1, subs.size());
    assertEquals(5, subs.get(0).submissionId());
    assertEquals(null, subs.get(0).version());
    assertEquals(null, subs.get(0).format());
  }

  @Test
  public void emptyArrayYieldsNoSubmissions() throws Exception {
    assertTrue(BioPortalDownloader.parseSubmissions("[]").isEmpty());
  }
}
