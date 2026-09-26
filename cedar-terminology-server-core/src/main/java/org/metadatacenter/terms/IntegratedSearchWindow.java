package org.metadatacenter.terms;

import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.SearchResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The results an integrated search re-sorts or rearranges, gathered before any of them is paged.
 *
 * <p>BioPortal returns each source in its own order. Whenever integrated search changes that order,
 * by sorting several sources together, sorting one source alphabetically, or applying a field's
 * actions, a page can only be cut from the whole reordered list. Sorting one upstream page at a time
 * made every page a different ordering of a different subset, so the server answered page 1 whatever
 * was asked for, and callers that walked the pages never reached the end.
 *
 * <p>The whole list of a large ontology is too much to fetch per request, so each source is read up
 * to {@link #WINDOW} results. Paging within the window is exact. A source with more than that is
 * truncated, and the window reports that it was.
 */
final class IntegratedSearchWindow {

  /** The most results read from any one source. */
  static final int WINDOW = 1_000;

  /** How many results one upstream request asks a source for. */
  static final int UPSTREAM_PAGE_SIZE = 500;

  /** One source, read a page at a time, with one-based page numbers. */
  @FunctionalInterface
  interface Source {
    PagedResults<SearchResult> page(int page, int pageSize) throws IOException;
  }

  /**
   * What the sources hold within the window.
   *
   * @param results   every result read, source after source, each in its source's own order
   * @param truncated whether any source holds more than the window read
   */
  record Window(List<SearchResult> results, boolean truncated) {
  }

  private IntegratedSearchWindow() {
  }

  static Window collect(List<Source> sources) throws IOException {
    return collect(sources, WINDOW, UPSTREAM_PAGE_SIZE);
  }

  static Window collect(List<Source> sources, int window, int upstreamPageSize) throws IOException {
    List<SearchResult> all = new ArrayList<>();
    boolean truncated = false;
    for (Source source : sources) {
      List<SearchResult> read = new ArrayList<>();
      boolean more = false;
      for (int page = 1; ; page++) {
        PagedResults<SearchResult> answer = source.page(page, upstreamPageSize);
        List<SearchResult> collection = answer == null || answer.getCollection() == null
            ? List.of() : answer.getCollection();
        read.addAll(collection);
        more = !collection.isEmpty() && answer.getNextPage() != null;
        if (!more || read.size() >= window) {
          break;
        }
      }
      if (read.size() > window) {
        read = new ArrayList<>(read.subList(0, window));
        more = true;
      }
      truncated |= more;
      all.addAll(read);
    }
    return new Window(all, truncated);
  }
}
