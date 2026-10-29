package org.metadatacenter.terms.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.terms.store.SnapshotStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Reads an archived EPA collection without contacting EPA. Edges mean set membership, not subclass. */
final class EpaPfasExtractor {
  static HierarchyExtractor.Result extract(Path file, SnapshotStore store) throws Exception {
    JsonNode archive = new ObjectMapper().readTree(file.toFile());
    if (!EpaPfasSource.FORMAT.equals(archive.path("format").asText())
        || archive.path("schemaVersion").asInt() != 1
        || !EpaPfasSource.COLLECTION_IRI.equals(archive.path("collectionIri").asText())) {
      throw new IOException("Unrecognized EPA PFAS archive");
    }
    EpaPfasSource.requireArray(archive.path("chemicals"), "EPA archive chemicals");
    EpaPfasSource.requireArray(archive.path("sets"), "EPA archive sets");
    Set<String> chemicals = new HashSet<>();
    List<SnapshotStore.LabelRow> labels = new ArrayList<>();
    for (JsonNode chemical : archive.path("chemicals")) {
      String id = EpaPfasSource.dtxsid(chemical);
      String iri = EpaPfasSource.CHEMICAL_IRI + id;
      String label = EpaPfasSource.text(chemical, "label");
      if (!chemicals.add(id) || label.isEmpty()) throw new IOException("Duplicate or unnamed EPA chemical");
      store.addConcept(iri, label);
      EpaPfasSource.requireArray(chemical.path("names"), "EPA chemical names");
      for (JsonNode name : chemical.path("names")) {
        if (!name.isTextual() || name.asText().isBlank()) throw new IOException("Invalid EPA chemical name");
        labels.add(new SnapshotStore.LabelRow(iri, "skos:altLabel", "", name.asText()));
      }
    }
    Set<String> sets = new HashSet<>();
    Set<String> used = new HashSet<>();
    int edges = 0;
    for (JsonNode set : archive.path("sets")) {
      String iri = EpaPfasSource.text(set, "iri"), label = EpaPfasSource.text(set, "label");
      if (!iri.startsWith("https://") || !sets.add(iri) || label.isEmpty()) throw new IOException("Invalid EPA value set");
      store.addConcept(iri, label);
      EpaPfasSource.requireArray(set.path("members"), "EPA value set members");
      Set<String> members = new HashSet<>();
      for (JsonNode member : set.path("members")) {
        String id = member.asText();
        if (!chemicals.contains(id) || !members.add(id)) throw new IOException("Unknown or duplicate EPA value set member");
        store.addEdge(EpaPfasSource.CHEMICAL_IRI + id, iri, "value-set:member");
        used.add(id);
        edges++;
      }
      if (members.isEmpty()) throw new IOException("Empty EPA value set");
    }
    if (sets.size() != 3 || chemicals.isEmpty() || !used.equals(chemicals)) throw new IOException("Incomplete EPA collection");
    store.addLabels(labels);
    store.setMeta(SnapshotStore.HASH_ALL_NAMES, "true");
    store.setMeta("ontology_iri", EpaPfasSource.COLLECTION_IRI);
    store.setMeta(org.metadatacenter.terms.store.SourcePresentation.META_KEY,
        new ObjectMapper().writeValueAsString(EpaPfasSource.presentation()));
    store.properties().write(List.of());
    store.materialize();
    return new HierarchyExtractor.Result(sets.size() + chemicals.size(), edges);
  }
}
