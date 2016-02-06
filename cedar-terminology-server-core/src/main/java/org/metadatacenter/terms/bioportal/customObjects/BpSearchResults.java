package org.metadatacenter.terms.bioportal.customObjects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BpSearchResults<T>
{

  private int page;
  private int pageCount;
  private int prevPage;
  private int nextPage;
  private List<T> collection;

  public BpSearchResults() {}

  public BpSearchResults(int page, int pageCount, int prevPage, int nextPage, List<T> collection)
  {
    this.page = page;
    this.pageCount = pageCount;
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
