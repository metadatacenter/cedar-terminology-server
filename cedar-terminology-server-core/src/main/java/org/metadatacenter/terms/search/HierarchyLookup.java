package org.metadatacenter.terms.search;

/**
 * The answer to a hierarchy request, including the reasons there is no tree to return.
 *
 * An empty result used to carry no reason, and three unrelated conditions arrived at a caller as
 * the same silence: a release identifier nothing matches, a release the store holds that does not
 * contain the term, and a term the cross-snapshot index does not hold. A client could only report
 * the first thing that came to mind, and the picker reported the store as holding no hierarchy in
 * all three cases — including for a nonexistent release, where nothing had been asked about the
 * term at all. Distinguishing them is the whole point of this type: each case is a different thing
 * to tell an author, and one of them is not about the term.
 */
public sealed interface HierarchyLookup {

  /** The term's ancestors and children, as the release or the index records them. */
  record Found(HierarchyResponse hierarchy) implements HierarchyLookup {}

  /**
   * No release of this source answers to that identifier, or the source is not served locally.
   *
   * Says nothing about the term: a release that cannot be resolved was never read.
   */
  record ReleaseNotHeld(String acronym, String versionId) implements HierarchyLookup {}

  /** The release is held and does not contain the term. Another release may well contain it. */
  record TermNotInRelease(String acronym, String termIri, String versionId) implements HierarchyLookup {}

  /** The cross-snapshot index, which holds each source's current release, does not hold the term. */
  record TermNotInIndex(String acronym, String termIri) implements HierarchyLookup {}
}
