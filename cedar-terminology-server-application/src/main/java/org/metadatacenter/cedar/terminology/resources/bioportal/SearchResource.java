package org.metadatacenter.cedar.terminology.resources.bioportal;

import static org.metadatacenter.constant.CedarQueryParameters.QP_SOURCE_ACRONYM;
import static org.metadatacenter.constant.CedarQueryParameters.QP_TERM_IRI;

import org.metadatacenter.terms.util.HTTPException;
import org.metadatacenter.terms.util.OffsetPaging;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirementEntry;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hibernate.validator.constraints.NotEmpty;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel.PagedSearchResults;
import org.metadatacenter.cedar.util.dw.AnonymousAccess;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;
import static org.metadatacenter.cedar.terminology.util.Constants.*;
import static org.metadatacenter.constant.CedarQueryParameters.QP_EXACT_MATCH;
import static org.metadatacenter.constant.CedarQueryParameters.QP_LIMIT;
import static org.metadatacenter.constant.CedarQueryParameters.QP_MAX_DEPTH;
import static org.metadatacenter.constant.CedarQueryParameters.QP_OFFSET;
import static org.metadatacenter.constant.CedarQueryParameters.QP_Q;
import static org.metadatacenter.constant.CedarQueryParameters.QP_REQUIRE_DEFINITIONS;
import static org.metadatacenter.constant.CedarQueryParameters.QP_SCOPE;
import static org.metadatacenter.constant.CedarQueryParameters.QP_SOURCE;
import static org.metadatacenter.constant.CedarQueryParameters.QP_SOURCES;
import static org.metadatacenter.constant.CedarQueryParameters.QP_SUBTREE_ROOT_ID;
import static org.metadatacenter.constant.CedarQueryParameters.QP_SUGGEST;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Classes")
@SecurityRequirement(name = "api_key")
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
  @Operation(summary = "Search", description = "Search for ontology classes, value sets, and values. <br /> <br />"
      + "A credential is optional. The route serves anonymous callers because the CEDAR Embeddable Designer "
      + "searches it from the browser and holds no CEDAR credential to send.", tags = {"Classes", "Value sets", "Values"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "One page of matching classes, value sets and values" + PAGED_GET_ENVELOPE, content = @Content(schema = @Schema(implementation = PagedSearchResults.class))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  // swagger-core gives every method the class's api_key requirement. An entry with a blank name adds
  // the empty alternative beside it, which is how OpenAPI says that a credential is optional.
  @SecurityRequirement(combine = @SecurityRequirementEntry(name = ""))
  @AnonymousAccess
  public Response search(
      @Parameter(description = "Search query. Example: melanoma.", required = true)
      @QueryParam(QP_Q) @NotEmpty String q,
      @Parameter(description = "Comma-separated list of search scopes. Accepted values={all, classes, value_sets, values}. " +
          "Default: all.")
      @QueryParam(QP_SCOPE) @DefaultValue("all") String scope,
      @Parameter(description = "Comma-separated list of target ontologies and/or value sets. Example: " +
          "'ontologies=CEDARVS,NCIT'. By default, all BioPortal ontologies and value sets are considered. The value " +
          "of 'scope' overrides the list of sources specified using this parameter.")
      @QueryParam(QP_SOURCES) String sources,
      @Parameter(description = "Will perform a search specifically geared towards type-ahead suggestions. Default: false.")
      @QueryParam(QP_SUGGEST) boolean suggest,
      @Parameter(description = "Ontology for which the subtree search will be performed. Example: NCIT.")
      @QueryParam(QP_SOURCE_ACRONYM) String source,
      @Parameter(description = "Class identifier that limits the search to the branch rooted on that class. It must be URL " +
          "encoded. Example: http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224.")
      @QueryParam(QP_TERM_IRI) String subtreeRootId,
      @Parameter(description = "Subtree depth.")
      @QueryParam(QP_MAX_DEPTH) @DefaultValue("1") int maxDepth,
      @Parameter(description = LIMIT_DESCRIPTION)
      @QueryParam(QP_LIMIT) Integer limit,
      @Parameter(description = OFFSET_DESCRIPTION)
      @QueryParam(QP_OFFSET) Integer offset) throws CedarException {

    CedarRequestContext c = buildAnonymousRequestContext();
    if (uriInfo.getQueryParameters().containsKey("source")
        || uriInfo.getQueryParameters().containsKey("subtree_root_id")) {
      return CedarResponse.badRequest().message("Use source_acronym and term_iri for subtree searches").build();
    }

    try {
      OffsetPaging.Request paging = pagingOf(limit, offset);
      // Review and clean scope
      List<String> scopeList = new ArrayList<>();
      List<String> referenceScopeList = Arrays
          .asList(BP_SEARCH_SCOPE_ALL, BP_SEARCH_SCOPE_CLASSES, BP_SEARCH_SCOPE_VALUE_SETS, BP_SEARCH_SCOPE_VALUES);
      for (String s : scope.split("\\s*,\\s*")) {
        if (!referenceScopeList.contains(s)) {
          return CedarResponse.badRequest()
              .errorKey(CedarErrorKey.INVALID_INPUT)
              .message("Wrong scope. Accepted values = {all, classes, value_sets, values}")
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
      // BioPortal does not say whether a result from a value-set collection is a value set or one of
      // its values, so the service is given the identifiers that settle it. It is passed as something
      // to call rather than as the answer: fetching it costs a BioPortal call per value-set
      // collection, and a search whose results are all ontology classes never has to ask.
      List<String> sourceNames = sourcesList;
      PagedResults results = enveloped(OffsetPaging.fetch(paging, (p, s) ->
          terminologyService.search(q, scopeList, sourceNames, suggest, source, subtreeRootId,
          maxDepth, p, s, false, true, apiKey, Cache::getValueSetIds)), paging);
      JsonNode output = JsonMapper.STRICT_MAPPER.valueToTree(results);
      return Response.ok().entity(output).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw bioPortalUnusable(e);
    }
  }

  /**
   * Search for properties
   */
  @GET
  @Timed
  @Path("/property-search")
  @Operation(summary = "Property search", description = "Search for properties.", tags = {"Properties"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "One page of matching properties" + PAGED_GET_ENVELOPE, content = @Content(schema = @Schema(implementation = PagedSearchResults.class))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Unauthorized"),
      @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Forbidden"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  public Response propertySearch(
      @Parameter(description = "Search query. Example: title.", required = true)
      @QueryParam(QP_Q) @NotEmpty String q,
      @Parameter(description = "Comma-separated list of target ontologies. Example: 'ontologies=BIBFRAME'. By default, all " +
          "BioPortal ontologies and value sets are considered.")
      @QueryParam(QP_SOURCES) String sources,
      @Parameter(description = "Restricts results only to the exact matches of the query in the property id, label, or the " +
          "generated label (a label, auto-generated from the id). Default: false.")
      @QueryParam(QP_EXACT_MATCH) boolean exactMatch,
      @Parameter(description = "Filter results only to those that include definitions.")
      @QueryParam(QP_REQUIRE_DEFINITIONS) boolean requireDefinitions,
      @Parameter(description = LIMIT_DESCRIPTION)
      @QueryParam(QP_LIMIT) Integer limit,
      @Parameter(description = OFFSET_DESCRIPTION)
      @QueryParam(QP_OFFSET) Integer offset) throws CedarException {

    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);

    try {
      OffsetPaging.Request paging = pagingOf(limit, offset);
      // Sources list
      List<String> sourcesList = new ArrayList<>();
      if (sources != null && sources.length() > 0) {
        sourcesList = Arrays.asList(sources.split("\\s*,\\s*"));
      }
      List<String> sourceNames = sourcesList;
      PagedResults results = enveloped(OffsetPaging.fetch(paging, (p, s) ->
          terminologyService.propertySearch(q, sourceNames, exactMatch, requireDefinitions,
          p, s, false, true, apiKey)), paging);
      JsonNode output = JsonMapper.STRICT_MAPPER.valueToTree(results);
      return Response.ok().entity(output).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw bioPortalUnusable(e);
    }
  }
}
