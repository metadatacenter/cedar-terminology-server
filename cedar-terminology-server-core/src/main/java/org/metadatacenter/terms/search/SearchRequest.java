package org.metadatacenter.terms.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * The body of {@code POST /search}: a query, the constraint types to answer, and the sources to
 * search with the version each is searched at.
 *
 * Keys are the versioned value-constraint specification's, so a result can become a constraint
 * entry without translation. The design, including what a response carries, is in
 * {@code cedar-development/ops/VERSION-AWARE-SEARCH.md}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchRequest(
    String query,
    List<String> types,
    List<SourceSelector> sources,
    String lang,
    Integer page,
    Integer pageSize) {

  /** A source to search, and the version to search it at. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SourceSelector(String sourceSystem, String sourceAcronym, VersionSelector version) {

    /** The system, defaulted. Absent or blank means BioPortal, as the constraint spec defines it. */
    public String systemOrDefault() {
      return sourceSystem == null || sourceSystem.isBlank() ? SearchRequest.BIOPORTAL : sourceSystem.trim();
    }
  }

  /**
   * A pinned version, or null for latest.
   *
   * Written either as an object carrying the content hash — {@code {"id": "63ef…"}} — or as the
   * string {@code "latest"}, which is how the constraint spec spells an unpinned entry. Both reach
   * one delegating creator because the alternative, two creators on one type, is where Jackson
   * starts choosing for you.
   */
  public record VersionSelector(String id) {

    @JsonCreator
    public static VersionSelector of(JsonNode node) {
      if (node == null || node.isNull()) {
        return null;
      }
      String id = node.isTextual() ? node.asText() : text(node.get("id"));
      if (id == null || id.isBlank() || LATEST.equalsIgnoreCase(id.trim())) {
        return null;
      }
      return new VersionSelector(id.trim());
    }

    private static String text(JsonNode node) {
      return node == null || node.isNull() ? null : node.asText();
    }
  }

  public static final String LATEST = "latest";
  public static final String BIOPORTAL = "bioportal";

  public static final String TYPE_ONTOLOGY = "ontology";
  public static final String TYPE_BRANCH = "branch";
  public static final String TYPE_CLASS = "class";
  public static final String TYPE_VALUE_SET = "valueSet";

  /** The four constraint types, in the order a response reports them. */
  public static final List<String> ALL_TYPES = List.of(TYPE_ONTOLOGY, TYPE_BRANCH, TYPE_CLASS, TYPE_VALUE_SET);

  public String queryOrEmpty() {
    return query == null ? "" : query.trim();
  }

  /** The requested types, or all four when none are named. */
  public List<String> typesOrAll() {
    return types == null || types.isEmpty() ? ALL_TYPES : types;
  }

  public List<SourceSelector> sourcesOrEmpty() {
    return sources == null ? List.of() : sources;
  }
}
