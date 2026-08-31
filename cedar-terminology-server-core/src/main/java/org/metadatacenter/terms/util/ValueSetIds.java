package org.metadatacenter.terms.util;

import java.io.IOException;
import java.util.Set;

/**
 * The identifiers of every value set BioPortal serves, fetched on demand.
 *
 * <p>A search result drawn from a value-set collection is either a value set or one of its values,
 * and BioPortal's answer does not say which. Telling them apart means knowing which identifiers
 * name a value set, which costs one BioPortal call per value-set collection.
 *
 * <p>The search path passes this rather than the set itself so that cost falls only on a search
 * that actually returns a result from one of those collections. Most do not: a search scoped to an
 * ontology can never return one, and a corpus-wide search usually does not. Resolving it up front
 * charged every search — every keystroke — for an answer almost none of them used, and made a
 * search the local store could serve entirely on its own fail whenever BioPortal was unreachable.
 */
@FunctionalInterface
public interface ValueSetIds {

  /**
   * Nothing is a value set.
   *
   * <p>For a caller whose results cannot need classifying: a search already scoped to the values
   * within one value set, where every result is a value by construction.
   */
  ValueSetIds NONE = Set::of;

  Set<String> get() throws IOException;
}
