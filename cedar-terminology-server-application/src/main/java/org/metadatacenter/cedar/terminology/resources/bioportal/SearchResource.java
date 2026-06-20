package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.hibernate.validator.constraints.NotEmpty;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;
import static org.metadatacenter.cedar.terminology.util.Constants.*;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/bioportal", tags = "Classes", authorizations = {@Authorization("api_key")})
public class SearchResource extends AbstractTerminologyServerResource {

  public SearchResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  /**
   * Search for classes, value sets and values
   */
  @GET
  @Timed
  @Path("/search")
  @ApiOperation(value = "Search", notes = "Search for ontology classes, value sets, and values.",
      tags = {"Classes", "Value sets", "Values"})
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response search(
      @ApiParam(value = "Search query. Example: melanoma.", required = true)
      @QueryParam("q") @NotEmpty String q,
      @ApiParam(value = "Comma-separated list of search scopes. Accepted values={all, classes, value_sets, values}. " +
          "Default: all.")
      @QueryParam("scope") @DefaultValue("all") String scope,
      @ApiParam(value = "Comma-separated list of target ontologies and/or value sets. Example: " +
          "'ontologies=CEDARVS,NCIT'. By default, all BioPortal ontologies and value sets are considered. The value " +
          "of 'scope' overrides the list of sources specified using this parameter.")
      @QueryParam("sources") String sources,
      @ApiParam(value = "Will perform a search specifically geared towards type-ahead suggestions. Default: false.")
      @QueryParam("suggest") boolean suggest,
      @ApiParam(value = "Ontology for which the subtree search will be performed. Example: NCIT.")
      @QueryParam("source") String source,
      @ApiParam(value = "Class identifier that limits the search to the branch rooted on that class. It must be URL " +
          "encoded. Example: http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224.")
      @QueryParam("subtree_root_id") String subtreeRootId,
      @ApiParam(value = "Subtree depth.")
      @QueryParam("max_depth") @DefaultValue("1") int maxDepth,
      @ApiParam(value = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @ApiParam(value = "Number of results per page. Example: 10.")
      @QueryParam("page_size") int pageSize) throws CedarException {

    CedarRequestContext c = buildAnonymousRequestContext();

    try {
      // If pageSize not defined, set default value
      if (pageSize == 0) {
        pageSize = defaultPageSize;
      }
      // Review and clean scope
      List<String> scopeList = new ArrayList<>();
      List<String> referenceScopeList = Arrays
          .asList(BP_SEARCH_SCOPE_ALL, BP_SEARCH_SCOPE_CLASSES, BP_SEARCH_SCOPE_VALUE_SETS, BP_SEARCH_SCOPE_VALUES);
      for (String s : scope.split("\\s*,\\s*")) {
        if (!referenceScopeList.contains(s)) {
          return CedarResponse.badRequest()
              .errorKey(CedarErrorKey.INVALID_INPUT)
              .errorMessage("Wrong scope. Accepted values = {all, classes, value_sets, values}")
              .build();
        } else {
          scopeList.add(s);
        }
      }
      // Sources list
      List<String> sourcesList = new ArrayList<>();
      if (sources != null && !sources.isEmpty()) {
        sourcesList = Arrays.asList(sources.split("\\s*,\\s*"));
      }
      List<String> valueSetsIds = new ArrayList<>(Cache.valueSetsCache.get("value-sets").keySet());
      // TODO: The valueSetsIds parameter is passed to the service to avoid making additional calls to BioPortal.
      // These ids are used to know if a particular result returned by BioPortal is a value or a value set.
      // BioPortal should provide this information and this parameter should be removed
      PagedResults results = terminologyService.search(q, scopeList, sourcesList, suggest, source, subtreeRootId,
          maxDepth, page, pageSize, false, true, apiKey, valueSetsIds);
      JsonNode output = JsonMapper.MAPPER.valueToTree(results);
      return Response.ok().entity(output).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException | ExecutionException e) {
      throw new CedarAssertionException(e);
    }
  }

  /**
   * Search for properties
   */
  @GET
  @Timed
  @Path("/property_search")
  @ApiOperation(value = "Property search", notes = "Search for properties.", tags = {"Properties"})
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response propertySearch(
      @ApiParam(value = "Search query. Example: title.", required = true)
      @QueryParam("q") @NotEmpty String q,
      @ApiParam(value = "Comma-separated list of target ontologies. Example: 'ontologies=BIBFRAME'. By default, all " +
          "BioPortal ontologies and value sets are considered.")
      @QueryParam("sources") String sources,
      @ApiParam(value = "Restricts results only to the exact matches of the query in the property id, label, or the " +
          "generated label (a label, auto-generated from the id). Default: false.")
      @QueryParam("exact_match") boolean exactMatch,
      @ApiParam(value = "Filter results only to those that include definitions.")
      @QueryParam("require_definitions") boolean requireDefinitions,
      @ApiParam(value = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @ApiParam(value = "Number of results per page. Example: 10.")
      @QueryParam("page_size") int pageSize) throws CedarException {

    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);

    try {
      // If pageSize not defined, set default value
      if (pageSize == 0) {
        pageSize = defaultPageSize;
      }
      // Sources list
      List<String> sourcesList = new ArrayList<>();
      if (sources != null && sources.length() > 0) {
        sourcesList = Arrays.asList(sources.split("\\s*,\\s*"));
      }
      PagedResults results = terminologyService.propertySearch(q, sourcesList, exactMatch, requireDefinitions,
          page, pageSize, false, true, apiKey);
      JsonNode output = JsonMapper.MAPPER.valueToTree(results);
      return Response.ok().entity(output).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }
}
