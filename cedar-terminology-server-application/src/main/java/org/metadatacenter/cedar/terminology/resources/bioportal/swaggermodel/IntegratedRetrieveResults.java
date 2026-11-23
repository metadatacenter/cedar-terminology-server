package org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Documentation-only model for the paginated results of the integrated-retrieve endpoint.
 *
 * <p>This thin bean exists purely to reproduce the {@code IntegratedRetrieveResults} schema that the
 * hand-authored spec exposed. The spec models the results as an array of paginated-result objects;
 * this class mirrors a single paginated-result entry, used together with
 * {@code responseContainer = "List"} on the operation.</p>
 */
@Schema(name = "IntegratedRetrieveResults", description = "One page of integrated-retrieve results.")
public class IntegratedRetrieveResults {

  @Schema(description = "Total number of results.", requiredMode = Schema.RequiredMode.REQUIRED)
  private Integer totalCount;

  @Schema(description = "The results contained in this page.", requiredMode = Schema.RequiredMode.REQUIRED)
  private List<Result> collection;

  @Schema(description = "Present and true when totalCount counts only what could be read rather than everything that matches.")
  private Boolean countCapped;

  @Schema(description = "The page that was served, as a limit and an offset, after defaults were applied. The "
      + "answer carries no paging links: ask for the next page by sending the next offset.")
  private PageRequest request;

  @Schema(description = "The offset of the first result on this page.")
  private Long currentOffset;

  /** The limit and offset a page was served with. */
  @Schema(name = "TerminologyPageRequest")
  public record PageRequest(int limit, int offset) {
  }

  public Integer getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(Integer totalCount) {
    this.totalCount = totalCount;
  }

  public List<Result> getCollection() {
    return collection;
  }

  public Boolean getCountCapped() {
    return countCapped;
  }

  public PageRequest getRequest() {
    return request;
  }

  public Long getCurrentOffset() {
    return currentOffset;
  }

  public void setCollection(List<Result> collection) {
    this.collection = collection;
  }

  @Schema(name = "IntegratedRetrieveResult")
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
