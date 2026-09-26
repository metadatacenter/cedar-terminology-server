package org.metadatacenter.terms.customObjects;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.http.PagedListResponse;

import java.util.List;
import java.util.Map;

/**
 * A page of results with BioPortal's paging fields and, beside them, CEDAR's body paging envelope.
 *
 * <p>{@code page}, {@code pageCount}, {@code pageSize}, {@code prevPage} and {@code nextPage} are
 * BioPortal's, kept for the clients that page by number; {@code pageSize} counts the results on this
 * page, not the size asked for. {@code request}, {@code currentOffset} and {@code paging} are the
 * envelope, filled by {@link #envelope}. {@code countCapped} is present, and true, only when the
 * results were read from a window that did not hold them all: {@code totalCount} is then the number
 * the window reached rather than the whole, and there is no {@code last} link.
 */
public class PagedResults<T>
{

  private Integer page;
  private Integer pageCount;
  private Integer pageSize;
  private Integer totalCount;
  private Integer prevPage;
  private Integer nextPage;
  private List<T> collection;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean countCapped;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private PagedListResponse.PageRequest request;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long currentOffset;
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Map<String, String> paging;

  public PagedResults() {}

  public PagedResults(Integer page, Integer pageCount, Integer pageSize, Integer totalCount, Integer prevPage, Integer nextPage, List<T> collection)
  {
    this.page = page;
    this.pageCount = pageCount;
    this.pageSize = pageSize;
    this.totalCount = totalCount;
    this.prevPage = prevPage;
    this.nextPage = nextPage;
    this.collection = collection;
  }

  public Integer getPage() {
    return page;
  }

  public void setPage(Integer page) {
    this.page = page;
  }

  public Integer getPageCount() {
    return pageCount;
  }

  public void setPageCount(Integer pageCount) {
    this.pageCount = pageCount;
  }

  public Integer getPageSize() {
    return pageSize;
  }

  public void setPageSize(Integer pageSize) {
    this.pageSize = pageSize;
  }

  public Integer getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(Integer totalCount) {
    this.totalCount = totalCount;
  }

  public Integer getPrevPage() {
    return prevPage;
  }

  public void setPrevPage(Integer prevPage) {
    this.prevPage = prevPage;
  }

  public Integer getNextPage() {
    return nextPage;
  }

  public void setNextPage(Integer nextPage) {
    this.nextPage = nextPage;
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
