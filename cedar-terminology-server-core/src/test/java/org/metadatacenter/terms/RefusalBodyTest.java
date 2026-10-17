package org.metadatacenter.terms;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.terms.bioportal.IBioPortalService;
import org.metadatacenter.terms.util.Util;
import org.metadatacenter.util.http.CedarError;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A refusal of an integrated search keeps its message in the shared error body.
 *
 * <p>Each was a BadRequestException without an entity, which reached the client as a 400 whose body
 * carried no message, so a client could not tell which part of its constraint was refused.
 */
class RefusalBodyTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String CLASS = "{\"uri\":\"http://example.org/ONTA/1\",\"prefLabel\":\"alpha\","
      + "\"type\":\"OntologyClass\",\"source\":\"ONTA\"}";

  /**
   * A move whose term is not among the results is looked up by its type, and an unknown type is
   * refused. The constraint enumerates its one class, so nothing is asked of BioPortal first.
   */
  @Test
  void aMovedTermOfAnUnknownTypeIsRefusedWithItsType() throws Exception {
    IBioPortalService unreachable = (IBioPortalService) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[]{IBioPortalService.class}, (proxy, method, args) -> {
          throw new AssertionError("BioPortal was asked " + method.getName());
        });
    ValueConstraints constraints = constraints("{\"classes\":[" + CLASS + "],\"actions\":[{\"to\":0,"
        + "\"action\":\"move\",\"termUri\":\"http://example.org/ONTA/2\",\"type\":\"Foo\",\"source\":\"ONTA\"}]}");

    assertRefused("Invalid type: Foo", () -> new TerminologyService(unreachable)
        .integratedSearch(Optional.empty(), constraints, 1, 10, "key", null));
  }

  @Test
  void anUnknownActionIsRefusedWithTheAction() throws Exception {
    ValueConstraints constraints = constraints("{\"classes\":[" + CLASS + "],\"actions\":[{\"action\":\"rename\","
        + "\"termUri\":\"http://example.org/ONTA/1\",\"type\":\"OntologyClass\",\"source\":\"ONTA\"}]}");

    assertRefused("Invalid action: rename", () -> ResultActions.apply(List.of(), constraints, action -> null));
  }

  @Test
  void aMalformedOntologyUriIsRefusedWithTheUri() {
    assertRefused("Malformed ontology URI: not-an-ontology",
        () -> Util.getOntologyAcronymFromOntologyUri("not-an-ontology"));
  }

  private static void assertRefused(String message, Executable call) {
    BadRequestException refusal = assertThrows(BadRequestException.class, call);
    CedarError body = assertInstanceOf(CedarError.class, refusal.getResponse().getEntity());
    assertEquals(400, body.statusCode);
    assertEquals(message, body.message);
  }

  private static ValueConstraints constraints(String json) throws Exception {
    String full = "{\"ontologies\":[],\"branches\":[],\"valueSets\":[],\"classes\":[],\"actions\":[],"
        + json.substring(1);
    return MAPPER.readValue(full, ValueConstraints.class);
  }
}
