package org.metadatacenter.terms;

import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves an ontology acronym to the {@link SnapshotStore} that currently serves it, backed by a
 * {@link CatalogStore} and gated by an explicit allowlist.
 *
 * An ontology is served locally only when it is both on the allowlist AND has a {@code latest}
 * snapshot in the catalog. The allowlist decouples ingestion from cutover: ingesting an ontology
 * populates the catalog but does not make the server serve it locally until it is allowlisted.
 *
 * Opened snapshot stores are cached by version id, so the {@code latest} pointer can move (a new
 * ingest) and the next resolution opens the new file while the old one stays cached until eviction
 * is added. Reads go through SQLite in its default serialized threading mode; each read creates and
 * closes its own statement, so a cached store is safe to share across request threads.
 */
public class CatalogSnapshotProvider implements SqliteTerminologyService.SnapshotProvider, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(CatalogSnapshotProvider.class);

  private final CatalogStore catalog;
  private final Set<String> allowed;
  // Cached open stores keyed by snapshot file path (unique per snapshot), not by version_id: two
  // ontologies can share a content-hash version_id (INCENTIVE / INCENTIVE-VARS) yet have distinct
  // files, so keying on version_id alone would hand back the wrong ontology's store.
  private final ConcurrentHashMap<String, SnapshotStore> openByFile = new ConcurrentHashMap<>();

  public CatalogSnapshotProvider(CatalogStore catalog, Set<String> allowed) {
    this.catalog = catalog;
    this.allowed = Set.copyOf(allowed);
  }

  @Override
  public Optional<SnapshotStore> forOntology(String ontology) {
    return forOntology(ontology, null);
  }

  @Override
  public Optional<SnapshotStore> forOntology(String ontology, String version) {
    if (ontology == null || !allowed.contains(ontology)) {
      return Optional.empty();
    }
    try {
      Optional<CatalogStore.SnapshotInfo> info = resolveInfo(ontology, version);
      if (info.isEmpty()) {
        return Optional.empty();
      }
      SnapshotStore store = openByFile.computeIfAbsent(info.get().filePath(), this::open);
      return Optional.ofNullable(store);
    } catch (SQLException e) {
      log.warn("Catalog lookup failed for ontology {} version {}; falling back to remote", ontology, version, e);
      return Optional.empty();
    }
  }

  /** Resolves the snapshot for a version: null/blank/"latest" -> current; a known tag -> its target;
   *  otherwise a version_id, scoped to the acronym. */
  private Optional<CatalogStore.SnapshotInfo> resolveInfo(String ontology, String version) throws SQLException {
    if (version == null || version.isBlank() || CatalogStore.TAG_LATEST.equals(version)) {
      return catalog.resolveLatest(ontology);
    }
    Optional<CatalogStore.SnapshotInfo> byTag = catalog.resolve(ontology, version);
    return byTag.isPresent() ? byTag : catalog.resolveVersion(ontology, version);
  }

  @Override
  public List<SqliteTerminologyService.VersionRef> versions(String ontology) {
    if (ontology == null || !allowed.contains(ontology)) {
      return List.of();
    }
    try {
      String latest = catalog.resolveLatest(ontology).map(CatalogStore.SnapshotInfo::versionId).orElse(null);
      List<SqliteTerminologyService.VersionRef> out = new ArrayList<>();
      for (CatalogStore.SnapshotInfo s : catalog.listSnapshots(ontology)) {
        out.add(new SqliteTerminologyService.VersionRef(
            s.versionId(), s.declaredVersion(), s.releasedAt(), s.versionId().equals(latest)));
      }
      return out;
    } catch (SQLException e) {
      log.warn("Catalog version listing failed for ontology {}", ontology, e);
      return List.of();
    }
  }

  /**
   * The catalog's ontologies that are also allowlisted — the ones this server actually serves
   * locally. Reported as {@link Ontology} metadata (hierarchical, so {@code isFlat = false}) for the
   * ontology-list endpoint, so it needs no BioPortal call. The {@code @id} uses BioPortal's ontology
   * URL form for compatibility with clients that key on it.
   */
  @Override
  public List<Ontology> ontologies() {
    try {
      List<Ontology> out = new ArrayList<>();
      for (CatalogStore.OntologyInfo o : catalog.listOntologies()) {
        if (allowed.contains(o.acronym())) {
          out.add(new Ontology(o.acronym(), BP_ONTOLOGY_BASE + o.acronym(), o.name(), false, null));
        }
      }
      return out;
    } catch (SQLException e) {
      log.warn("Catalog ontology listing failed; serving no local ontology list", e);
      return List.of();
    }
  }

  private static final String BP_ONTOLOGY_BASE = "https://data.bioontology.org/ontologies/";

  private SnapshotStore open(String path) {
    try {
      return SnapshotStore.openFile(path);
    } catch (SQLException e) {
      log.warn("Failed to open snapshot file {}; falling back to remote", path, e);
      return null;
    }
  }

  @Override
  public void close() {
    for (SnapshotStore store : openByFile.values()) {
      try {
        store.close();
      } catch (SQLException e) {
        log.warn("Error closing snapshot store", e);
      }
    }
    openByFile.clear();
    try {
      catalog.close();
    } catch (SQLException e) {
      log.warn("Error closing catalog store", e);
    }
  }
}
