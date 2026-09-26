package org.metadatacenter.terms.customObjects;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A page of results with BioPortal's paging fields. {@code countCapped} is present, and true, only
 * when the results were read from a window that did not hold them all: {@code totalCount} is then
 * the number the window reached rather than the whole.
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
}
