package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
public class RelationResource extends AbstractTerminologyServerResource {

  public RelationResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Path("relations")
  //  @ApiOperation(
  //      value = "Create a provisional relation",
  //      httpMethod = "POST")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Successful creation of a provisional relation"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(value = "Relation to be created", required = true, dataType = "org.metadatacenter.terms" +
  //          ".domainObjects.Relation", paramType = "body")})
  public Response createRelation() throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
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
  //  @ApiOperation(
  //      value = "Find provisional relation by id",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Provisional relation id. Example:
  // 720f50f0-ae6f-0133-848f-005056010073",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findRelation(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
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
//  //      @ApiImplicitParam(name = "id", value = "Provisional relation id. Example: 720f50f0-ae6f-0133-848f-005056010073",
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
  //  @ApiOperation(
  //      value = "Delete a provisional relation",
  //      httpMethod = "DELETE")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 204, message = "Success! (No Content)"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Provisional relation id. Example:
  // 720f50f0-ae6f-0133-848f-005056010073",
  //          required = true, dataType = "string", paramType = "path")})
  public Response deleteRelation(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
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