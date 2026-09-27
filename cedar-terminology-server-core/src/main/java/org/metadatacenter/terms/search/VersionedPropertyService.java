package org.metadatacenter.terms.search;

import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.http.PagedListResponse;
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

  /**
   * A property search. It is paged either by {@code limit} and {@code offset}, CEDAR's paging, or by
   * {@code page} and {@code pageSize}, not both.
   */
  public record Request(
      String query, List<Source> sources, List<String> kinds, Integer page, Integer pageSize,
      Integer limit, Integer offset) {

    public Request(String query, List<Source> sources, List<String> kinds, Integer page, Integer pageSize) {
      this(query, sources, kinds, page, pageSize, null, null);
    }
  }

  /**
   * A page of properties. {@code total}, {@code page} and {@code pageSize} are the fields this route
   * answered with before it took an offset; {@code totalCount}, {@code request} and
   * {@code currentOffset} state the same page as CEDAR's paging does. A POST has no URL to link to,
   * so there are no links: the next page is asked for by sending the offset.
   */
  public record Result(long total, int page, int pageSize, List<PropertySearchIndex.Hit> items,
                       long totalCount, PagedListResponse.PageRequest request, long currentOffset) {

    static Result at(long total, int offset, int limit, List<PropertySearchIndex.Hit> items) {
      return new Result(total, offset / limit + 1, limit, items, total,
          new PagedListResponse.PageRequest(limit, offset), offset);
    }
  }

  public record Detail(
      String sourceAcronym, String versionId, SnapshotProperties.Property property) {}

  /**
   * Where a property sits. The children are paged, and nothing counts them, so {@code totalCount} is
   * a lower bound and {@code countCapped} is always true: everything up to this page, and one more
   * when the page came back full.
   */
  public record Hierarchy(
      Detail selected,
      List<SnapshotProperties.Property> ancestors,
      List<SnapshotProperties.Summary> children,
      int offset,
      PagedListResponse.PageRequest request,
      long totalCount,
      long currentOffset,
      boolean countCapped,
      Map<String, String> paging) {

    public Hierarchy paged(String requestUrl) {
      return new Hierarchy(selected, ancestors, children, offset, request, totalCount, currentOffset,
          countCapped, links(requestUrl, totalCount, request));
    }
  }

  /** A page of root properties, counted as {@link Hierarchy} counts its children. */
  public record Roots(
      String sourceAcronym, String versionId, List<SnapshotProperties.Summary> items, int offset,
      PagedListResponse.PageRequest request, long totalCount, long currentOffset, boolean countCapped,
      Map<String, String> paging) {

    public Roots paged(String requestUrl) {
      return new Roots(sourceAcronym, versionId, items, offset, request, totalCount, currentOffset,
          countCapped, links(requestUrl, totalCount, request));
    }
  }

  /** The most children or roots one request may ask for, and how many it gets by default. */
  public static final int MAX_CHILD_LIMIT = 500;
  public static final int DEFAULT_CHILD_LIMIT = 50;

  private static Map<String, String> links(String requestUrl, long totalCount,
                                           PagedListResponse.PageRequest request) {
    if (requestUrl == null) {
      return null;
    }
    Map<String, String> links =
        LinkHeaderUtil.getPagingLinkHeaders(requestUrl, totalCount, request.limit(), request.offset());
    links.remove(HttpConstants.HEADER_LINK_TYPE_LAST);
    return links;
  }

  /** The least a page's total can be when nothing counts it. */
  private static long lowerBound(int offset, int returned, int limit) {
    return (long) offset + returned + (returned >= limit ? 1 : 0);
  }

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
    boolean byOffset = request.limit() != null || request.offset() != null;
    if (byOffset && (request.page() != null || request.pageSize() != null))
      throw new IllegalArgumentException("Send either limit and offset or page and pageSize, not both");
    int start, size;
    if (byOffset) {
      size = request.limit() == null ? 20 : request.limit();
      start = request.offset() == null ? 0 : request.offset();
      if (size < 1 || size > 200 || start < 0 || start + size > 20_000)
        throw new IllegalArgumentException("limit must be 1–200, offset must not be negative, and a page must end "
            + "within the first 20,000 properties");
    } else {
      int page = request.page() == null ? 1 : request.page();
      size = request.pageSize() == null ? 20 : request.pageSize();
      if (page < 1 || page > 100 || size < 1 || size > 200)
        throw new IllegalArgumentException("page must be 1–100 and pageSize 1–200");
      start = (page - 1) * size;
    }
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
          index.properties().search(request.query(), allowed, kinds, start, size);
      return Result.at(results.total(), start, size, results.items());
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
      for (int offset = 0; offset < start + size; offset += 200) {
        var result =
            resolved
                .properties()
                .search(request.query(), kinds, offset, Math.min(200, start + size - offset));
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
    return Result.at(total, start, size, all.stream().skip(start).limit(size).toList());
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
    return hierarchy(acronym, version, iri, kind, offset, DEFAULT_CHILD_LIMIT);
  }

  public Hierarchy hierarchy(String acronym, String version, String iri, String kind, int offset, int limit)
      throws SQLException {
    Detail detail = find(acronym, version, iri, kind);
    Resolved r = resolve(acronym, detail.versionId());
    List<SnapshotProperties.Property> ancestors = new ArrayList<>();
    for (var parent : r.properties().ancestors(iri, kind)) {
      ancestors.add(r.properties().property(parent.iri(), kind).orElseThrow());
    }
    List<SnapshotProperties.Summary> children = r.properties().children(iri, kind, offset, limit);
    return new Hierarchy(detail, ancestors, children, offset, new PagedListResponse.PageRequest(limit, offset),
        lowerBound(offset, children.size(), limit), offset, true, null);
  }

  public Roots roots(String acronym, String version, String kind, int offset) throws SQLException {
    return roots(acronym, version, kind, offset, DEFAULT_CHILD_LIMIT);
  }

  public Roots roots(String acronym, String version, String kind, int offset, int limit) throws SQLException {
    if (kind == null) throw new IllegalArgumentException("kind is required");
    SnapshotProperties.validateKinds(List.of(kind));
    Resolved r = resolve(acronym, version);
    List<SnapshotProperties.Summary> items = r.properties().roots(kind, offset, limit);
    return new Roots(acronym, r.version(), items, offset, new PagedListResponse.PageRequest(limit, offset),
        lowerBound(offset, items.size(), limit), offset, true, null);
  }
}
