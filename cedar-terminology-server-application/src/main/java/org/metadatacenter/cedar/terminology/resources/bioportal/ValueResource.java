package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Values")
@SecurityRequirement(name = "api_key")
public class ValueResource extends AbstractTerminologyServerResource {

  public ValueResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Path("vs-collections/{vs_collection}/values/{id}")
  @Operation(summary = "Find value by id", description = "Find value by id.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Unauthorized"),
      @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Forbidden"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  public Response findValue(
      @Parameter(description = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection) throws
      CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      Value v = terminologyService.findValue(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(v)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/values/{id}/tree")
  @Operation(summary = "Get value tree", description = "Get value tree (only for regular values).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Unauthorized"),
      @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Forbidden"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  public Response findValueTree(
      @Parameter(description = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      TreeNode tree = terminologyService.getValueTree(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(tree)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/value-sets/{vs}/values")
  @Operation(summary = "Find all values in a value set", description = "Find all values in a value set (either regular or provisional).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Unauthorized"),
      @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Forbidden"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  public Response findValuesByValueSet(
      @Parameter(description = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection,
      @Parameter(description = "Value set identifier. Example: http://www.semanticweb.org/jgraybeal/ontologies/2015/7/" +
          "cedarvaluesets#Study_File_Type", required = true)
      @PathParam("vs") @Encoded String vsId,
      @Parameter(description = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @Parameter(description = "Number of results per page. Example: 10.")
      @QueryParam("pageSize") int pageSize,
      @Parameter(description = "Alias for the page size, accepted in either spelling.")
      @QueryParam("page_size") int pageSizeAlias) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    pageSize = resolvePageSize(pageSize, pageSizeAlias);
    try {
      PagedResults<Value> values = terminologyService.findValuesByValueSet(vsId, vsCollection, page, pageSize, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(values)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/values/{id}/all-values")
  @Operation(summary = "Find all values in the value set that the given value belongs to", description = "Find all values in the value set that the given value belongs to.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Unauthorized"),
      @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Forbidden"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  public Response findAllValuesInValueSetByValue(
      @Parameter(description = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection,
      @Parameter(description = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @Parameter(description = "Number of results per page. Example: 10.")
      @QueryParam("pageSize") int pageSize,
      @Parameter(description = "Alias for the page size, accepted in either spelling.")
      @QueryParam("page_size") int pageSizeAlias) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    pageSize = resolvePageSize(pageSize, pageSizeAlias);
    try {
      PagedResults<Value> values =
          terminologyService.findAllValuesInValueSetByValue(id, vsCollection, page, pageSize, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(values)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }


}
