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

  /**
   * Compares the membership of two definitions (e.g. one intensional rule pinned to two snapshot
   * versions), reporting concepts added and removed.
   */
  public ValueSetDiff diff(ValueSetDefinition from, ValueSetDefinition to) throws SQLException {
    java.util.Set<String> before = new java.util.TreeSet<>(expand(from));
    java.util.Set<String> after = new java.util.TreeSet<>(expand(to));
    List<String> added = new ArrayList<>();
    for (String iri : after) {
      if (!before.contains(iri)) {
        added.add(iri);
      }
    }
    List<String> removed = new ArrayList<>();
    for (String iri : before) {
      if (!after.contains(iri)) {
        removed.add(iri);
      }
    }
    return new ValueSetDiff(before.size(), after.size(), added, removed);
  }

  /**
   * Validates a value set's members against a target snapshot version: expands the definition (at
   * its own pinned snapshot), then classifies each member in the target as still active, obsolete
   * (annotated with its replacement IRI when known), or removed.
   */
  public ValueSetValidation validateAgainst(ValueSetDefinition def, String targetVersionId) throws SQLException {
    List<String> members = expand(def);
    SnapshotStore target = snapshot(targetVersionId);
    int active = 0;
    List<String> obsoleted = new ArrayList<>();
    List<String> removed = new ArrayList<>();
    for (String iri : members) {
      Optional<SnapshotStore.ConceptMeta> meta = target.conceptMeta(iri);
      if (meta.isEmpty()) {
        removed.add(iri);
      } else if (meta.get().obsolete()) {
        obsoleted.add(meta.get().replacedBy() == null ? iri : iri + " => " + meta.get().replacedBy());
      } else {
        active++;
      }
    }
    return new ValueSetValidation(members.size(), active, obsoleted, removed);
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
   *   ValueSetExpander &lt;catalogDb&gt; diff-descendants &lt;versionA&gt; &lt;versionB&gt; &lt;rootIri&gt;
   * Prints member counts / diff summary and a sample.
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("Usage: ValueSetExpander <catalogDb> descendants <versionId> <rootIri> [includeRoot]");
      System.err.println("       ValueSetExpander <catalogDb> relation <versionId> <predicate> <objectIri>");
      System.err.println("       ValueSetExpander <catalogDb> diff-descendants <versionA> <versionB> <rootIri>");
      System.err.println("       ValueSetExpander <catalogDb> validate-descendants <definedVersion> <targetVersion> <rootIri>");
      System.exit(2);
    }
    try (CatalogStore catalog = CatalogStore.openFile(args[0]);
         ValueSetExpander expander = new ValueSetExpander(catalog)) {
      if ("diff-descendants".equals(args[1])) {
        ValueSetDiff d = expander.diff(
            ValueSetDefinition.descendants(args[2], args[4], false),
            ValueSetDefinition.descendants(args[3], args[4], false));
        System.out.println(d.summary());
        sample("added", d.added());
        sample("removed", d.removed());
      } else if ("validate-descendants".equals(args[1])) {
        // args: catalog validate-descendants <definedVersion> <targetVersion> <rootIri>
        ValueSetValidation v = expander.validateAgainst(
            ValueSetDefinition.descendants(args[2], args[4], false), args[3]);
        System.out.println(v.summary());
        sample("obsoleted", v.obsoleted());
        sample("removed", v.removed());
      } else {
        ValueSetDefinition def = switch (args[1]) {
          case "descendants" -> ValueSetDefinition.descendants(args[2], args[3],
              args.length > 4 && Boolean.parseBoolean(args[4]));
          case "relation" -> ValueSetDefinition.relation(args[2], args[3], args[4]);
          default -> throw new IllegalArgumentException("Unknown kind: " + args[1]);
        };
        List<String> members = expander.expand(def);
        System.out.println("members: " + members.size());
        members.stream().limit(10).forEach(m -> System.out.println("  " + m));
      }
    }
  }

  private static void sample(String label, List<String> items) {
    if (!items.isEmpty()) {
      System.out.println(label + " (" + items.size() + ", showing up to 10):");
      items.stream().limit(10).forEach(m -> System.out.println("  " + m));
    }
  }
}
