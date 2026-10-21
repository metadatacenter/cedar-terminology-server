package org.metadatacenter.terms;

import org.metadatacenter.cedar.terminology.validation.integratedsearch.*;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.store.SnapshotStore;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/** Exact-identifier membership, independent of label search, paging, and UI save policy.
 * Every constraint is checked: a partially available union is never reported as verified.
 * An explicit pin never falls back to a current remote release. */
public final class TermMembershipVerifier {
  private final SqliteTerminologyService.SnapshotProvider provider;
  private final ITerminologyService remote;
  private static final int MAX_REMOTE_PARENT_LOOKUPS = 256;
  private static final class LookupBudget {
    private int remaining = 256;
    private final long deadline = System.nanoTime() + java.time.Duration.ofSeconds(20).toNanos();
    void use() {
      if (remaining-- <= 0 || System.nanoTime() > deadline)
        throw new UnsupportedOperationException("The exact verification lookup budget was exhausted; membership is unverified.");
    }
  }

  public TermMembershipVerifier(SqliteTerminologyService.SnapshotProvider provider, ITerminologyService remote) {
    this.provider = provider;
    this.remote = remote;
  }

  private record Scope(String acronym, String version, String iri, String sourceSystem) {}
  @FunctionalInterface private interface Check { boolean check() throws IOException, SQLException; }

  public TermVerification verify(String iri, ValueConstraints constraints, String apiKey) {
    if (constraints == null) return TermVerification.unavailable(iri, "No value constraints were supplied.");
    LookupBudget budget = new LookupBudget();
    List<Check> checks = new ArrayList<>();
    List<Scope> scopes = new ArrayList<>();
    for (OntologyValueConstraint c : list(constraints.getOntologies())) {
      Scope scope = new Scope(c.getAcronym(), c.getVersion(), c.getIri(), c.getSourceSystem());
      scopes.add(scope);
      checks.add(() -> contains(scope, iri, apiKey, budget));
    }
    for (BranchValueConstraint c : list(constraints.getBranches())) {
      Scope scope = new Scope(c.getAcronym(), c.getVersion(), c.getIri(), c.getSourceSystem());
      scopes.add(scope);
      checks.add(() -> inBranch(scope, c.getUri(), iri, c.getMaxDepth(), apiKey, budget));
    }
    for (ValueSetValueConstraint c : list(constraints.getValueSets())) {
      Scope scope = new Scope(acronym(c.getVsCollection()), c.getVersion(), c.getIri(), c.getSourceSystem());
      scopes.add(scope);
      // Value-set values are the direct children, not every descendant of the value-set node.
      checks.add(() -> !c.getUri().equals(iri) && inBranch(scope, c.getUri(), iri, 1, apiKey, budget));
    }
    for (ClassValueConstraint c : list(constraints.getClasses())) {
      Scope scope = new Scope(acronym(c.getSource()), c.getVersion(), c.getIri(), c.getSourceSystem());
      scopes.add(scope);
      checks.add(() -> {
        // An explicit class list proves admission by equality, but the admitted term must resolve
        // in its declared source/release. A stored label is not evidence of existence.
        return c.getUri().equals(iri) && contains(scope, iri, apiKey, budget);
      });
    }

    List<Action> matchingActions = list(constraints.getActions()).stream()
        .filter(a -> iri.equals(a.getTermUri())).toList();
    boolean moved = matchingActions.stream().anyMatch(a -> "move".equals(a.getAction()));
    boolean deleted = matchingActions.stream().anyMatch(a -> "delete".equals(a.getAction()));
    if (deleted && !moved) return answer(iri, false, "The identifier is excluded by the field's actions.");
    if (checks.isEmpty() && !moved) {
      return new TermVerification("notChecked", "semanticNotChecked",
          "The field declares no terminology membership constraint; term existence was not checked.", iri);
    }
    boolean admitted = false;
    List<String> unavailable = new ArrayList<>();
    for (Check check : checks) {
      try { admitted |= check.check(); }
      catch (UnsupportedOperationException e) {
        unavailable.add(e.getMessage() == null ? "A terminology source could not be checked." : e.getMessage());
      } catch (IOException | SQLException e) {
        unavailable.add("A terminology source could not be read for verification.");
      } catch (HTTPException e) {
        // A source refusing a relationship query is not proof of non-membership.
        unavailable.add("The terminology service could not verify membership (HTTP " + e.getStatusCode() + ").");
      }
    }
    if (moved && !admitted) {
      // Existing result actions can insert a named term. Inherit the field's version for that
      // source; do not silently resolve an action against latest when the field is pinned.
      Action action = matchingActions.stream().filter(a -> "move".equals(a.getAction())).findFirst().orElseThrow();
      String source = acronym(action.getSource());
      List<Scope> matching = scopes.stream().filter(s -> s.acronym().equals(source)).distinct().toList();
      try {
        if (matching.size() > 1) throw new UnsupportedOperationException("The moved term's source names multiple releases or identities.");
        Scope scope = matching.isEmpty() ? new Scope(source, null, null, null) : matching.get(0);
        admitted = contains(scope, iri, apiKey, budget);
      } catch (IOException | SQLException | UnsupportedOperationException | HTTPException e) {
        unavailable.add("The moved term could not be verified against its source.");
      }
    }
    if (!unavailable.isEmpty()) return TermVerification.unavailable(iri, String.join(" ", new LinkedHashSet<>(unavailable)));
    return answer(iri, admitted, admitted ? "The exact identifier is permitted by the field's constraints."
        : "The exact identifier is not permitted by the field's constraints.");
  }

