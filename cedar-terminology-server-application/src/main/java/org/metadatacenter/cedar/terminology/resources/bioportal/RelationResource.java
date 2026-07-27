package org.metadatacenter.cedar.terminology.resources.bioportal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.domainObjects.Relation;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URI;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Relations")
@SecurityRequirement(name = "api_key")
public class RelationResource extends AbstractTerminologyServerResource {

  public RelationResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Path("relations")
  @Operation(summary = "Create a provisional relation", description = "Create a provisional relation.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Successful operation (no content)"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response createRelation() throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      Relation r = JsonMapper.MAPPER.convertValue(ctx.request().getRequestBody().asJson(), Relation.class);
      Relation createdRelation = terminologyService.createProvisionalRelation(r, apiKey);
      return Response.created(new URI(createdRelation.getLdId())).entity(JsonMapper.MAPPER.valueToTree
          (createdRelation)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }

  @GET
  @Path("relations/{id}")
  @Operation(summary = "Find provisional relation by id", description = "Find provisional relation by id.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findRelation(
      @Parameter(description = "Provisional relation short identifier. Examples: 720f50f0-ae6f-0133-848f-005056010073.", required = true)
      @PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      Relation r = terminologyService.findProvisionalRelation(id, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(r)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  // TODO: when needed...
//  @PUT
//  @Path("relations/{id}")
//  //  @ApiOperation(
//  //      value = "Update a provisional relation",
//  //      httpMethod = "PATCH")
//  //  @ApiResponses(value = {
//  //      @ApiResponse(code = 204, message = "Success! (No Content)"),
//  //      @ApiResponse(code = 400, message = "Bad Request"),
//  //      @ApiResponse(code = 401, message = "Unauthorized"),
//  //      @ApiResponse(code = 404, message = "Not Found"),
//  //      @ApiResponse(code = 500, message = "Internal Server Error")})
//  //  @ApiImplicitParams(value = {
//  //      @ApiImplicitParam(name = "Authorization", value = "Format: apikey={your_bioportal_apikey}. "
//  //          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
//  //          required = true, dataType = "string", paramType = "header"),
//  //      @ApiImplicitParam(name = "id", value = "Provisional relation id. Example:
// 720f50f0-ae6f-0133-848f-005056010073",
//  //          required = true, dataType = "string", paramType = "path"),
//  //      @ApiImplicitParam(value = "Updated information for the relation", required = true, dataType = "org
//  // .metadatacenter.terms" +
//  //          ".domainObjects.Relation", paramType = "body")})
//  public Response updateRelation(@PathParam("id") String id) throws CedarException {
//    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
//    ctx.must(ctx.user()).be(LoggedIn);
//    try {
//      Relation r = JsonMapper.MAPPER.readValue(request.getInputStream(), Relation.class);
//      terminologyService.updateProvisionalRelation(r, apiKey);
//      return Response.noContent().build();
//    } catch (HTTPException e) {
//      return Response.status(e.getStatusCode()).build();
//    } catch (IOException e) {
//      throw new CedarAssertionException(e);
//    }
//  }

  @DELETE
  @Path("relations/{id}")
  @Operation(summary = "Delete provisional relation", description = "Delete provisional relation by id.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response deleteRelation(
      @Parameter(description = "Provisional relation short identifier. Examples: 720f50f0-ae6f-0133-848f-005056010073.", required = true)
      @PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      terminologyService.deleteProvisionalRelation(id, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

}