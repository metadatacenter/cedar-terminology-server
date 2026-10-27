package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.terms.ITerminologyService;
import org.metadatacenter.terms.TermVerification;
import org.metadatacenter.util.json.JsonMapper;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What an exact-term verification refuses before any terminology is consulted. A malformed request is
 * the caller's to fix and answers 400; only a well-formed one reaches the service, whose answer is
 * relayed as it is, and a server with no service says so rather than claiming a verdict.
 */
class TermVerificationRequestTest {
  private static final String TERM = "http://purl.obolibrary.org/obo/DOID_4";
  private static TermVerificationResource resource;
  private static String apiKey;
  private ITerminologyService original;
  private final AtomicReference<String> verifiedIri = new AtomicReference<>();

  @BeforeAll static void build() {
    CedarConfig config = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_TERMINOLOGY));
    resource = new TermVerificationResource(config);
    apiKey = config.getTerminologyConfig().getBioPortal().getApiKey();
  }

  @BeforeEach void injectService() {
    original = AbstractTerminologyServerResource.terminologyService;
    AbstractTerminologyServerResource.injectTerminologyService((ITerminologyService) Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class<?>[]{ITerminologyService.class}, (proxy, method, args) -> {
          if (!method.getName().equals("verifyTerm")) throw new UnsupportedOperationException(method.getName());
          verifiedIri.set((String) args[0]);
          assertEquals(apiKey, args[2]);
          return new TermVerification("passed", "controlledTermVerified", "Found.", (String) args[0]);
        }));
  }

  @AfterEach void restoreService() {
    AbstractTerminologyServerResource.injectTerminologyService(original);
  }

  private static JsonNode json(String text) throws Exception {
    return JsonMapper.STRICT_MAPPER.readTree(text.replace('\'', '"'));
  }

  private static JsonNode request(String constraints) throws Exception {
    return json("{'iri': '" + TERM + "', 'valueConstraints': " + constraints + "}");
  }

  private static void assertRefused(JsonNode body) {
    Response response = resource.verify(body);
    assertEquals(400, response.getStatus(), String.valueOf(body));
  }

  @Test void aBodyThatIsNotExactlyAnIriAndItsConstraintsIsRefused() throws Exception {
    assertRefused(null);
    assertRefused(json("[]"));
    assertRefused(json("{'iri': '" + TERM + "'}"));
    assertRefused(json("{'iri': '" + TERM + "', 'valueConstraints': {}, 'label': 'x'}"));
    assertRefused(json("{'term': '" + TERM + "', 'valueConstraints': {}}"));
    assertRefused(json("{'iri': 4, 'valueConstraints': {}}"));
    assertRefused(json("{'iri': '" + TERM + "', 'valueConstraints': []}"));
  }

  @Test void theTermMustBeAnAbsoluteIri() throws Exception {
    assertRefused(json("{'iri': 'DOID_4', 'valueConstraints': {}}"));
    assertRefused(json("{'iri': 'http://example.org/a b', 'valueConstraints': {}}"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ontologies", "branches", "classes", "valueSets"})
  void eachConstraintListMustBeAListOfObjects(String list) throws Exception {
    assertRefused(request("{'" + list + "': 'DOID'}"));
    assertRefused(request("{'" + list + "': ['DOID']}"));
    StringBuilder many = new StringBuilder("{'" + list + "': [");
    for (int i = 0; i <= 1000; i++) many.append(i == 0 ? "" : ",").append("{}");
    assertRefused(request(many.append("]}").toString()));
  }

  @Test void aConstraintsIdentifiersMustBeAbsoluteIris() throws Exception {
    assertRefused(request("{'ontologies': [{'acronym': 'DOID', 'uri': 'DOID'}]}"));
    assertRefused(request("{'ontologies': [{'acronym': 'DOID', 'iri': 7}]}"));
  }

  @Test void aClassConstraintNamesAClassOrAValue() throws Exception {
    String term = "'uri': '" + TERM + "', 'prefLabel': 'disease', 'source': 'DOID'";
    assertRefused(request("{'classes': [{" + term + "}]}"));
    assertRefused(request("{'classes': [{" + term + ", 'type': 'Individual'}]}"));
    assertEquals(200, resource.verify(request("{'classes': [{" + term + ", 'type': 'OntologyClass'}]}")).getStatus());
  }

  @Test void actionsMustBeWellFormed() throws Exception {
    String action = "'termUri': '" + TERM + "', 'type': 'OntologyClass', 'source': 'DOID'";
    assertRefused(request("{'actions': {}}"));
    assertRefused(request("{'actions': ['delete']}"));
    assertRefused(request("{'actions': [{'termUri': 'DOID_4', 'type': 'OntologyClass', 'action': 'delete'}]}"));
    assertRefused(request("{'actions': [{" + action.replace("OntologyClass", "Individual") + ", 'action': 'delete'}]}"));
    for (String to : new String[]{"", ", 'to': -1", ", 'to': '2'", ", 'to': 2.5"})
      assertRefused(request("{'actions': [{" + action + ", 'action': 'move'" + to + "}]}"));
    assertEquals(200, resource.verify(request("{'actions': [{" + action + ", 'action': 'move', 'to': 2}]}")).getStatus());
    StringBuilder many = new StringBuilder("{'actions': [");
    for (int i = 0; i <= 1000; i++) many.append(i == 0 ? "" : ",").append("{" + action + ", 'action': 'delete'}");
    assertRefused(request(many.append("]}").toString()));
  }

  @Test void aBranchDepthIsANonnegativeInteger() throws Exception {
    String branch = "'uri': '" + TERM + "', 'acronym': 'DOID'";
    for (String depth : new String[]{"-1", "'2'", "2.5"})
      assertRefused(request("{'branches': [{" + branch + ", 'maxDepth': " + depth + "}]}"));
    assertEquals(200, resource.verify(request("{'branches': [{" + branch + ", 'maxDepth': 3}]}")).getStatus());
  }

  @Test void constraintsMissingWhatTheyNeedOrCarryingUnknownKeysAreRefused() throws Exception {
    assertRefused(request("{'ontologies': [{'name': 'Disease Ontology'}]}"));
    assertRefused(request("{'ontologies': [{'acronym': 'DOID', 'colour': 'red'}]}"));
  }

  @Test void aWellFormedRequestIsRelayedWithTheTermSpelledAsGiven() throws Exception {
    String spelled = "http://purl.obolibrary.org/obo/DOID_%34";
    Response response = resource.verify(json("{'iri': '" + spelled + "', 'valueConstraints': {'ontologies': [{'acronym': 'DOID'}]}}"));
    assertEquals(200, response.getStatus());
    assertEquals(spelled, verifiedIri.get());
    assertEquals("passed", ((TermVerification) response.getEntity()).status());
  }

  @Test void aServerWithoutATerminologyServiceSaysVerificationIsUnavailable() throws Exception {
    AbstractTerminologyServerResource.injectTerminologyService(null);
    Response response = resource.verify(request("{'ontologies': [{'acronym': 'DOID'}]}"));
    assertEquals(200, response.getStatus());
    assertEquals("unavailable", ((TermVerification) response.getEntity()).status());
  }
}
