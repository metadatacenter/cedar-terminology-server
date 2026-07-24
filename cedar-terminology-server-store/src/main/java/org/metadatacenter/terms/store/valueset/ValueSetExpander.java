package org.metadatacenter.terms.store.valueset;

import org.metadatacenter.terms.store.CatalogStore;
import org.metadatacenter.terms.store.SnapshotStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a {@link ValueSetDefinition} to its member concept IRIs against the version-pinned
 * snapshots it references.
 *
 * Intensional definitions read the snapshot the definition is pinned to: {@code DESCENDANTS} uses
 * the precomputed closure, {@code RELATION} the retained Level-1 relations. Extensional definitions
 * validate each pinned member against its snapshot. Because every reference carries a content-hash
 * snapshot id, expanding the same definition always yields the same members.
 *
 * Snapshot stores are resolved via the catalog and cached; {@link #close()} closes them (not the
 * catalog, which the caller owns).
 */
public class ValueSetExpander implements AutoCloseable {

  private final CatalogStore catalog;
  private final Map<String, SnapshotStore> openByVersion = new ConcurrentHashMap<>();

  public ValueSetExpander(CatalogStore catalog) {
    this.catalog = catalog;
  }

  /** Expands a definition to a sorted, de-duplicated list of member concept IRIs. */
  public List<String> expand(ValueSetDefinition def) throws SQLException {
    return switch (def.kind()) {
      case DESCENDANTS -> {
        SnapshotStore store = snapshot(def.snapshotVersionId());
        TreeSet<String> members = new TreeSet<>(store.descendants(def.rootIri()));
        if (def.includeRoot() && store.contains(def.rootIri())) {
          members.add(def.rootIri());
        }
        yield new ArrayList<>(members);
      }
      case RELATION -> snapshot(def.snapshotVersionId()).subjectsWith(def.predicate(), def.objectIri());
      case EXTENSIONAL -> {
        TreeSet<String> members = new TreeSet<>();
        for (PinnedConcept m : def.members()) {
          if (snapshot(m.versionId()).contains(m.conceptIri())) {
            members.add(m.conceptIri());
          }
        }
        yield new ArrayList<>(members);
      }
    };
  }

  private SnapshotStore snapshot(String versionId) throws SQLException {
    SnapshotStore cached = openByVersion.get(versionId);
    if (cached != null) {
      return cached;
    }
    Optional<CatalogStore.SnapshotInfo> info = catalog.getSnapshot(versionId);
    if (info.isEmpty()) {
      throw new SQLException("No snapshot for version id " + versionId);
    }
    SnapshotStore store = SnapshotStore.openFile(info.get().filePath());
    openByVersion.put(versionId, store);
    return store;
  }

  @Override
  public void close() throws SQLException {
    for (SnapshotStore store : openByVersion.values()) {
      store.close();
    }
    openByVersion.clear();
  }

  /**
   * Usage:
   *   ValueSetExpander &lt;catalogDb&gt; descendants &lt;versionId&gt; &lt;rootIri&gt; [includeRoot]
   *   ValueSetExpander &lt;catalogDb&gt; relation    &lt;versionId&gt; &lt;predicate&gt; &lt;objectIri&gt;
   * Prints the member count and a sample.
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("Usage: ValueSetExpander <catalogDb> descendants <versionId> <rootIri> [includeRoot]");
      System.err.println("       ValueSetExpander <catalogDb> relation <versionId> <predicate> <objectIri>");
      System.exit(2);
    }
    ValueSetDefinition def = switch (args[1]) {
      case "descendants" -> ValueSetDefinition.descendants(args[2], args[3], args.length > 4 && Boolean.parseBoolean(args[4]));
      case "relation" -> ValueSetDefinition.relation(args[2], args[3], args[4]);
      default -> throw new IllegalArgumentException("Unknown kind: " + args[1]);
    };
    try (CatalogStore catalog = CatalogStore.openFile(args[0]);
         ValueSetExpander expander = new ValueSetExpander(catalog)) {
      List<String> members = expander.expand(def);
      System.out.println("members: " + members.size());
      members.stream().limit(10).forEach(m -> System.out.println("  " + m));
    }
  }
}
