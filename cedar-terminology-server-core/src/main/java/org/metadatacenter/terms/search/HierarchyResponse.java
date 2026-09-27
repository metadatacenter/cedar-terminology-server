package org.metadatacenter.terms.search;

import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.http.PagedListResponse;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Where a term sits in its ontology: the chain above it, and what hangs directly below.
 *
 * A search result names a term. Whether it is the right term is a question about its neighbourhood,
 * and a label alone cannot answer it — "Disease" under "Clinical finding" and "Disease" under
 * "disposition" are different concepts that read identically on a row.
 *
 * Keyed to the same names the search response uses, so a client reads one vocabulary throughout.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HierarchyResponse(
    String sourceSystem,
    String sourceAcronym,
    SearchResponse.SourceBlock source,
    /** Root first, ending at the term's parent. Empty where the term is a root. */
    List<SearchResponse.TermRef> path,
    String termIri,
    String termLabel,
    /** Directly below, alphabetical, capped — {@code childCount} says how many there are in all. */
    List<Child> children,
    int childCount,
    /** Where the returned children start, so a client can ask for the rest. */
    int offset,
    int descendantCount,
    /**
     * What the source says the term itself means, where it says anything.
     *
     * The children carry theirs, and without this the one term the response is about was the one
     * term in the tree with nothing said about it.
     */
    String definition,
    /** The page of children asked for, as CEDAR's paging states it. */
    PagedListResponse.PageRequest request,
    /** How many children there are in all: {@code childCount}, under the name CEDAR's paging uses. */
    Integer totalCount,
    /** Where the returned children start: {@code offset}, under the name CEDAR's paging uses. */
    Long currentOffset,
    /** Links to the first, previous, next and last pages of children, keyed by relation. */
    Map<String, String> paging) {

  /** The response with its children's page stated as CEDAR's paging states one. */
  public HierarchyResponse paged(int limit, String requestUrl) {
    Map<String, String> links = requestUrl == null ? null
        : LinkHeaderUtil.getPagingLinkHeaders(requestUrl, (long) childCount, limit, offset);
    return new HierarchyResponse(sourceSystem, sourceAcronym, source, path, termIri, termLabel, children,
        childCount, offset, descendantCount, definition, new PagedListResponse.PageRequest(limit, offset),
        childCount, (long) offset, links);
  }

  /** A step below the term, carrying enough to say whether it is worth opening in turn. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Child(String termIri, String termLabel, boolean hasChildren, int descendantCount,
                      /** What the source says it means, so a tree says which term it is offering. */
                      String definition) {}
}
