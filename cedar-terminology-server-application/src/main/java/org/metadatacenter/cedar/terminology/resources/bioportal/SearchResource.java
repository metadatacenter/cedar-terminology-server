package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
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
import static org.metadatacenter.terms.util.Constants.*;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
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
  public Response search(@QueryParam("q") @NotEmpty String q,
                         @QueryParam("scope") @DefaultValue("all") String scope,
                         @QueryParam("sources") String sources,
                         @QueryParam("suggest") boolean suggest,
                         @QueryParam("source") String source,
                         @QueryParam("subtree_root_id") String subtreeRootId,
                         @QueryParam("max_depth") @DefaultValue("1") int maxDepth,
                         @QueryParam("page") @DefaultValue("1") int page,
                         @QueryParam("page_size") int pageSize) throws CedarException {

    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);

    try {
      // If pageSize not defined, set default value
      if (pageSize == 0) {
        pageSize = defaultPageSize;
      }
      // Review and clean scope
      List<String> scopeList = new ArrayList<>();
      List<String> referenceScopeList = Arrays
          .asList(BP_SEARCH_SCOPE_ALL, BP_SEARCH_SCOPE_CLASSES, BP_SEARCH_SCOPE_VALUE_SETS, BP_SEARCH_SCOPE_VALUES);
      for (String s : Arrays.asList(scope.split("\\s*,\\s*"))) {
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
      if (sources != null && sources.length() > 0) {
        sourcesList = Arrays.asList(sources.split("\\s*,\\s*"));
      }
      List<String> valueSetsIds = new ArrayList<>(Cache.valueSetsCache.get("value-sets").keySet());
      // TODO: The valueSetsIds parameter is passed to the service to avoid making additional calls to BioPortal.
      // These ids are used to know if a particular result returned by BioPortal is a value or a value set.
      // BioPortal should provide this information and this parameter should be removed
      PagedResults results = terminologyService.search(q, scopeList, sourcesList, suggest, source, subtreeRootId,
          maxDepth,
          page, pageSize, false, true, apiKey, valueSetsIds);
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
  public Response propertySearch(@QueryParam("q") @NotEmpty String q,
                                 @QueryParam("sources") String sources,
                                 @QueryParam("exact_match") boolean exactMatch,
                                 @QueryParam("require_definitions") boolean requireDefinitions,
                                 @QueryParam("page") @DefaultValue("1") int page,
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