package org.metadatacenter.terms.util;

import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.paging.PageLimits;
import org.metadatacenter.util.http.PagedQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the {@code limit} and {@code offset} a terminology route is paged by, and serves an offset
 * from sources that page by number.
 *
 * <p>A {@code limit} defaults to {@link PageLimits#DEFAULT_LIMIT} and is refused above
 * {@link PageLimits#MAX_LIMIT}. Paging by page number is withdrawn. A JSON body may still carry
 * {@code page} 1, which older clients send with every request, and is then served by its
 * {@code limit} and {@code offset}; any other page is refused.
 *
 * <p>Every source behind these routes pages by number, BioPortal and the local store alike. An
 * offset on a page boundary is one page; an offset inside a page is served from the two pages it
 * straddles, so a client never has to align its offset to anything.
 */
public final class OffsetPaging {

  private OffsetPaging() {
  }

  /** The page asked for, as an offset and a limit. */
  public record Request(int offset, int limit) {
  }

  /** One source, read a page at a time, with one-based page numbers. */
  @FunctionalInterface
  public interface PageSource<T> {
    PagedResults<T> page(int page, int size) throws IOException;
  }

  /** The page a request asks for by {@code limit} and {@code offset}, either of which may be absent. */
  public static Request resolve(Integer limit, Integer offset) throws CedarException {
    return resolve(limit, offset, null);
  }

  /**
   * The page a GET request asks for, refused when its query carries a withdrawn page-number
   * parameter. A null query is not checked.
   */
  public static Request resolve(Integer limit, Integer offset, Map<String, List<String>> queryParameters)
      throws CedarException {
    PagedQuery query = new PagedQuery(PageLimits.DEFAULT_LIMIT, PageLimits.MAX_LIMIT)
        .limit(Optional.ofNullable(limit)).offset(Optional.ofNullable(offset)).queryParameters(queryParameters);
    query.validate();
    return new Request(query.getOffset(), query.getLimit());
  }

  /**
   * The page a JSON body asks for. A {@code page} that is absent or 1 is ignored, and the body is
   * served by its {@code limit} and {@code offset}; any other page is refused.
   */
  public static Request resolveBody(Integer page, Integer limit, Integer offset) throws CedarException {
    if (!isFirstOrAbsent(page)) {
      throw new CedarAssertionException(PageLimits.PAGE_NUMBERS_WITHDRAWN).parameter("page", page).badRequest();
    }
    return resolve(limit, offset);
  }

  /** Whether a body's {@code page} is one this server ignores rather than refuses. */
  public static boolean isFirstOrAbsent(Integer page) {
    return page == null || page == 1;
  }

  /**
   * The page a request asks for, read from a source that pages by number, with its paging worked
   * out from the offset.
   */
  public static <T> PagedResults<T> fetch(Request request, PageSource<T> source) throws IOException {
    int limit = request.limit();
    int firstPage = request.offset() / limit + 1;
    int skip = request.offset() % limit;
    PagedResults<T> first = source.page(firstPage, limit);
    List<T> collected = new ArrayList<>(first.getCollection() == null ? List.of() : first.getCollection());
    Integer total = first.getTotalCount();
    boolean capped = Boolean.TRUE.equals(first.getCountCapped());
    if (skip > 0 && first.hasNextPage() && !collected.isEmpty()) {
      PagedResults<T> second = source.page(firstPage + 1, limit);
      if (second.getCollection() != null) {
        collected.addAll(second.getCollection());
      }
      capped |= Boolean.TRUE.equals(second.getCountCapped());
    }
    List<T> slice = new ArrayList<>(collected.subList(Math.min(skip, collected.size()),
        Math.min(skip + limit, collected.size())));
    int end = request.offset() + slice.size();
    boolean more = total != null ? end < total : slice.size() == limit;
    PagedResults<T> page = new PagedResults<>(total, more, slice);
    if (capped) {
      page.setCountCapped(true);
    }
    return page;
  }
}
