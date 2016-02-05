package org.metadatacenter.terminology.bioportal.domainObjects;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.metadatacenter.terminology.bioportal.deserializers.SearchResultDeserializer;

@JsonDeserialize(using = SearchResultDeserializer.class)
public class SearchResult
{
  public enum ResultType {CLASS, VALUE_SET, VALUE};

  private String id;
  private String label;
  private ResultType resultType;
  private boolean provisional;
  // Source types:
  // - For class -> BioPortal ontology
  // - For value set -> value set collection (BioPortal ontology)
  // - For value -> value set (BioPortal class)
  private String source;
  private String creator;
  private String creationDate;

  public SearchResult(String id, String label, ResultType resultType, boolean provisional, String source,
    String creator, String creationDate)
  {
    this.id = id;
    this.label = label;
    this.resultType = resultType;
    this.provisional = provisional;
    this.source = source;
    this.creator = creator;
    this.creationDate = creationDate;
  }

  public String getId()
  {
    return id;
  }

  public void setId(String id)
  {
    this.id = id;
  }

  public String getLabel()
  {
    return label;
  }

  public void setLabel(String label)
  {
    this.label = label;
  }

  public ResultType getResultType()
  {
    return resultType;
  }

  public void setResultType(ResultType resultType)
  {
    this.resultType = resultType;
  }

  public boolean isProvisional()
  {
    return provisional;
  }

  public void setProvisional(boolean provisional)
  {
    this.provisional = provisional;
  }

  public String getSource()
  {
    return source;
  }

  public void setSource(String source)
  {
    this.source = source;
  }

  public String getCreator()
  {
    return creator;
  }

  public void setCreator(String creator)
  {
    this.creator = creator;
  }

  public String getCreationDate()
  {
    return creationDate;
  }

  public void setCreationDate(String creationDate)
  {
    this.creationDate = creationDate;
  }
}
