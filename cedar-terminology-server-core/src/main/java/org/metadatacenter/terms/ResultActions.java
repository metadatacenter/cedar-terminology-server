package org.metadatacenter.terms;

import jakarta.ws.rs.BadRequestException;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.Action;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ClassValueConstraint;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.terms.domainObjects.SearchResult;
import org.metadatacenter.terms.util.ObjectConverter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.metadatacenter.cedar.terminology.util.Constants.CEDAR_VALUE_ARRANGEMENTS_ACTION_DELETE;
import static org.metadatacenter.cedar.terminology.util.Constants.CEDAR_VALUE_ARRANGEMENTS_ACTION_MOVE;

/**
 * A field's result actions, applied to the results its constraints admit.
 *
 * <p>Every term an action names is removed, then each 'move' term is inserted at its position. The
 * list is the whole set of results about to be paged, not one page of them, so a removed term leaves
 * no hole and a move puts its term where it says on whichever page that is.
 *
 * <p>Both paths that serve integrated search apply actions here, the BioPortal path and the local
 * store, so a field means the same thing whichever answers it. They differ only in where a moved term
 * that is not among the results is looked up, which the caller supplies.
 */
final class ResultActions {

  /** Looks up a moved term that is not among the results. Null means the source no longer has it. */
  @FunctionalInterface
  interface TermResolver {
    SearchResult resolve(Action action) throws IOException;
  }

  private ResultActions() {
  }

  static boolean present(ValueConstraints valueConstraints) {
    return valueConstraints != null && valueConstraints.getActions() != null
        && !valueConstraints.getActions().isEmpty();
  }

  static List<SearchResult> apply(List<SearchResult> results, ValueConstraints valueConstraints,
                                  TermResolver resolver) throws IOException {
    List<SearchResult> updatedResults = new ArrayList<>();

    // Sort actions to apply them in the right order. First, we will apply the 'delete' actions. Then, move actions
    // must be applied in order, from highest to lowest rank
    List<Action> moveActions = new ArrayList<>();
    List<String> actionTermUris = new ArrayList<>();
    for (Action action : valueConstraints.getActions()) {
      if (action.getAction().equals(CEDAR_VALUE_ARRANGEMENTS_ACTION_MOVE)) {
        moveActions.add(action);
      } else if (!action.getAction().equals(CEDAR_VALUE_ARRANGEMENTS_ACTION_DELETE)) {
        throw new BadRequestException("Invalid action: " + action.getAction());
      }
      actionTermUris.add(action.getTermUri());
    }

    // Ignore classes referenced by actions
    for (SearchResult result : results) {
      if (!actionTermUris.contains(result.getLdId())) {
        updatedResults.add(result);
      }
    }

    moveActions.sort(Comparator.comparing(Action::getTo));

    // Now, insert the classes referenced by 'move' actions into the right position, clamped to the
    // list: every action's term was removed above, so skipping an out-of-range one would drop the
    // term rather than move it.
    for (Action action : moveActions) {
      SearchResult moved = resolve(action, results, valueConstraints.getClasses(), resolver);
      // A term the source no longer serves costs its own action's effect and nothing more.
      // It was not among the results, so the removal above took nothing out of the list.
      if (moved == null) {
        continue;
      }
      int position = Math.max(0, Math.min(action.getTo(), updatedResults.size()));
      updatedResults.add(position, moved);
    }
    return updatedResults;
  }

  /**
   * The term a 'move' action names: from the results when it is among them, from the field's
   * enumerated classes when it is one, and otherwise from the resolver.
   */
  private static SearchResult resolve(Action action, List<SearchResult> results,
                                      List<ClassValueConstraint> enumeratedClasses,
                                      TermResolver resolver) throws IOException {
    for (SearchResult result : results) {
      if (result.getLdId().equals(action.getTermUri())) {
        return result;
      }
    }
    if (enumeratedClasses != null) {
      for (ClassValueConstraint c : enumeratedClasses) {
        if (c.getUri().equals(action.getTermUri())) {
          return ObjectConverter.toSearchResult(c);
        }
      }
    }
    return resolver.resolve(action);
  }
}
