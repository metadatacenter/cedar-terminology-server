package org.metadatacenter.terms.search;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.terms.CatalogSnapshotProvider;
import org.metadatacenter.terms.store.*;

/**
 * Property search, hierarchy and roots paged by limit and offset. Two releases are searched as two
 * sources, whose properties the service merges by label, so an offset has to land in the merged
 * list rather than in either source's own.
 */
class VersionedPropertyPagingTest {
  @TempDir Path dir;

  private CatalogStore catalog;
  private SearchIndexStore index;
  private CatalogSnapshotProvider provider;
  private VersionedPropertyService service;
  private String first;
  private String second;

  /** {@code n} properties whose labels, all containing "prop", are written out of order. */
  private static List<SnapshotProperties.Property> properties(String prefix, int n, String parent) {
    List<SnapshotProperties.Property> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      int k = (int) (((long) i * 1_000_003 + 5) % n);
      List<String> parents = parent == null || k == 0 ? List.of() : List.of(parent);
      out.add(new SnapshotProperties.Property("urn:" + prefix + k, "object",
          String.format("%s prop %03d", prefix, k), false, List.of(), parents));
    }
    return out;
  }

  private String snapshot(String name, List<SnapshotProperties.Property> properties) throws Exception {
    Path file = dir.resolve(name + ".sqlite");
    String version;
    try (SnapshotStore s = SnapshotStore.openFile(file.toString())) {
      s.initSchema();
      s.addConcept("urn:class-" + name, "Class " + name, false, null);
      s.properties().write(properties);
      version = s.normalizedContentHash(true);
    }
    catalog.addSnapshot(new CatalogStore.SnapshotInfo(version, "EX", name, "2026-01-01", "2026-01-01", "OWL",
        "subsumption", 1, 0, file.toString(), name, "public"));
    return version;
  }

  @BeforeEach
  void setUp() throws Exception {
    catalog = CatalogStore.openFile(dir.resolve("catalog.sqlite").toString());
    index = SearchIndexStore.openFile(dir.resolve("search-index.sqlite").toString());
    catalog.initSchema();
    index.initSchema();
    catalog.upsertOntology(new CatalogStore.OntologyInfo("EX", "Example", null, "OWL"));
    first = snapshot("first", properties("alpha", 13, null));
    // The second release's properties hang from its first, so it has a hierarchy to page through.
    second = snapshot("second", properties("beta", 17, "urn:beta0"));
    catalog.setTag("EX", "latest", second);
    provider = new CatalogSnapshotProvider(catalog, Set.of("EX"));
    service = new VersionedPropertyService(provider, index);
  }

  @AfterEach
  void tearDown() throws Exception {
    provider.close();
    index.close();
    catalog.close();
  }

  private List<VersionedPropertyService.Source> bothReleases() {
    return List.of(new VersionedPropertyService.Source("EX", first), new VersionedPropertyService.Source("EX", second));
  }

  private static List<String> labels(VersionedPropertyService.Result result) {
    return result.items().stream().map(h -> h.property().label()).toList();
  }

  @Test
  void walkingTwoSourcesByOffsetVisitsTheMergedListOnceInLabelOrder() throws Exception {
    List<String> seen = new ArrayList<>();
    for (int offset = 0; offset < 30; offset += 4) {
      seen.addAll(labels(service.search(
          new VersionedPropertyService.Request("prop", bothReleases(), null, null, null, 4, offset))));
    }

    assertEquals(30, seen.size());
    List<String> sorted = new ArrayList<>(seen);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);
    assertEquals(sorted, seen);
    assertEquals(30, new HashSet<>(seen).size());
  }

  @Test
  void anOffsetInsideAPageMatchesTheSliceOfTheWhole() throws Exception {
    List<String> whole = labels(service.search(
        new VersionedPropertyService.Request("prop", bothReleases(), null, null, null, 30, 0)));

    VersionedPropertyService.Result window = service.search(
        new VersionedPropertyService.Request("prop", bothReleases(), null, null, null, 5, 11));

    assertEquals(whole.subList(11, 16), labels(window));
    assertEquals(30, window.totalCount());
    assertEquals(30, window.total(), "the old field says the same");
    assertEquals(11, window.currentOffset());
    assertEquals(5, window.request().limit());
  }

  @Test
  void aPageRequestStillWorksAndStatesItsOffset() throws Exception {
    VersionedPropertyService.Result page = service.search(
        new VersionedPropertyService.Request("prop", bothReleases(), null, 3, 5));

    assertEquals(3, page.page());
    assertEquals(10, page.currentOffset());
    assertEquals(5, page.items().size());
  }

  @Test
  void bothKindsOfPagingAndOutOfRangeLimitsAreRefused() {
    assertThrows(IllegalArgumentException.class, () -> service.search(
        new VersionedPropertyService.Request("prop", bothReleases(), null, 1, null, 10, null)));
    assertThrows(IllegalArgumentException.class, () -> service.search(
        new VersionedPropertyService.Request("prop", bothReleases(), null, null, null, 201, 0)));
    assertThrows(IllegalArgumentException.class, () -> service.search(
        new VersionedPropertyService.Request("prop", bothReleases(), null, null, null, 10, -1)));
    assertThrows(IllegalArgumentException.class, () -> service.search(
        new VersionedPropertyService.Request("prop", bothReleases(), null, null, null, 100, 19_950)));
  }

  @Test
  void aHierarchysChildrenPageByLimitWithALowerBoundForTheirCount() throws Exception {
    VersionedPropertyService.Hierarchy full = service.hierarchy("EX", second, "urn:beta0", "object", 0, 10);
    VersionedPropertyService.Hierarchy last = service.hierarchy("EX", second, "urn:beta0", "object", 10, 10);

    assertEquals(10, full.children().size());
    assertTrue(full.countCapped());
    assertEquals(11, full.totalCount(), "a full page implies at least one more");
    assertEquals(6, last.children().size());
    assertEquals(16, last.totalCount(), "a short page is the last");
    assertEquals(10, last.currentOffset());
  }

  @Test
  void aHierarchysLinksCarryTheOffsetAndNeverALastPage() throws Exception {
    VersionedPropertyService.Hierarchy page = service.hierarchy("EX", second, "urn:beta0", "object", 0, 10)
        .paged("http://t/properties/hierarchy?sourceAcronym=EX&propertyIri=urn:beta0&kind=object");

    assertTrue(page.paging().get("next").contains("offset=10"));
    assertTrue(page.paging().get("next").contains("propertyIri=urn"));
    assertFalse(page.paging().containsKey("last"));
  }

  @Test
  void rootsPageByLimit() throws Exception {
    VersionedPropertyService.Roots roots = service.roots("EX", first, "object", 0, 5);

    assertEquals(5, roots.items().size());
    assertEquals(6, roots.totalCount());
    assertEquals(new org.metadatacenter.util.http.PagedListResponse.PageRequest(5, 0), roots.request());
  }
}
