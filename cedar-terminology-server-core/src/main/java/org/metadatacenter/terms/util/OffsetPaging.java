package org.metadatacenter.terms.util;

import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.util.http.PagedQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads either kind of paging a terminology route accepts, and serves an offset from sources that
 * page by number.
 *
 * <p>The routes take {@code limit} and {@code offset}, CEDAR's paging. They still take the one-based
 * {@code page} and a page size, which is what BioPortal takes and what their older clients send. A
 * request may use one kind or the other, not both.
 *
 * <p>Every source behind these routes pages by number, BioPortal and the local store alike. An
 * offset on a page boundary is one page; an offset inside a page is served from the two pages it
 * straddles, so a client never has to align its offset to anything.
 */
public final class OffsetPaging {

  /** The largest page a client may ask for by limit. */
  public static final int MAX_LIMIT = 1000;

  private OffsetPaging() {
  }

  /**
   * The page asked for, as an offset and a limit.
   *
   * @param byOffset whether the client sent {@code limit} or {@code offset}, rather than a page number
   */
  public record Request(int offset, int limit, boolean byOffset) {
  }

  /** One source, read a page at a time, with one-based page numbers. */
  @FunctionalInterface
  public interface PageSource<T> {
    PagedResults<T> page(int page, int size) throws IOException;
  }

  /**
   * The page a request asks for.
   *
   * @param page         the one-based page, or null when none was sent
   * @param pageSize     the page size, already defaulted
   * @param pageSizeSent whether a page size was sent in either spelling
   */
  public static Request resolve(Integer limit, Integer offset, Integer page, int pageSize, boolean pageSizeSent,
                                int defaultLimit) throws CedarException {
    boolean byNumber = page != null || pageSizeSent;
    boolean byOffset = limit != null || offset != null;
    if (byNumber && byOffset) {
      throw new CedarAssertionException("Send either limit and offset or page and a page size, not both")
          .badRequest();
    }
    if (byOffset) {
      PagedQuery query = new PagedQuery(defaultLimit, MAX_LIMIT)
          .limit(Optional.ofNullable(limit)).offset(Optional.ofNullable(offset));
      query.validate();
      return new Request(query.getOffset(), query.getLimit(), true);
    }
    int number = page == null ? 1 : Math.max(1, page);
    return new Request((number - 1) * pageSize, pageSize, false);
  }

  /**
   * The page a request asks for, read from a source that pages by number.
   *
   * <p>A request by page number is passed to the source as it was, and the source's own paging
   * fields are kept. A request by offset has its paging fields worked out from the offset.
   */
  public static <T> PagedResults<T> fetch(Request request, PageSource<T> source) throws IOException {
    int limit = request.limit();
    int firstPage = request.offset() / limit + 1;
    if (!request.byOffset()) {
      return source.page(firstPage, limit);
    }
    int skip = request.offset() % limit;
    PagedResults<T> first = source.page(firstPage, limit);
    List<T> collected = new ArrayList<>(first.getCollection() == null ? List.of() : first.getCollection());
    Integer total = first.getTotalCount();
    boolean capped = Boolean.TRUE.equals(first.getCountCapped());
    if (skip > 0 && first.getNextPage() != null && !collected.isEmpty()) {
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
    PagedResults<T> page = new PagedResults<>(firstPage,
        total == null ? null : (int) Math.ceil((double) total / limit), slice.size(), total,
        request.offset() > 0 ? Math.max(1, (request.offset() - 1) / limit + 1) : null,
        more ? end / limit + 1 : null, slice);
    if (capped) {
      page.setCountCapped(true);
    }
    return page;
  }
}
