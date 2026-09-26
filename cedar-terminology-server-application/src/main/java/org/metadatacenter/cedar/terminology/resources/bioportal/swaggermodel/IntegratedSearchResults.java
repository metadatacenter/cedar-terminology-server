package org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Documentation-only model for the paginated results of the integrated-search endpoint.
 *
 * <p>This thin bean exists purely to reproduce the {@code IntegratedSearchResults} schema that the
 * hand-authored spec exposed. The spec models the results as an array of paginated-result objects;
 * this class mirrors a single paginated-result entry, used together with
 * {@code responseContainer = "List"} on the operation.</p>
 */
@Schema(name = "IntegratedSearchResults", description = "A paginated list of integrated-search results.")
public class IntegratedSearchResults {

  @Schema(description = "Current page.", requiredMode = Schema.RequiredMode.REQUIRED)
  private Integer page;

  @Schema(description = "Total number of pages.", requiredMode = Schema.RequiredMode.REQUIRED)
  private Integer pageCount;

  @Schema(description = "Number of results per page.", requiredMode = Schema.RequiredMode.REQUIRED)
  private Integer pageSize;

  @Schema(description = "Total number of results.", requiredMode = Schema.RequiredMode.REQUIRED)
  private Integer totalCount;

  @Schema(description = "Previous page.", requiredMode = Schema.RequiredMode.REQUIRED)
  private Integer prevPage;

  @Schema(description = "Next page.", requiredMode = Schema.RequiredMode.REQUIRED)
  private Integer nextPage;

  @Schema(description = "The results contained in this page.", requiredMode = Schema.RequiredMode.REQUIRED)
  private List<Result> collection;

  @Schema(description = "The page that was asked for, as a limit and an offset, after defaults were applied.")
  private PageRequest request;

  @Schema(description = "The offset of the first result on this page.")
  private Long currentOffset;

  /** The limit and offset a page was served with. */
  @Schema(name = "TerminologyPageRequest")
  public record PageRequest(int limit, int offset) {
  }

  @Schema(description = "Present and true when a reordered search read only the first 1,000 results of some "
      + "source, so totalCount counts what was read rather than everything that matches.")
  private Boolean countCapped;

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

  public PageRequest getRequest() {
    return request;
  }

  public Long getCurrentOffset() {
    return currentOffset;
  }

  public Boolean getCountCapped() {
    return countCapped;
  }

  public void setCollection(List<Result> collection) {
    this.collection = collection;
  }

  @Schema(name = "IntegratedSearchResult")
  public static class Result {

    @Schema(description = "Result identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(name = "@id", description = "Unique URL identifier of the result.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String atId;

    @Schema(name = "@type", description = "Type of the result.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String atType;

    @Schema(description = "Type of the result.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "Preferred label of the result.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prefLabel;

    @Schema(description = "Notation of the result.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String notation;

    @Schema(description = "Definition of the result.", requiredMode = Schema.RequiredMode.REQUIRED)
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
