package org.metadatacenter.terms;

import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyVersion;
import org.metadatacenter.terms.domainObjects.VersionTriple;
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

  /** Whether this provider is allowed to serve an ontology at all, before any version is resolved. */
  public boolean serves(String ontology) {
    return ontology != null && allowed.contains(ontology);
  }

  /**
   * The snapshot a version request resolves to, without opening it.
   *
   * {@link #forOntology} answers with a store and so cannot distinguish "not served" from "served,
   * but not at that version" — both are an empty Optional. A caller that has to report which of the
   * two happened needs the resolution itself, which is what this returns.
   */
  public Optional<CatalogStore.SnapshotInfo> snapshotInfo(String ontology, String version) throws SQLException {
    if (!serves(ontology)) {
      return Optional.empty();
    }
    return resolveInfo(ontology, version);
  }

  /** The catalog behind this provider, for metadata a snapshot does not carry. */
  public CatalogStore catalog() {
    return catalog;
  }

  /**
   * Resolves the snapshot a version request names. Precedence: {@code null}/blank/{@code "latest"} →
   * current; then, for a specific request, {@code content hash → tag → as-of date → declared
   * version}. These are distinct namespaces (a hash is 64 hex chars, a tag a short name, a date
   * ISO {@code YYYY-MM-DD}, a declared version a free-form label), and the first interpretation that
   * matches wins.
   *
   * A request that matches none resolves to empty. The caller then distinguishes by whether a version
   * was explicitly pinned: an unpinned (latest) miss may route to the remote adapter, but an explicit
   * pin fails loud ({@link PinnedVersionUnavailableException}) rather than silently serving {@code
   * latest} from remote — a pin that cannot be honored must not resolve to the wrong content. A
   * date-shaped request that finds no snapshot on or before it falls
   * through to the declared-version match, in case the string was a label that merely looks like a
   * date; when the label is genuinely a date, the earlier as-of resolution has already answered.
   */
  private Optional<CatalogStore.SnapshotInfo> resolveInfo(String ontology, String version) throws SQLException {
    if (version == null || version.isBlank() || CatalogStore.TAG_LATEST.equalsIgnoreCase(version)) {
      return catalog.resolveLatest(ontology);
    }
    Optional<CatalogStore.SnapshotInfo> byHash = catalog.resolveVersion(ontology, version);
    if (byHash.isPresent()) {
      return byHash;
    }
    Optional<CatalogStore.SnapshotInfo> byTag = catalog.resolve(ontology, version);
    if (byTag.isPresent()) {
      return byTag;
    }
    Optional<String> asOf = asOfDate(version);
    if (asOf.isPresent()) {
      Optional<CatalogStore.SnapshotInfo> byDate = catalog.resolveAsOfDate(ontology, asOf.get());
      if (byDate.isPresent()) {
        return byDate;
      }
      // Fall through: nothing was published on or before this date, but the string may still be a
      // declared-version label that happens to look like a date.
    }
    List<CatalogStore.SnapshotInfo> byDeclared = catalog.resolveByDeclaredVersion(ontology, version);
    if (byDeclared.isEmpty()) {
      return Optional.empty();
    }
    if (byDeclared.size() > 1) {
      CatalogStore.SnapshotInfo chosen = byDeclared.get(0);
      log.warn("Declared version '{}' of ontology {} is ambiguous: {} snapshots carry it; serving the "
              + "newest ({}, released {}). Pin a content hash for a reproducible reference.",
          version, ontology, byDeclared.size(), chosen.versionId(), chosen.releasedAt());
    }
    return Optional.of(byDeclared.get(0));
  }

  /**
   * If {@code version} begins with an ISO calendar date ({@code YYYY-MM-DD}, optionally followed by a
   * time or anything else), returns that date; otherwise empty. This decides whether a version
   * request is interpreted as an "as of" date. A content-hash id can never match: hex has no
   * {@code -} at the date separator positions.
   */
  static Optional<String> asOfDate(String version) {
    if (version == null || version.length() < 10) {
      return Optional.empty();
    }
    String head = version.substring(0, 10);
    try {
      java.time.LocalDate.parse(head); // validates the calendar date, rejecting e.g. 2024-13-40
      return Optional.of(head);
    } catch (java.time.format.DateTimeParseException notADate) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<String> ontologyForConceptIri(String conceptIri) {
    if (conceptIri == null) {
      return Optional.empty();
    }
    try {
      // The concept's ID-space (SnapshotStore.idspace) is the ontology's raw namespace; reverse-look
      // it up in the catalog, then keep it only if we actually serve that ontology.
      return catalog.acronymForNamespace(SnapshotStore.idspace(conceptIri)).filter(allowed::contains);
    } catch (SQLException e) {
      log.warn("Catalog namespace lookup failed for concept {}", conceptIri, e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<VersionTriple> currentVersion(String ontology) {
    if (ontology == null || !allowed.contains(ontology)) {
      return Optional.empty();
    }
    try {
      return catalog.resolveLatest(ontology).map(CatalogSnapshotProvider::toTriple);
    } catch (SQLException e) {
      log.warn("Catalog current-version lookup failed for ontology {}", ontology, e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<VersionTriple> currentVersionForValueSetCollection(String vsCollection) {
    if (vsCollection == null) {
      return Optional.empty();
    }
    try {
      // Gate on the catalog's artifact-kind, not the ontology serving allowlist: a value-set
      // collection is not served for search/browse, so it is never allowlisted. It resolves purely on
      // being ingested and marked a value-set collection (with the whole local store off in prod, this
      // answers nothing there). The kind check also stops an ontology of the same acronym answering.
      if (!catalog.isValueSetCollection(vsCollection)) {
        return Optional.empty();
      }
      return catalog.resolveLatest(vsCollection).map(CatalogSnapshotProvider::toTriple);
    } catch (SQLException e) {
      log.warn("Catalog current-version lookup failed for value-set collection {}", vsCollection, e);
      return Optional.empty();
    }
  }

  /**
   * Builds the {@link VersionTriple} for a snapshot: content-hash id, effective date (the source's
   * release date, or the ingest date when the source records none), and the declared-version label
   * as-is (may be null).
   */
  static VersionTriple toTriple(CatalogStore.SnapshotInfo s) {
    return new VersionTriple(s.versionId(), effectiveDate(s), s.declaredVersion());
  }

  /**
   * The date this snapshot's state entered circulation: the calendar-date component of the source's
   * release timestamp, or of the ingest timestamp when the source records no release. BioPortal's
   * timestamps are day-granular, so the time-of-day and UTC offset carry no information; truncating
   * to the day is both faithful and offset-independent. Shared by the triple and the versions list
   * so the two never disagree.
   */
  private static String effectiveDate(CatalogStore.SnapshotInfo s) {
    String source = s.releasedAt() != null ? s.releasedAt() : s.ingestedAt();
    return source != null && source.length() >= 10 ? source.substring(0, 10) : source;
  }

  @Override
  public List<OntologyVersion> versions(String ontology) {
    if (ontology == null || !allowed.contains(ontology)) {
      return List.of();
    }
    try {
      String latest = catalog.resolveLatest(ontology).map(CatalogStore.SnapshotInfo::versionId).orElse(null);
      List<OntologyVersion> out = new ArrayList<>();
      for (CatalogStore.SnapshotInfo s : catalog.listSnapshots(ontology)) {
        out.add(new OntologyVersion(
            s.versionId(), s.declaredVersion(), s.releasedAt(), effectiveDate(s), s.versionId().equals(latest)));
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
