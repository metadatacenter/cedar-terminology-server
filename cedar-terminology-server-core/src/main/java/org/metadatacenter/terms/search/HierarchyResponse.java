package org.metadatacenter.terms.search;

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
    int descendantCount) {

  /** A step below the term, carrying enough to say whether it is worth opening in turn. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Child(String termIri, String termLabel, boolean hasChildren, int descendantCount,
                      /** What the source says it means, so a tree says which term it is offering. */
                      String definition) {}
}