  private static TermVerification answer(String iri, boolean admitted, String message) {
    return new TermVerification(admitted ? "passed" : "failed",
        admitted ? "controlledTermVerified" : "controlledTermMembership", message, iri);
  }

  private Optional<SnapshotStore> snapshot(Scope scope) throws SQLException {
    if (provider == null) return Optional.empty();
    if (pinned(scope.version()) && !provider.supportsVersionPins()) {
      throw new UnsupportedOperationException("The configured terminology store cannot verify a pinned release.");
    }
    if (scope.iri() != null) {
      // Canonical identity is additive, but when present it must match; acronym coincidence is
      // insufficient. A provider that cannot establish that identity must say so.
      if (!(provider instanceof CatalogSnapshotProvider catalog)
          || !catalog.catalog().ontologyIri(scope.acronym()).filter(scope.iri()::equals).isPresent()) {
        throw new UnsupportedOperationException("The requested vocabulary identity is not available: " + scope.acronym());
      }
    } else if (nonBioPortal(scope.sourceSystem())) {
      throw new UnsupportedOperationException("The requested source cannot be identified without its canonical vocabulary IRI.");
    }
    return provider.forOntology(scope.acronym(), scope.version());
  }

  private void requireRemote(Scope scope) {
    if (pinned(scope.version())) throw new UnsupportedOperationException("The pinned vocabulary release is unavailable: " + scope.acronym() + "@" + scope.version());
    if (scope.iri() != null || nonBioPortal(scope.sourceSystem())) {
      throw new UnsupportedOperationException("The requested vocabulary identity cannot be verified by the remote service.");
    }
    if (remote == null) throw new UnsupportedOperationException("The vocabulary is not available for verification: " + scope.acronym());
  }

  private boolean contains(Scope scope, String iri, String apiKey, LookupBudget budget) throws SQLException, IOException {
    Optional<SnapshotStore> local = snapshot(scope);
    if (local.isPresent()) return local.get().contains(iri);
    requireRemote(scope);
    return remoteClass(scope, iri, apiKey, budget) != null;
  }

  private OntologyClass remoteClass(Scope scope, String iri, String apiKey, LookupBudget budget) throws IOException {
    try {
      budget.use();
      OntologyClass found = remote.findClass(iri, scope.acronym(), apiKey);
      if (found != null) {
        if (!iri.equals(found.getLdId())) throw new IOException("The terminology service returned a different identifier.");
        requireSource(scope, found);
      }
      return found;
    } catch (HTTPException e) {
      // The legacy lookup tries the global provisional endpoint after any regular-class HTTP
      // failure. A terminal 404 can therefore carry a suppressed outage, not proof of absence.
      if (definitiveNotFound(e)) return null;
      throw e;
    } catch (RuntimeException e) {
      throw new IOException("The terminology service returned an unreadable class answer.", e);
    }
  }

