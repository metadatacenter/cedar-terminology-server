package org.metadatacenter.terms.search;

import java.sql.SQLException;
import java.util.*;
import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.store.*;

/**
 * Properties resolve through the same catalog, version identifiers and snapshot provider as
 * classes.
 */
public final class VersionedPropertyService {
  public record Source(String sourceAcronym, String versionId) {}

  public record Request(
      String query, List<Source> sources, List<String> kinds, Integer page, Integer pageSize) {}

  public record Result(long total, int page, int pageSize, List<PropertySearchIndex.Hit> items) {}

  public record Detail(
      String sourceAcronym, String versionId, SnapshotProperties.Property property) {}

  public record Hierarchy(
      Detail selected,
      List<SnapshotProperties.Property> ancestors,
      List<SnapshotProperties.Summary> children,
      int offset) {}

  public record Roots(
      String sourceAcronym, String versionId, List<SnapshotProperties.Summary> items, int offset) {}

  public record Version(
      String id, String declaredVersion, String effectiveDate, boolean propertiesAvailable) {}

  public static class NotHeld extends RuntimeException {
    public NotHeld(String message) {
      super(message);
    }
  }

  private record Resolved(String acronym, String version, SnapshotProperties properties) {}

  private final CatalogSnapshotProvider provider;
  private final SearchIndexStore index;

  public VersionedPropertyService(CatalogSnapshotProvider provider, SearchIndexStore index) {
    this.provider = provider;
    this.index = index;
  }

  private Resolved resolve(String acronym, String requested) throws SQLException {
    if (acronym == null || acronym.isBlank())
      throw new IllegalArgumentException("sourceAcronym is required");
    var info =
        provider
            .snapshotInfo(acronym, requested)
            .orElseThrow(() -> new NotHeld("Ontology release not held for " + acronym));
    var snapshot =
        provider
            .forOntology(acronym, info.versionId())
            .orElseThrow(() -> new NotHeld("Ontology snapshot unavailable for " + acronym));
    if (!snapshot.properties().available())
      throw new IllegalStateException(
          "Properties were not extracted for ontology version " + info.versionId());
    return new Resolved(acronym, info.versionId(), snapshot.properties());
  }

  public Result search(Request request) throws SQLException {
    if (request == null) throw new IllegalArgumentException("A search needs a JSON body");
    SnapshotProperties.queryTokens(request.query());
    int page = request.page() == null ? 1 : request.page(),
        size = request.pageSize() == null ? 20 : request.pageSize();
    if (page < 1 || page > 100 || size < 1 || size > 200)
      throw new IllegalArgumentException("page must be 1–100 and pageSize 1–200");
    List<String> kinds = request.kinds() == null ? List.of() : request.kinds();
    SnapshotProperties.validateKinds(kinds);
    List<Source> sources = request.sources() == null ? List.of() : request.sources();
    if (sources.size() > 20)
      throw new IllegalArgumentException("At most 20 sources may be searched");
    if (sources.isEmpty()) {
      if (index == null || !index.properties().available())
        throw new IllegalStateException(
            "Corpus-wide property search needs the property tables in the search index");
      List<String> allowed = new ArrayList<>();
      for (var ontology : provider.catalog().listOntologies())
        if (provider.serves(ontology.acronym())) allowed.add(ontology.acronym());
      var results =
          index.properties().search(request.query(), allowed, kinds, (page - 1) * size, size);
      return new Result(results.total(), page, size, results.items());
    }
    List<PropertySearchIndex.Hit> all = new ArrayList<>();
    long total = 0;
    Set<String> seen = new HashSet<>();
    for (Source source : sources) {
      if (source == null) throw new IllegalArgumentException("A source must not be null");
      Resolved resolved = resolve(source.sourceAcronym(), source.versionId());
      if (!seen.add(resolved.acronym() + "\n" + resolved.version())) continue;
      // Snapshot reads for explicitly selected releases. Fetch just enough per source to form the
      // global page.
      long count = 0;
      for (int offset = 0; offset < page * size; offset += 200) {
        var result =
            resolved
                .properties()
                .search(request.query(), kinds, offset, Math.min(200, page * size - offset));
        count = result.total();
        for (var property : result.items())
          all.add(new PropertySearchIndex.Hit(resolved.acronym(), resolved.version(), property));
        if (offset + result.items().size() >= count) break;
      }
      total += count;
    }
    all.sort(
        Comparator.comparing(
                (PropertySearchIndex.Hit h) -> h.property().label().toLowerCase(Locale.ROOT))
            .thenComparing(h -> h.property().iri())
            .thenComparing(PropertySearchIndex.Hit::sourceAcronym)
            .thenComparing(h -> h.property().kind())
            .thenComparing(PropertySearchIndex.Hit::versionId));
    return new Result(
        total, page, size, all.stream().skip((long) (page - 1) * size).limit(size).toList());
  }

  public List<Version> versions(String acronym) throws SQLException {
    if (acronym == null || !provider.serves(acronym))
      throw new NotHeld("Ontology not served locally");
    List<Version> out = new ArrayList<>();
    for (var s : provider.catalog().listSnapshots(acronym)) {
      var snapshot = provider.forOntology(acronym, s.versionId());
      out.add(
          new Version(
              s.versionId(),
              s.declaredVersion(),
              s.releasedAt(),
              snapshot.isPresent() && snapshot.get().properties().available()));
    }
    return out;
  }

  public Detail find(String acronym, String version, String iri, String kind) throws SQLException {
    if (iri == null || iri.isBlank()) throw new IllegalArgumentException("propertyIri is required");
    if (kind == null) throw new IllegalArgumentException("kind is required");
    SnapshotProperties.validateKinds(List.of(kind));
    Resolved r = resolve(acronym, version);
    return new Detail(
        acronym,
        r.version(),
        r.properties()
            .property(iri, kind)
            .orElseThrow(() -> new NotHeld("Property not in the selected ontology release")));
  }

  public Hierarchy hierarchy(String acronym, String version, String iri, String kind, int offset)
      throws SQLException {
    Detail detail = find(acronym, version, iri, kind);
    Resolved r = resolve(acronym, detail.versionId());
    List<SnapshotProperties.Property> ancestors = new ArrayList<>();
    for (var parent : r.properties().ancestors(iri, kind)) {
      ancestors.add(r.properties().property(parent.iri(), kind).orElseThrow());
    }
    return new Hierarchy(detail, ancestors, r.properties().children(iri, kind, offset, 50), offset);
  }

  public Roots roots(String acronym, String version, String kind, int offset) throws SQLException {
    if (kind == null) throw new IllegalArgumentException("kind is required");
    SnapshotProperties.validateKinds(List.of(kind));
    Resolved r = resolve(acronym, version);
    return new Roots(acronym, r.version(), r.properties().roots(kind, offset, 50), offset);
  }
}
