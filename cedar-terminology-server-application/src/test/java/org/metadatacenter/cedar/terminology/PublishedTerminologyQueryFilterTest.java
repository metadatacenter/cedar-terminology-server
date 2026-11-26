package org.metadatacenter.cedar.terminology;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class PublishedTerminologyQueryFilterTest {
  private ContainerRequest request(String method, String path) {
    var request = new ContainerRequest(URI.create("http://localhost/"), URI.create("http://localhost/" + path),
        method, null, new MapPropertiesDelegate());
    new PublishedTerminologyQueryFilter().filter(request);
    return request;
  }

  @Test void releasedNamesReachTheSameLookupWithoutChangingBodiesOrUnrelatedQueries() {
    for (String path : new String[]{"properties", "properties/hierarchy", "search/hierarchy"}) {
      var result = request("GET", path + "?sourceAcronym=TEST&versionId=v1&termIri=urn%3Atest&propertyIri=urn%3Ap&limit=3");
      var query = result.getUriInfo().getQueryParameters();
      assertEquals("TEST", query.getFirst("source_acronym"));
      assertEquals("v1", query.getFirst("version_id"));
      assertEquals("urn:test", query.getFirst("term_iri"));
      assertEquals("urn:p", query.getFirst("property_iri"));
      assertEquals("3", query.getFirst("limit"));
      assertFalse(query.containsKey("sourceAcronym"));
    }
    assertEquals("TEST", request("POST", "properties/search?sourceAcronym=TEST").getUriInfo().getQueryParameters().getFirst("sourceAcronym"));
    assertEquals("TEST", request("GET", "bioportal/search?sourceAcronym=TEST").getUriInfo().getQueryParameters().getFirst("sourceAcronym"));
  }

  @Test void canonicalQueriesPassThroughAndMixedNamesAreRefused() {
    assertNull(request("GET", "properties?source_acronym=TEST").getAbortResponse());
    assertEquals(400, request("GET", "properties?sourceAcronym=OLD&source_acronym=NEW").getAbortResponse().getStatus());
  }
}
