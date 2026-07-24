package org.metadatacenter.terms;

import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
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
  private final ConcurrentHashMap<String, SnapshotStore> openByVersion = new ConcurrentHashMap<>();

  public CatalogSnapshotProvider(CatalogStore catalog, Set<String> allowed) {
    this.catalog = catalog;
    this.allowed = Set.copyOf(allowed);
  }

  @Override
  public Optional<SnapshotStore> forOntology(String ontology) {
    if (ontology == null || !allowed.contains(ontology)) {
      return Optional.empty();
    }
    try {
      Optional<CatalogStore.SnapshotInfo> info = catalog.resolveLatest(ontology);
      if (info.isEmpty()) {
        return Optional.empty();
      }
      String versionId = info.get().versionId();
      SnapshotStore store = openByVersion.computeIfAbsent(versionId, v -> open(info.get().filePath()));
      return Optional.ofNullable(store);
    } catch (SQLException e) {
      log.warn("Catalog lookup failed for ontology {}; falling back to remote", ontology, e);
      return Optional.empty();
    }
  }

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
    for (SnapshotStore store : openByVersion.values()) {
      try {
        store.close();
      } catch (SQLException e) {
        log.warn("Error closing snapshot store", e);
      }
    }
    openByVersion.clear();
    try {
      catalog.close();
    } catch (SQLException e) {
      log.warn("Error closing catalog store", e);
    }
  }
}