  private static boolean definitiveNotFound(HTTPException failure) {
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Deque<Throwable> remaining = new ArrayDeque<>();
    remaining.add(failure);
    while (!remaining.isEmpty()) {
      Throwable next = remaining.removeFirst();
      if (!seen.add(next)) continue;
      if (!(next instanceof HTTPException http) || http.getStatusCode() != 404) return false;
      if (next.getCause() != null) remaining.add(next.getCause());
      remaining.addAll(Arrays.asList(next.getSuppressed()));
    }
    return true;
  }

  private static void requireSource(Scope scope, OntologyClass found) throws IOException {
    String source = found.getOntology();
    // In particular, the global provisional fallback must not prove membership in an unrelated
    // ontology. Only BioPortal's source identity, at data.bioontology.org or at the BioPortal this
    // server is configured to read, or its exact acronym establishes that scope.
    if (!scope.acronym().equals(source)
        && !("http://data.bioontology.org/ontologies/" + scope.acronym()).equals(source)
        && !("https://data.bioontology.org/ontologies/" + scope.acronym()).equals(source)
        && !(org.metadatacenter.cedar.terminology.util.Constants.BP_API_BASE + "ontologies/" + scope.acronym()).equals(source)) {
      throw new IOException("The terminology service did not establish the requested vocabulary identity.");
    }
  }

  private boolean inBranch(Scope scope, String root, String iri, Integer maximum, String apiKey, LookupBudget budget)
      throws SQLException, IOException {
    int depth = maximum == null ? 0 : maximum;
    Optional<SnapshotStore> local = snapshot(scope);
    if (local.isPresent()) {
      SnapshotStore store = local.get();
      if (!store.contains(root)) throw new UnsupportedOperationException("The constrained branch or value set is absent from its vocabulary release.");
      if (!store.contains(iri)) return false;
      if (root.equals(iri)) return true;
      if (depth == 0) return store.subsumes(root, iri);
      return reachesRoot(root, iri, depth, store::parents, Integer.MAX_VALUE);
    }
    requireRemote(scope);
    if (remoteClass(scope, root, apiKey, budget) == null) throw new UnsupportedOperationException("The constrained branch or value set cannot be resolved.");
    if (remoteClass(scope, iri, apiKey, budget) == null) return false;
    if (root.equals(iri)) return true;
    return reachesRoot(root, iri, depth, term -> {
      budget.use();
      try {
        List<OntologyClass> parents = remote.getClassParents(term, scope.acronym(), apiKey);
        if (parents == null) throw new IOException("The terminology service returned no hierarchy answer.");
        List<String> identifiers = new ArrayList<>();
        for (OntologyClass parent : parents) {
          if (parent == null || parent.getLdId() == null || parent.getLdId().isBlank())
            throw new IOException("The terminology service returned an incomplete hierarchy answer.");
          requireSource(scope, parent);
          identifiers.add(parent.getLdId());
        }
        return identifiers;
      } catch (RuntimeException e) {
        throw new IOException("The terminology service returned an unreadable hierarchy answer.", e);
      }
    }, MAX_REMOTE_PARENT_LOOKUPS);
  }

  @FunctionalInterface private interface Parents { List<String> of(String iri) throws SQLException, IOException; }
  private static boolean reachesRoot(String root, String iri, int maxDepth, Parents parents, int budget)
      throws SQLException, IOException {
    Set<String> seen = new HashSet<>();
    List<String> level = List.of(iri);
    for (int distance = 1; !level.isEmpty() && (maxDepth == 0 || distance <= maxDepth); distance++) {
      List<String> next = new ArrayList<>();
      for (String node : level) {
        if (!seen.add(node)) continue;
        if (seen.size() > budget) throw new UnsupportedOperationException("The exact hierarchy verification exceeded its lookup limit.");
        for (String parent : parents.of(node)) {
          if (root.equals(parent)) return true;
          if (!seen.contains(parent)) next.add(parent);
        }
      }
      level = next;
    }
    return false;
  }

  private static String acronym(String source) {
    int slash = source.lastIndexOf('/');
    return slash < 0 ? source : source.substring(slash + 1);
  }
  private static boolean pinned(String version) { return version != null && !version.isBlank() && !"latest".equalsIgnoreCase(version); }
  private static boolean nonBioPortal(String system) { return system != null && !system.isBlank() && !"bioportal".equalsIgnoreCase(system); }
  private static <T> List<T> list(List<T> value) { return value == null ? List.of() : value; }
}
