package org.metadatacenter.terms.search;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * The body of a {@code POST /search} response: the sources that were searched, described once, and
 * the results of each constraint type.
 *
 * The split between the two is between a key and an attribute. A hit names its source with the
 * {@code sourceSystem} and {@code sourceAcronym} pair and carries nothing else about it, because
 * whether a source can be pinned, and which version it was searched at, are properties of the
 * source within this request rather than of any one term. Saying them per hit would state one fact
 * once per result and let the copies disagree.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResponse(String query, List<SourceBlock> sources, Map<String, TypeResults> results) {

  /** How one source answered: at which version, from where, and therefore whether it can be pinned. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SourceBlock(
      String sourceSystem,
      String sourceAcronym,
      String sourceName,
      String sourceIri,
      String served,
      boolean pinnable,
      VersionInfo version,
      String reason,
      SearchRequest.VersionSelector requestedVersion) {

    /** Served from the local store at an exact snapshot, and therefore pinnable. */
    public static final String SERVED_LOCAL = "local";
    /** Served from BioPortal at whatever it currently holds; no content hash, never pinnable. */
    public static final String SERVED_PROXIED = "proxied";
    /** Not searched. The results say nothing about this source, which is why it is reported. */
    public static final String SERVED_UNAVAILABLE = "unavailable";

    /** The store holds the source but not the version the request pinned. */
    public static final String REASON_VERSION_NOT_HELD = "versionNotHeld";
    /** The source is not served from the local store. */
    public static final String REASON_SOURCE_NOT_SERVED = "sourceNotServed";
    /** No such source in the catalog. */
    public static final String REASON_SOURCE_UNKNOWN = "sourceUnknown";
  }

  /**
   * The version a source was searched at. {@code id} is the content hash and is what makes a pin
   * reproducible; the other two are human-facing labels and identify nothing.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record VersionInfo(String id, String effectiveDate, String declaredVersion) {}

  /**
   * One type's results. {@code countCapped} distinguishes a count from a ceiling: when it is true,
   * {@code totalCount} is where counting stopped rather than how many matched, and a client that
   * renders it as an exact figure is lying on the server's behalf.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record TypeResults(
      int totalCount,
      boolean countCapped,
      Integer distinctLabelCount,
      Boolean distinctLabelCountCapped,
      int page,
      int pageSize,
      List<? extends Hit> collection) {

    public TypeResults(int totalCount, boolean countCapped, int page, int pageSize,
                       List<? extends Hit> collection) {
      this(totalCount, countCapped, null, null, page, pageSize, collection);
    }
  }

  /** A match. Every hit names its constraint type and the source it came from, and nothing more of it. */
  public interface Hit {
    String type();

    String sourceSystem();

    String sourceAcronym();
  }

  /** A term identified and named — a path step, an example descendant, a matched value. */
  public record TermRef(String termIri, String termLabel) {}

  /** A captured name that matched the query, in the language it was recorded under. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MatchedLabel(String label, String language) {}

  /** Matched on the served preferred label. */
  public static final String MATCH_TERM_LABEL = "termLabel";
  /** Matched on a synonym or on a label in another language; {@code matchedLabels} says which. */
  public static final String MATCH_SYNONYM = "synonym";
  /** An ontology matched on its acronym. */
  public static final String MATCH_SOURCE_ACRONYM = "sourceAcronym";
  /** An ontology matched on its name. */
  public static final String MATCH_SOURCE_NAME = "sourceName";
  /** A value set matched on its own name. */
  public static final String MATCH_TERM_BASE_LABEL = "termBaseLabel";
  /** A value set matched because one of its values did; {@code matchedTerms} says which. */
  public static final String MATCH_MEMBER = "member";
  /** An ontology surfaced because the query matched terms in it, not because of its name. */
  public static final String MATCH_TERMS = "terms";

  /** A specific term. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ClassHit(
      String type,
      String sourceSystem,
      String sourceAcronym,
      String termIri,
      String termType,
      String termLabel,
      String matchType,
      List<MatchedLabel> matchedLabels,
      boolean obsolete,
      TermRef replacedBy,
      boolean hasChildren,
      int descendantCount) implements Hit {}

  /** Everything at or below a term. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record BranchHit(
      String type,
      String sourceSystem,
      String sourceAcronym,
      String termBaseIri,
      String termBaseLabel,
      int descendantCount,
      String matchType,
      List<MatchedLabel> matchedLabels,
      boolean obsolete,
      List<TermRef> path,
      List<TermRef> examples) implements Hit {}

  /**
   * A whole vocabulary. Thin, because its source block already carries everything else.
   *
   * {@code termCount} is the vocabulary's size, as the constraint spec defines it. {@code matchCount}
   * is how many of its terms this query matched, which is evidence rather than part of a constraint
   * and is the difference between "is there a vocabulary named this" and "which vocabulary should
   * this field draw from".
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record OntologyHit(
      String type,
      String sourceSystem,
      String sourceAcronym,
      Integer termCount,
      String matchType,
      Integer matchCount) implements Hit {}

  /** A curated list. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ValueSetHit(
      String type,
      String sourceSystem,
      String sourceAcronym,
      String termBaseIri,
      String termBaseLabel,
      Integer termCount,
      String matchType,
      List<TermRef> matchedTerms) implements Hit {}
}
