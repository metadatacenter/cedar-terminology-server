package org.metadatacenter.terms.ingest;

import java.util.ArrayList;
import java.util.List;

/**
 * What counts as a name, applied to the literals an ontology offers as one.
 *
 * A name is a single line of text. Sources do not always oblige: some assert a label padded with
 * whitespace, and some put a list into one literal, separating the entries with line breaks rather
 * than asserting each as its own label. Both reach a reader as a name — the padded one silently, the
 * list as every entry run together on one line, because a display collapses the breaks.
 *
 * So a literal is reduced to its first non-blank line, trimmed, and the lines below it become names
 * in their own right. That keeps each entry findable, where the run-on could only be matched by a
 * query that spanned two of them, and it invents nothing: every name still comes from the source.
 */
final class Names {

  private Names() {
  }

  /**
   * The name a literal offers, or {@code null} if it offers none.
   *
   * The first non-blank line, trimmed. A literal that is blank, or has no non-blank line, is not a
   * name: taking one leaves the concept unlabeled as far as everything downstream is concerned, and
   * it then draws the IRI-fragment fallback.
   */
  static String nameOf(String literal) {
    if (literal == null) {
      return null;
    }
    for (String line : literal.split("\\R")) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty()) {
        return trimmed;
      }
    }
    return null;
  }

  /**
   * The names a literal carries beyond the first: every later non-blank line, trimmed.
   *
   * Empty for the single-line literal that nearly every source asserts, so this only adds rows where
   * a source packed a list into one literal.
   */
  static List<String> restOf(String literal) {
    if (literal == null || !hasBreak(literal)) {
      return List.of();
    }
    List<String> rest = new ArrayList<>();
    boolean first = true;
    for (String line : literal.split("\\R")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (first) {
        first = false;
        continue;
      }
      rest.add(trimmed);
    }
    return rest;
  }

  /**
   * Whether a literal holds a line break, and so is a list rather than a name.
   *
   * Used to rank one candidate label against another: where a concept asserts both a plain label and
   * a list, the plain one is the name the source meant. ABD's meningitis class asserts
   * {@code "Meningitis"} and a six-line list of the kinds of meningitis, and the list was winning.
   */
  static boolean hasBreak(String literal) {
    return literal != null && literal.lines().count() > 1;
  }
}
