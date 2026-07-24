package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.model.OWLOntology;

import java.io.File;
import java.sql.SQLException;

/**
 * Extracts a subsumption hierarchy from an ontology document into a {@link SnapshotStore}.
 *
 * Implementations differ by the source vocabulary's modelling: {@link OwlHierarchyExtractor} reads
 * OWL/OBO {@code rdfs:subClassOf}; {@link SkosHierarchyExtractor} reads SKOS {@code skos:broader}.
 * {@link IngestJob} selects the implementation by the submission's declared format.
 */
public interface HierarchyExtractor {

  /** Counts produced by an extraction run. */
  record Result(int classCount, int edgeCount) {}

  /** Extracts from an already-loaded ontology into the (freshly initialized) store. */
  Result extract(OWLOntology ont, SnapshotStore store) throws SQLException;

  /** Loads an ontology document and extracts it into the store. */
  Result extractFromFile(File file, SnapshotStore store) throws Exception;
}
