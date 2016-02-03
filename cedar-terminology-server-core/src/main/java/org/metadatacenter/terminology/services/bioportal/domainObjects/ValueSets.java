package org.metadatacenter.terminology.services.bioportal.domainObjects;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.metadatacenter.terminology.services.bioportal.deserializers.SearchResultsDeserializer;
import org.metadatacenter.terminology.services.bioportal.deserializers.ValueSetsDeserializer;

import java.util.ArrayList;
import java.util.List;

@JsonDeserialize(using = ValueSetsDeserializer.class)
public class ValueSets
{

  private int page;
  private int pageCount;
  private int pageSize;
  private int prevPage;
  private int nextPage;
  private List<SingleValueSet> collection = new ArrayList<>();

  public ValueSets(int page, int pageCount, int pageSize, int prevPage, int nextPage,
    List<SingleValueSet> collection)
  {
    this.page = page;
    this.pageCount = pageCount;
    this.pageSize = pageSize;
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

  public List<SingleValueSet> getCollection()
  {
    return collection;
  }

  public void setCollection(List<SingleValueSet> collection)
  {
    this.collection = collection;
  }
}
