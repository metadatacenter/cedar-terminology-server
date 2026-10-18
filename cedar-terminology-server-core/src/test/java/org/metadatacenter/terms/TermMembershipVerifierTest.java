package org.metadatacenter.terms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.terms.store.SnapshotStore;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TermMembershipVerifierTest {
  private SnapshotStore current;
  private SnapshotStore old;
  private TermMembershipVerifier verifier;
  private static final String BASE = "https://example.org/";

  @BeforeEach void setup() throws Exception {
    current = SnapshotStore.openInMemory(); current.initSchema();
    old = SnapshotStore.openInMemory(); old.initSchema();
    for (String name : List.of("root", "child", "grandchild", "outside", "a+b", "a%2Bb")) current.addConcept(BASE + name, "same label");
    current.addEdge(BASE + "child", BASE + "root", "subclass");
    current.addEdge(BASE + "grandchild", BASE + "child", "subclass");
    current.materialize();
    old.addConcept(BASE + "root", "old label"); old.materialize();
    var provider = new SqliteTerminologyService.SnapshotProvider() {
      public boolean supportsVersionPins() { return true; }
      public Optional<SnapshotStore> forOntology(String source) { return forOntology(source, null); }
      public Optional<SnapshotStore> forOntology(String source, String version) {
        if (!"EX".equals(source)) return Optional.empty();
        if (version == null || "latest".equals(version)) return Optional.of(current);
        return "old".equals(version) ? Optional.of(old) : Optional.empty();
      }
    };
    verifier = new TermMembershipVerifier(provider, null);
  }
  @AfterEach void close() throws Exception { current.close(); old.close(); }
  private TermVerification check(String term, String constraints) throws Exception {
    return verifier.verify(BASE + term, new ObjectMapper().readValue(constraints, ValueConstraints.class), "key");
  }

  @Test void exactIdentifiersNeverUseLabelsOrDecodePercentEscapes() throws Exception {
    String classes = "{\"classes\":[{\"uri\":\"" + BASE + "a+b\",\"prefLabel\":\"stale label\",\"source\":\"EX\",\"type\":\"OntologyClass\"}]}";
    assertEquals("passed", check("a+b", classes).status());
    assertEquals("failed", check("a%2Bb", classes).status());
    assertEquals("failed", check("child", classes).status());
  }
  @Test void ontologyReleasePinsAreHonoredAndNeverFallBack() throws Exception {
    assertEquals("passed", check("child", "{\"ontologies\":[{\"acronym\":\"EX\"}]}").status());
    assertEquals("failed", check("child", "{\"ontologies\":[{\"acronym\":\"EX\",\"version\":{\"id\":\"old\"}}]}").status());
    assertEquals("unavailable", check("child", "{\"ontologies\":[{\"acronym\":\"EX\",\"version\":\"missing\"}]}").status());
  }
  @Test void branchesIncludeRootAndHonorMaximumDepth() throws Exception {
    String branch = "{\"branches\":[{\"uri\":\"" + BASE + "root\",\"acronym\":\"EX\",\"maxDepth\":1}]}";
    assertEquals("passed", check("root", branch).status());
    assertEquals("passed", check("child", branch).status());
    assertEquals("failed", check("grandchild", branch).status());
    assertEquals("failed", check("outside", branch).status());
    assertEquals("passed", check("grandchild", branch.replace(":1", ":0")).status());
  }
  @Test void valueSetsContainDirectMembersOnly() throws Exception {
    String vs = "{\"valueSets\":[{\"uri\":\"" + BASE + "root\",\"vsCollection\":\"EX\"}]}";
    assertEquals("passed", check("child", vs).status());
    assertEquals("failed", check("root", vs).status());
    assertEquals("failed", check("grandchild", vs).status());
  }
  @Test void exclusionsAndMoveActionsUseExistingUnionSemantics() throws Exception {
    String constraint = "{\"branches\":[{\"uri\":\"" + BASE + "root\",\"acronym\":\"EX\"}],\"actions\":[{\"action\":\"delete\",\"termUri\":\"" + BASE + "child\",\"source\":\"EX\",\"type\":\"OntologyClass\"}]}";
    assertEquals("failed", check("child", constraint).status());
    String move = constraint.replace("delete", "move").replace("\"source\"", "\"to\":0,\"source\"").replace(BASE + "child", BASE + "outside");
    assertEquals("passed", check("outside", move).status());
    assertEquals("failed", check("outside", move.replace("\"acronym\":\"EX\"", "\"acronym\":\"EX\",\"version\":\"old\"" )).status());
  }
  @Test void partialCoverageCannotBecomeSuccessOrFailure() throws Exception {
    assertEquals("unavailable", check("child", "{\"ontologies\":[{\"acronym\":\"EX\"},{\"acronym\":\"MISSING\"}]}").status());
    assertEquals("unavailable", check("unrecognized", "{\"ontologies\":[{\"acronym\":\"MISSING\"}]}").status());
    assertEquals("notChecked", check("child", "{}").status());
    assertEquals("unavailable", check("child", "{\"ontologies\":[{\"acronym\":\"EX\",\"sourceSystem\":\"other-registry\"}]}").status());
  }
  @Test void aLegacyProviderCannotSilentlyIgnoreTheRequestedVersion() throws Exception {
    verifier = new TermMembershipVerifier(source -> Optional.of(current), null);
    assertEquals("unavailable", check("child", "{\"ontologies\":[{\"acronym\":\"EX\",\"version\":\"old\"}]}").status());
  }
  @Test void remoteOutageAndNotFoundAreDifferent() throws Exception {
    boolean[] offline = {true};
    int[] calls = {0};
    ITerminologyService remote = (ITerminologyService) java.lang.reflect.Proxy.newProxyInstance(
        ITerminologyService.class.getClassLoader(), new Class[]{ITerminologyService.class}, (proxy, method, args) -> {
          calls[0]++;
          if (offline[0]) throw new IOException("offline");
          return null;
        });
    verifier = new TermMembershipVerifier(null, remote);
    assertEquals("unavailable", check("child", "{\"ontologies\":[{\"acronym\":\"EX\"}]}").status());
    offline[0] = false;
    assertEquals("failed", check("child", "{\"ontologies\":[{\"acronym\":\"EX\"}]}").status());
    calls[0] = 0;
    assertEquals("unavailable", check("child", "{\"ontologies\":[{\"acronym\":\"EX\",\"version\":\"old\"}]}").status());
    assertEquals(0, calls[0]);
  }

  @Test void missingLocalStoreUsesExactRemoteLookupAndParentsWithoutLabelSearch() throws Exception {
    ITerminologyService remote = (ITerminologyService) java.lang.reflect.Proxy.newProxyInstance(
        ITerminologyService.class.getClassLoader(), new Class[]{ITerminologyService.class}, (proxy, method, args) -> {
          String iri = (String) args[0];
          if (method.getName().equals("findClass")) return iri.endsWith("unknown") ? null : concept(iri);
          if (method.getName().equals("getClassParents")) return iri.endsWith("child") ? List.of(concept(BASE + "root")) : List.of();
          throw new AssertionError("Verification must not use " + method.getName());
        });
    verifier = new TermMembershipVerifier(source -> Optional.empty(), remote);
    String branch = "{\"branches\":[{\"uri\":\"" + BASE + "root\",\"acronym\":\"EX\",\"maxDepth\":1}]}";
    assertEquals("passed", check("child", branch).status());
    assertEquals("failed", check("outside", branch).status());
    assertEquals("failed", check("unknown", branch).status());
  }

  @Test void lookupBudgetExhaustionIsUnavailableRatherThanAFalseSuccess() throws Exception {
    ITerminologyService remote = (ITerminologyService) java.lang.reflect.Proxy.newProxyInstance(
        ITerminologyService.class.getClassLoader(), new Class[]{ITerminologyService.class}, (proxy, method, args) -> {
          var result = concept((String) args[0]);
          result.setOntology((String) args[1]);
          return result;
        });
    verifier = new TermMembershipVerifier(null, remote);
    String sources = java.util.stream.IntStream.range(0, 257).mapToObj(i -> "{\"acronym\":\"EX" + i + "\"}").collect(java.util.stream.Collectors.joining(","));
    assertEquals("unavailable", check("child", "{\"ontologies\":[" + sources + "]}").status());
  }

  @Test void suppressedOrWrappedTimeoutsAreNotProofOfAbsence() throws Exception {
    for (Throwable problem : List.of(new java.net.SocketTimeoutException("unavailable"),
        new java.util.concurrent.CompletionException(new java.net.SocketTimeoutException("unavailable")))) {
      var notFound = new javax.xml.ws.http.HTTPException(404);
      var innerNotFound = new javax.xml.ws.http.HTTPException(404);
      innerNotFound.addSuppressed(problem);
      notFound.addSuppressed(innerNotFound);
      ITerminologyService remote = (ITerminologyService) java.lang.reflect.Proxy.newProxyInstance(
          ITerminologyService.class.getClassLoader(), new Class[]{ITerminologyService.class},
          (proxy, method, args) -> { throw notFound; });
      verifier = new TermMembershipVerifier(null, remote);
      assertEquals("unavailable", check("child", "{\"ontologies\":[{\"acronym\":\"EX\"}]}").status());
    }
  }

  private static org.metadatacenter.terms.domainObjects.OntologyClass concept(String iri) {
    return new org.metadatacenter.terms.domainObjects.OntologyClass("id", iri, "Label ignored", null, "EX",
        null, null, null, null, false, null, false);
  }
}
