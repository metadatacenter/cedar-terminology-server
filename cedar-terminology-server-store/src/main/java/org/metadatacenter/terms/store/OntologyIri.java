package org.metadatacenter.terms.store;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes a raw term-ID namespace to the canonical ontology IRI that identifies an ontology
 * across sources (VERSIONING-DESIGN §6.4).
 *
 * The namespace derived from an ontology's own concepts is a term-ID prefix — {@code
 * http://purl.obolibrary.org/obo/DOID_} (trailing {@code _}) or {@code
 * http://purl.bioontology.org/ontology/MESH/} (trailing {@code /}) — not a clean ontology IRI. This
 * folds it to a stable base, uniformly:
 * <ul>
 *   <li><b>OBO</b> term prefix → drop the trailing {@code _} and lowercase the id, yielding the OBO
 *       Foundry ontology IRI: {@code .../obo/DOID_} → {@code .../obo/doid}.</li>
 *   <li><b>Other</b> namespace → strip a single trailing separator ({@code /} or {@code #}),
 *       preserving case: {@code .../ontology/MESH/} → {@code .../ontology/MESH}; {@code
 *       http://www.ebi.ac.uk/efo/} → {@code http://www.ebi.ac.uk/efo}.</li>
 * </ul>
 *
 * The canonical IRI is identity; the raw namespace it was folded from is kept alongside as
 * provenance.
 */
public final class OntologyIri {

  // A raw OBO term-ID namespace as produced by SnapshotStore.idspace: ".../obo/<PREFIX>_".
  private static final Pattern OBO_TERM = Pattern.compile("(.*/obo/)([A-Za-z][A-Za-z0-9]*)_$");

  /**
   * The canonical ontology IRI for a raw term-ID {@code namespace}. Null/blank in, same out (nothing
   * to normalize).
   */
  public static String canonical(String namespace) {
    if (namespace == null || namespace.isEmpty()) {
      return namespace;
    }
    Matcher obo = OBO_TERM.matcher(namespace);
    if (obo.matches()) {
      return obo.group(1) + obo.group(2).toLowerCase();
    }
    char last = namespace.charAt(namespace.length() - 1);
    if (last == '/' || last == '#') {
      return namespace.substring(0, namespace.length() - 1);
    }
    return namespace;
  }

  private OntologyIri() {}
}
