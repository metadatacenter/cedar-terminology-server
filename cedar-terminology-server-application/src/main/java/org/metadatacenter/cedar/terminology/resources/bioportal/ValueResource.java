package org.metadatacenter.cedar.terminology.resources.bioportal;

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
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel.PagedValues;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;
import static org.metadatacenter.constant.CedarPathParameters.PP_ID;
import static org.metadatacenter.constant.CedarPathParameters.PP_VS;
import static org.metadatacenter.constant.CedarPathParameters.PP_VS_COLLECTION;
import static org.metadatacenter.constant.CedarQueryParameters.QP_LIMIT;
import static org.metadatacenter.constant.CedarQueryParameters.QP_OFFSET;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Values")
@SecurityRequirement(name = "api_key")
public class ValueResource extends AbstractTerminologyServerResource {

  public ValueResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Timed
  @Path("vs-collections/{vs_collection}/values/{id}")
  @Operation(summary = "Find value by id", description = "Find value by id.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The value", content = @Content(schema = @Schema(implementation = Value.class))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Unauthorized"),
      @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Forbidden"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  public Response findValue(
      @Parameter(description = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam(PP_ID) @Encoded String id,
      @Parameter(description = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam(PP_VS_COLLECTION) String vsCollection) throws
      CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      Value v = terminologyService.findValue(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.STRICT_MAPPER.valueToTree(v)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw bioPortalUnusable(e);
    }
  }

  @GET
  @Timed
  @Path("vs-collections/{vs_collection}/values/{id}/tree")
  @Operation(summary = "Get value tree", description = "Get value tree (only for regular values).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The value and the values around it in its value set", content = @Content(schema = @Schema(implementation = TreeNode.class))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Unauthorized"),
      @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Forbidden"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  public Response findValueTree(
      @Parameter(description = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam(PP_ID) @Encoded String id,
      @Parameter(description = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam(PP_VS_COLLECTION) String vsCollection)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      TreeNode tree = terminologyService.getValueTree(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.STRICT_MAPPER.valueToTree(tree)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw bioPortalUnusable(e);
    }
  }

  @GET
  @Timed
  @Path("vs-collections/{vs_collection}/value-sets/{vs}/values")
  @Operation(summary = "Find all values in a value set", description = "Find all values in a value set (either regular or provisional).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "One page of the value set's values" + PAGED_GET_ENVELOPE, content = @Content(schema = @Schema(implementation = PagedValues.class))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Unauthorized"),
      @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Forbidden"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  public Response findValuesByValueSet(
      @Parameter(description = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam(PP_VS_COLLECTION) String vsCollection,
      @Parameter(description = "Value set identifier. Example: http://www.semanticweb.org/jgraybeal/ontologies/2015/7/" +
          "cedarvaluesets#Study_File_Type", required = true)
      @PathParam(PP_VS) @Encoded String vsId,
      @Parameter(description = LIMIT_DESCRIPTION)
      @QueryParam(QP_LIMIT) Integer limit,
      @Parameter(description = OFFSET_DESCRIPTION)
      @QueryParam(QP_OFFSET) Integer offset) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    OffsetPaging.Request paging = pagingOf(limit, offset);
    try {
      PagedResults<Value> values = enveloped(OffsetPaging.fetch(paging, (p, s) ->
          terminologyService.findValuesByValueSet(vsId, vsCollection, p, s, apiKey)), paging);
      return Response.ok().entity(JsonMapper.STRICT_MAPPER.valueToTree(values)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw bioPortalUnusable(e);
    }
  }

  @GET
  @Timed
  @Path("vs-collections/{vs_collection}/values/{id}/all-values")
  @Operation(summary = "Find all values in the value set that the given value belongs to", description = "Find all values in the value set that the given value belongs to.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "One page of the values in the value set the given value belongs to" + PAGED_GET_ENVELOPE, content = @Content(schema = @Schema(implementation = PagedValues.class))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Unauthorized"),
      @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Forbidden"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  public Response findAllValuesInValueSetByValue(
      @Parameter(description = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam(PP_ID) @Encoded String id,
      @Parameter(description = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam(PP_VS_COLLECTION) String vsCollection,
      @Parameter(description = LIMIT_DESCRIPTION)
      @QueryParam(QP_LIMIT) Integer limit,
      @Parameter(description = OFFSET_DESCRIPTION)
      @QueryParam(QP_OFFSET) Integer offset) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    OffsetPaging.Request paging = pagingOf(limit, offset);
    try {
      PagedResults<Value> values =
          enveloped(OffsetPaging.fetch(paging, (p, s) ->
          terminologyService.findAllValuesInValueSetByValue(id, vsCollection, p, s, apiKey)), paging);
      return Response.ok().entity(JsonMapper.STRICT_MAPPER.valueToTree(values)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw bioPortalUnusable(e);
    }
  }


}
