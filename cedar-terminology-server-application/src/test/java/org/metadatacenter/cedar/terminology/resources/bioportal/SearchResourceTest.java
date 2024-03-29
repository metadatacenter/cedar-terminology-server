package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

/**
 * Integration tests. They are done by starting a test server that makes it possible to test the real HTTP stack.
 */

public class SearchResourceTest extends AbstractTerminologyServerResourceTest {

  @Test
  public void searchAllTest() {
    // Query parameters
    String q = "white blood cell";
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpSearch).queryParam("q", q).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    int pageCount = jsonResponse.get("pageCount").asInt();
    int lowLimitPageCount = 2000;
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", pageCount >
        lowLimitPageCount);
  }

  @Test
  public void searchClassesTest() {
    // Query parameters
    String q = "white blood cell";
    String scope = "classes";
    // Service invocation
    Response response =
        clientBuilder.build().target(baseUrlBpSearch).queryParam("q", q).queryParam("scope", scope).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    int pageCount = jsonResponse.get("pageCount").asInt();
    int lowLimitPageCount = 2000;
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", pageCount >
        lowLimitPageCount);
  }

  /**
   * The following test will only work if the BioPortal API flag 'also_search_properties' has been set to true
   **/
  @Test
  public void searchClassByPropertyValue() {
    // Query parameters
    String q = "audiodisc";
    String source = "BIBLIOTEK-O";
    String scope = "classes";
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .queryParam("sources", source)
        .request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    int pageCount = jsonResponse.get("pageCount").asInt();
    int lowLimitPageCount = 1;
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected",
        pageCount >= lowLimitPageCount);
  }

  @Test
  public void searchClassesAndValuesTest() {
    // Query parameters
    String q = "white blood cell";
    String scope = "classes,values";
    // Service invocation
    Response response =
        clientBuilder.build().target(baseUrlBpSearch).queryParam("q", q).queryParam("scope", scope).request()
        .header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    int pageCount = jsonResponse.get("pageCount").asInt();
    int lowLimitPageCount = 2000;
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", pageCount >
        lowLimitPageCount);
  }

  @Test
  public void searchClassesBySourceTest() {
    // Query parameters
    String q = "cell";
    String scope = "classes";
    String source = "OBI";
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .queryParam("sources", source)
        .request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that there are some results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    JsonNode results = jsonResponse.get("collection");
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", results.size() > 1);
    // Check that the retrieved classes are from the right source
    for (JsonNode r : results) {
      String resultSource = r.get("source").asText();
      String shortResultSource = resultSource.substring(resultSource.lastIndexOf("/") + 1);
      Assert.assertTrue("Class source does not match the expected source",
          source.compareTo(shortResultSource) == 0);
    }
  }

  @Test
  public void searchClassesBySourceCheckMatchTypePrefLabelTest() {
    // Query parameters
    String q = "Autism";
    String scope = "classes";
    String source = "NCIT";
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .queryParam("sources", source)
        .request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that there are some results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    JsonNode results = jsonResponse.get("collection");
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", results.size() > 1);
    // Check synonyms information
    JsonNode firstResult = results.get(0);
    String matchType = firstResult.get("matchType").asText();
    Assert.assertTrue("Returned 'matchType' does not match the expected value", matchType.equals("prefLabel"));
  }

  @Test
  public void searchClassesBySourceCheckMatchTypeSynonymTest() throws IOException {
    // Query parameters
    String q = "Autistic Disorder"; // 'Autistic Disorder' is a synonym of 'autism' in NCIT
    String scope = "classes";
    String source = "NCIT";
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .queryParam("sources", source)
        .request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that there are some results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    JsonNode results = jsonResponse.get("collection");
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", results.size() > 1);
    // Check synonyms information
    JsonNode firstResult = results.get(0);
    String matchType = firstResult.get("matchType").asText();
    Assert.assertTrue("Returned 'matchType' does not match the expected value", matchType.equals("synonym"));

    List<String> matchedSynonyms = mapper.readValue(firstResult.get("matchedSynonyms").traverse(),
        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
    Assert.assertTrue("Returned 'matchedSynonyms' does not match the expected value",
        matchedSynonyms.size() == 1);
    Assert.assertTrue("Returned 'matchedSynonyms' does not match the expected value",
        matchedSynonyms.get(0).equals(q));
  }

  @Test
  public void searchClassesByWrongSourceTest() {
    // Query parameters
    String q = "cell";
    String scope = "classes";
    String source = "WRONG-SOURCE";
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .queryParam("sources", source)
        .request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  public void searchValueSetTest() {
    // Query parameters
    String q = "Amblyopia";
    String scope = "value_sets";
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that there are some results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    JsonNode results = jsonResponse.get("collection");
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", results.size() > 0);
  }

  @Test
  public void searchValuesTest() {
    // Query parameters
    String q = "inclusion";
    String scope = "values";
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpSearch)
        .queryParam("q", q)
        .queryParam("scope", scope)
        .request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that there are some results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    JsonNode results = jsonResponse.get("collection");
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", results.size() > 0);
  }

  @Test
  public void searchPropertiesTest() {
    // Query parameters
    String q = "has title";
    // Service invocation
    Response response =
        clientBuilder.build().target(baseUrlBpPropertySearch).queryParam("q", q).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    int pageCount = jsonResponse.get("pageCount").asInt();
    int lowLimitPageCount = 100;
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected", pageCount >
        lowLimitPageCount);
  }

  @Test
  public void searchWrongPropertyTest() {
    // Query parameters
    String q = "wrongproperty333";
    // Service invocation
    Response response =
        clientBuilder.build().target(baseUrlBpPropertySearch).queryParam("q", q).request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check the number of results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    int pageSize = jsonResponse.get("pageSize").asInt();
    int expectedCount = 0;
    Assert.assertTrue("The number of search results for '" + q + "' is different than expected",
        pageSize == expectedCount);
  }

  @Test
  public void searchPropertiesBySourceTest() {
    // Query parameters
    String q = "main title";
    String source = "BIBFRAME";
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpPropertySearch)
        .queryParam("q", q)
        .queryParam("sources", source)
        .request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that there are some results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    JsonNode results = jsonResponse.get("collection");
    // The ontology may change over time, so we define a range for the number of properties found
    int lowerLimitResultsCount = 4;
    int upperLimitResultsCount = 20;
    int pageSize = jsonResponse.get("pageSize").asInt();
    int pageCount = jsonResponse.get("pageCount").asInt();
    int approxResultsCount = pageSize * pageCount;
    Assert.assertTrue("The number of search results for '" + q + "' is lower than expected",
        approxResultsCount > lowerLimitResultsCount);
    Assert.assertTrue("The number of search results for '" + q + "' is higher than expected",
        approxResultsCount < upperLimitResultsCount);
    // Check that the retrieved classes are from the right source
    for (JsonNode r : results) {
      String resultSource = r.get("source").asText();
      String shortResultSource = resultSource.substring(resultSource.lastIndexOf("/") + 1);
      Assert.assertTrue("Class source does not match the expected source",
          source.compareTo(shortResultSource) == 0);
    }
  }

  @Test
  public void searchPropertiesBySourceExactMatchTest() {
    // Query parameters
    String q = "main title";
    String source = "BIBFRAME";
    boolean exactMatch = true;
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpPropertySearch)
        .queryParam("q", q)
        .queryParam("sources", source)
        .queryParam("exact_match", exactMatch)
        .request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    // Check that there are some results
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    JsonNode results = jsonResponse.get("collection");
    // Enabling exact_match should only return one property
    Assert.assertTrue("The number of search results for '" + q + "' is different than expected", results.size() == 1);
    // Check that the retrieved classes are from the right source
    for (JsonNode r : results) {
      String resultSource = r.get("source").asText();
      String shortResultSource = resultSource.substring(resultSource.lastIndexOf("/") + 1);
      Assert.assertTrue("Class source does not match the expected source",
          source.compareTo(shortResultSource) == 0);
    }
  }

  @Test
  public void searchPropertiesByWrongSourceTest() {
    // Query parameters
    String q = "main title";
    String source = "WRONG-SOURCE";
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpPropertySearch)
        .queryParam("q", q)
        .queryParam("sources", source)
        .request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.NOT_FOUND.getStatusCode(), response.getStatus());
  }

  @Test
  public void searchPropertiesRequireDefinitionsTest() {
    // Query parameters
    String q = "main title";
    boolean requireDefinitions = true;
    // Service invocation
    Response response = clientBuilder.build().target(baseUrlBpPropertySearch)
        .queryParam("q", q)
        .queryParam("require_definitions", requireDefinitions)
        .request().header(HTTP_HEADER_AUTHORIZATION, authHeader).get();
    // Check HTTP response
    Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());
    // Check Content-Type
    Assert.assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    JsonNode jsonResponse = response.readEntity(JsonNode.class);
    response.close();
    JsonNode results = jsonResponse.get("collection");
    // Check that there are some results
    Assert.assertTrue("No search results obtained for '" + q + "'", results.size() > 0);
    // Check that all properties found contain at least one definition
    // TODO: We are just checking the first page of results. Check all of them.
    for (JsonNode r : results) {
      Assert.assertTrue("A property with no definitions has been returned", r.get("definition") != null);
      Assert.assertTrue("A property with no definitions has been returned", r.get("definition").asText().length() > 0);
    }
  }

}
