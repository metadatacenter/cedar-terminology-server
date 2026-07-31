package org.metadatacenter.terms.store;

import java.util.Locale;
import java.util.Optional;
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

  // A canonical OBO ontology IRI as produced by canonical(): ".../obo/<id>" (id lowercased).
  private static final Pattern OBO_IRI = Pattern.compile(".*/obo/([a-z][a-z0-9]*)$");

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

  /**
   * The OBO Foundry ontology id in a canonical OBO IRI ({@code .../obo/<id>}), or empty for a
   * non-OBO IRI. Used to pick the true owner when one canonical IRI is claimed by several
   * content-distinct ontologies (import leak): only the ontology whose acronym <i>is</i> that OBO id
   * genuinely owns the namespace; the rest merely import from it.
   */
  public static Optional<String> oboId(String canonicalIri) {
    if (canonicalIri == null) {
      return Optional.empty();
    }
    Matcher m = OBO_IRI.matcher(canonicalIri);
    return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
  }

  /**
   * Whether {@code acronym} is the OBO owner of {@code canonicalIri}: the IRI is an OBO ontology IRI
   * and the acronym, normalized (lowercased, non-alphanumerics dropped), equals its OBO id. So
   * {@code PO} owns {@code .../obo/po} but {@code GRO-CPGA} and {@code GO-PLUS} do not own
   * {@code .../obo/po} / {@code .../obo/go} — they import it. A non-OBO IRI has no derivable owner and
   * yields false, so a placeholder/host base shared by unrelated ontologies (webprotege, a Protégé
   * {@code ont.owl} default) is owned by none and declined for all.
   */
  public static boolean isOboOwner(String acronym, String canonicalIri) {
    if (acronym == null) {
      return false;
    }
    return oboId(canonicalIri).map(id -> normalizeAcronym(acronym).equals(id)).orElse(false);
  }

  /** An acronym reduced to its comparable core: lowercased, non-alphanumerics removed. */
  static String normalizeAcronym(String acronym) {
    return acronym.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }

  private OntologyIri() {}
}
