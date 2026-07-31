package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.CatalogStore.OntologyInfo;
import org.metadatacenter.terms.store.CatalogStore.SnapshotInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The identity invariant: a canonical iri identifies at most one content-distinct ontology. Covers
 * the pure decision ({@link IriDeconfliction#toDecline}) and a full pass over an in-memory catalog.
 */
public class IriDeconflictionTest {

  private CatalogStore catalog;

  @BeforeEach
  public void setUp() throws Exception {
    catalog = CatalogStore.openInMemory();
    catalog.initSchema();
  }

  @AfterEach
  public void tearDown() throws Exception {
    catalog.close();
  }

  // ---- pure policy --------------------------------------------------------------------------

  @Test
  public void toDecline_trueDuplicateKeepsAll() {
    Map<String, String> sharers = new LinkedHashMap<>();
    sharers.put("INCENTIVE", "hashX");
    sharers.put("INCENTIVE-VARS", "hashX"); // identical content
    assertEquals(List.of(), IriDeconfliction.toDecline("http://x/incentive", sharers));
  }

  @Test
  public void toDecline_oboConflictKeepsOwnerDeclinesImporters() {
    Map<String, String> sharers = new LinkedHashMap<>();
    sharers.put("PO", "hashPO");
    sharers.put("GRO-CPGA", "hashGro");
    sharers.put("NMDCO", "hashNmd"); // larger, but still an importer
    // Only PO owns obo/po; the importers are declined regardless of size.
    assertEquals(List.of("GRO-CPGA", "NMDCO"),
        IriDeconfliction.toDecline("http://purl.obolibrary.org/obo/po", sharers));
  }

  @Test
  public void toDecline_placeholderBaseWithNoOwnerDeclinesAll() {
    Map<String, String> sharers = new LinkedHashMap<>();
    sharers.put("NIST_GEL", "h1");
    sharers.put("COPDO", "h2");
    sharers.put("CSO", "h3");
    // Non-OBO base, no derivable owner ⇒ none may claim it.
    assertEquals(List.of("COPDO", "CSO", "NIST_GEL"),
        IriDeconfliction.toDecline("http://webprotege.stanford.edu", sharers));
  }

  // ---- full pass over a catalog -------------------------------------------------------------

  /** Registers an ontology with a latest snapshot and a derived iri. */
  private void ontology(String acronym, String iri, String versionId) throws Exception {
    catalog.upsertOntology(new OntologyInfo(acronym, acronym, null, "OWL"));
    catalog.addSnapshot(new SnapshotInfo(versionId, acronym, "1.0", "2025-01-01", "2025-01-02T00:00:00Z",
        "OWL", "subsumption", 10, 5, "/s/" + acronym + ".sqlite", versionId, "open"));
    catalog.setTag(acronym, CatalogStore.TAG_LATEST, versionId);
    catalog.setOntologyIri(acronym, iri, iri + "/");
  }

  @Test
  public void run_resolvesEveryShapeAndPrunesOrphans() throws Exception {
    String po = "http://purl.obolibrary.org/obo/po";
    String wp = "http://webprotege.stanford.edu";
    String inc = "http://x/incentive";
    String doid = "http://purl.obolibrary.org/obo/doid";

    ontology("PO", po, "vPO");             // OBO owner
    ontology("GRO-CPGA", po, "vGro");      // imports PO — content-distinct
    ontology("NIST_GEL", wp, "vN1");       // placeholder base, no owner
    ontology("COPDO", wp, "vN2");
    ontology("INCENTIVE", inc, "vShared"); // true duplicate pair (same content)
    ontology("INCENTIVE-VARS", inc, "vShared");
    ontology("DOID", doid, "vDOID");       // sole holder, untouched

    IriDeconfliction.Result r = IriDeconfliction.run(catalog, true);

    assertEquals(3, r.sharedIris());            // po, webprotege, incentive
    assertEquals(1, r.duplicates());            // incentive
    assertEquals(1, r.conflictsWithOwner());    // po
    assertEquals(1, r.conflictsNoOwner());      // webprotege
    assertEquals(3, r.acronymsDeclined());      // GRO-CPGA, NIST_GEL, COPDO

    // PO keeps obo/po; its importer is now acronym-only.
    assertEquals(po, catalog.ontologyIri("PO").orElseThrow());
    assertTrue(catalog.ontologyIri("GRO-CPGA").isEmpty());
    // The placeholder base is claimed by nobody.
    assertTrue(catalog.ontologyIri("NIST_GEL").isEmpty());
    assertTrue(catalog.ontologyIri("COPDO").isEmpty());
    assertEquals(List.of(), catalog.acronymsForIri(wp));
    // The true duplicate is preserved.
    assertEquals(List.of("INCENTIVE", "INCENTIVE-VARS"), catalog.acronymsForIri(inc));
    // The sole holder is untouched.
    assertEquals(doid, catalog.ontologyIri("DOID").orElseThrow());

    // Identity rows: po, incentive, doid survive; the webprotege orphan is pruned.
    assertTrue(r.orphanIdentitiesPruned() >= 1);
    assertEquals(List.of(doid, po, inc), catalog.listOntologyIdentities()); // ascending by iri string
  }

  @Test
  public void run_dryRunReportsWithoutMutating() throws Exception {
    String po = "http://purl.obolibrary.org/obo/po";
    ontology("PO", po, "vPO");
    ontology("GRO-CPGA", po, "vGro");

    IriDeconfliction.Result r = IriDeconfliction.run(catalog, false);
    assertEquals(1, r.conflictsWithOwner());
    assertEquals(1, r.acronymsDeclined());
    assertEquals(0, r.orphanIdentitiesPruned());
    // Nothing changed.
    assertEquals(po, catalog.ontologyIri("GRO-CPGA").orElseThrow());
    assertFalse(catalog.acronymsForIri(po).isEmpty());
  }
}
