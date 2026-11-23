package org.metadatacenter.terms.customObjects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.http.PagedListResponse;

import java.util.List;
import java.util.Map;

/**
 * A page of results in CEDAR's body paging envelope.
 *
 * <p>{@code totalCount} counts every result, not just this page's. {@code request},
 * {@code currentOffset} and {@code paging} are the envelope, filled by {@link #envelope}.
 * {@code countCapped} is present, and true, only when the results were read from a window that did
 * not hold them all: {@code totalCount} is then the number the window reached rather than the whole,
 * and there is no {@code last} link.
 *
 * <p>The sources behind the routes page by number, and {@link #hasNextPage} records whether the
 * source holds a page after this one. It is read when a page is assembled and is never serialized.
 */
public class PagedResults<T>
{

  private Integer totalCount;
  private List<T> collection;
  @JsonIgnore
  private boolean hasNextPage;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean countCapped;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private PagedListResponse.PageRequest request;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long currentOffset;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, String> paging;

  public PagedResults() {}

  public PagedResults(Integer totalCount, boolean hasNextPage, List<T> collection)
  {
    this.totalCount = totalCount;
    this.hasNextPage = hasNextPage;
    this.collection = collection;
  }

  public Integer getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(Integer totalCount) {
    this.totalCount = totalCount;
  }

  /** Whether the source holds a page after this one. */
  @JsonIgnore
  public boolean hasNextPage() {
    return hasNextPage;
  }

  @JsonIgnore
  public void setHasNextPage(boolean hasNextPage) {
    this.hasNextPage = hasNextPage;
  }

  public List<T> getCollection()
  {
    return collection;
  }

  public void setCollection(List<T> collection)
  {
    this.collection = collection;
  }

  public Boolean getCountCapped() {
    return countCapped;
  }

  public void setCountCapped(Boolean countCapped) {
    this.countCapped = countCapped;
  }

  public PagedListResponse.PageRequest getRequest() {
    return request;
  }

  public Long getCurrentOffset() {
    return currentOffset;
  }

  public Map<String, String> getPaging() {
    return paging;
  }

  /**
   * Fills the envelope for the page served at {@code offset} with {@code limit}. Links are given only
   * when {@code requestUrl} is: a POST search has no URL to link to, and its client asks for the next
   * page by sending the offset.
   */
  public PagedResults<T> envelope(int limit, int offset, String requestUrl) {
    this.request = new PagedListResponse.PageRequest(limit, offset);
    this.currentOffset = (long) offset;
    if (requestUrl != null && totalCount != null) {
      Map<String, String> links =
          LinkHeaderUtil.getPagingLinkHeaders(requestUrl, totalCount.longValue(), limit, offset);
      if (Boolean.TRUE.equals(countCapped)) {
        links.remove(HttpConstants.HEADER_LINK_TYPE_LAST);
      }
      this.paging = links;
    }
    return this;
  }
}
