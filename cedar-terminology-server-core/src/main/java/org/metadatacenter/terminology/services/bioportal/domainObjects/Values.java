package org.metadatacenter.terminology.services.bioportal.domainObjects;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.metadatacenter.terminology.services.bioportal.deserializers.ValueSetsDeserializer;
import org.metadatacenter.terminology.services.bioportal.deserializers.ValuesDeserializer;

import java.util.ArrayList;
import java.util.List;

@JsonDeserialize(using = ValuesDeserializer.class)
public class Values
{

  private int page;
  private int pageCount;
  private int pageSize;
  private int prevPage;
  private int nextPage;
  private List<Value> values = new ArrayList<>();

  public Values(int page, int pageCount, int pageSize, int prevPage, int nextPage, List<Value> values)
  {
    this.page = page;
    this.pageCount = pageCount;
    this.pageSize = pageSize;
    this.prevPage = prevPage;
    this.nextPage = nextPage;
    this.values = values;
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

  public List<Value> getValues()
  {
    return values;
  }

  public void setValues(List<Value> values)
  {
    this.values = values;
  }
}
