package org.metadatacenter.terms.customObjects;

import java.util.List;

public class PagedResults<T>
{

  private int page;
  private int pageCount;
  private int pageSize;
  private int totalCount;
  private int prevPage;
  private int nextPage;
  private List<T> collection;

  public PagedResults() {}

  public PagedResults(int page, int pageCount, int pageSize, int totalCount, int prevPage, int nextPage, List<T> collection)
  {
    this.page = page;
    this.pageCount = pageCount;
    this.pageSize = pageSize;
    this.totalCount = totalCount;
    this.prevPage = prevPage;
    this.nextPage = nextPage;
    this.collection = collection;
  }

  public int getPage()
  {
    return page;
  }

  public void setPage(int page)
  {
    this.page = page;
  }

  public int getPageCount()
  {
    return pageCount;
  }

  public void setPageCount(int pageCount)
  {
    this.pageCount = pageCount;
  }

  public int getPageSize()
  {
    return pageSize;
  }

  public void setPageSize(int pageSize)
  {
    this.pageSize = pageSize;
  }

  public int getTotalCount() { return totalCount; }

  public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

  public int getPrevPage()
  {
    return prevPage;
  }

  public void setPrevPage(int prevPage)
  {
    this.prevPage = prevPage;
  }

  public int getNextPage()
  {
    return nextPage;
  }

  public void setNextPage(int nextPage)
  {
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
}
