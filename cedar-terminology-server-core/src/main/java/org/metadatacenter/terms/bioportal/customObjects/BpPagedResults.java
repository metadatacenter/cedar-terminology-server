package org.metadatacenter.terms.bioportal.customObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpPagedResults<T>
{

  private int page;
  private int pageCount;
  private int totalCount;
  private int prevPage;
  private int nextPage;
  private List<T> collection;

  public BpPagedResults() {}

  public BpPagedResults(int page, int pageCount, int totalCount, int prevPage, int nextPage, List<T> collection)
  {
    this.page = page;
    this.pageCount = pageCount;
    this.totalCount = totalCount;
    this.prevPage = prevPage;
    this.nextPage = nextPage;
    this.collection = collection;
  }

  public int getPage()
  {
    return page;
  }

  public int getPageCount()
  {
    return pageCount;
  }

  public int getTotalCount() { return totalCount; }

  public int getPrevPage()
  {
    return prevPage;
  }

  public int getNextPage()
  {
    return nextPage;
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
