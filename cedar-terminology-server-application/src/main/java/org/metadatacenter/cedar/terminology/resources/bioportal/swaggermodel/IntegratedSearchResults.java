package org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

/**
 * Documentation-only model for the paginated results of the integrated-search endpoint.
 *
 * <p>This thin bean exists purely to reproduce the {@code IntegratedSearchResults} schema that the
 * hand-authored spec exposed. The spec models the results as an array of paginated-result objects;
 * this class mirrors a single paginated-result entry, used together with
 * {@code responseContainer = "List"} on the operation.</p>
 */
@ApiModel(value = "IntegratedSearchResults", description = "A paginated list of integrated-search results.")
public class IntegratedSearchResults {

  @ApiModelProperty(value = "Current page.", required = true)
  private Integer page;

  @ApiModelProperty(value = "Total number of pages.", required = true)
  private Integer pageCount;

  @ApiModelProperty(value = "Number of results per page.", required = true)
  private Integer pageSize;

  @ApiModelProperty(value = "Total number of results.", required = true)
  private Integer totalCount;

  @ApiModelProperty(value = "Previous page.", required = true)
  private Integer prevPage;

  @ApiModelProperty(value = "Next page.", required = true)
  private Integer nextPage;

  @ApiModelProperty(value = "The results contained in this page.", required = true)
  private List<Result> collection;

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

  public List<Result> getCollection() {
    return collection;
  }

  public void setCollection(List<Result> collection) {
    this.collection = collection;
  }

  @ApiModel(value = "IntegratedSearchResult")
  public static class Result {

    @ApiModelProperty(value = "Result identifier.", required = true)
    private String id;

    @ApiModelProperty(name = "@id", value = "Unique URL identifier of the result.", required = true)
    private String atId;

    @ApiModelProperty(name = "@type", value = "Type of the result.", required = true)
    private String atType;

    @ApiModelProperty(value = "Type of the result.", required = true)
    private String type;

    @ApiModelProperty(value = "Preferred label of the result.", required = true)
    private String prefLabel;

    @ApiModelProperty(value = "Notation of the result.", required = true)
    private String notation;

    @ApiModelProperty(value = "Definition of the result.", required = true)
    private String definition;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getAtId() {
      return atId;
    }

    public void setAtId(String atId) {
      this.atId = atId;
    }

    public String getAtType() {
      return atType;
    }

    public void setAtType(String atType) {
      this.atType = atType;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getPrefLabel() {
      return prefLabel;
    }

    public void setPrefLabel(String prefLabel) {
      this.prefLabel = prefLabel;
    }

    public String getNotation() {
      return notation;
    }

    public void setNotation(String notation) {
      this.notation = notation;
    }

    public String getDefinition() {
      return definition;
    }

    public void setDefinition(String definition) {
      this.definition = definition;
    }
  }
}
