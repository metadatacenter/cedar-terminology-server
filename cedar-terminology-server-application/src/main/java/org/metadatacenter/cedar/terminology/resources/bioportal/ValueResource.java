package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
public class ValueResource extends AbstractTerminologyServerResource {

  public ValueResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Path("vs-collections/{vs_collection}/value-sets/{vs}/values")
  //  @ApiOperation(
  //      value = "Create a value",
  //      httpMethod = "POST")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Successful creation of a provisional value"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
  //          required = true, dataType = "string", paramType = "path"),
  //      @ApiImplicitParam(name = "vs", value = "Value set identifier. Example: http%3A%2F%2Fwww.semanticweb" +
  //          ".org%2Fjgraybeal%2Fontologies%2F2015%2F7%2Fcedarvaluesets%23Study_File_Type",
  //          required = true, dataType = "string", paramType = "path"),
  //      @ApiImplicitParam(value = "Value to be created", required = true, dataType = "org.metadatacenter.terms" +
  //          ".domainObjects.Value", paramType = "body")})
  public Response createValue(@PathParam("vs_collection") String vsCollection, @PathParam("vs") String vs) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      Value v = JsonMapper.MAPPER.convertValue(ctx.request().getRequestBody().asJson(), Value.class);
      v.setVsCollection(vsCollection);
      v.setVsId(vs);
      Value createdValue = terminologyService.createProvisionalValue(v, apiKey);
      JsonNode createdValueJson = JsonMapper.MAPPER.valueToTree(createdValue);
      return Response.created(new URI(createdValue.getLdId())).entity(createdValueJson).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (URISyntaxException | IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/values/{id}")
  //  @ApiOperation(
  //      value = "Find value by id",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
  //          required = true, dataType = "string", paramType = "path"),
  //      @ApiImplicitParam(name = "id", value = "Value id. Example: 42f22880-b04b-0133-848f-005056010073",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findValue(@PathParam("id") @Encoded String id, @PathParam("vs_collection") String vsCollection) throws
      CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      Value v = terminologyService.findValue(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(v)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/values/{id}/tree")
  //  @ApiOperation(
  //      value = "Get value tree (only for regular classes)",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Value id. It must be encoded",
  //          required = true, dataType = "string", paramType = "path"),
  //      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findValueTree(@PathParam("id") @Encoded String id, @PathParam("vs_collection") String vsCollection)
      throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      TreeNode tree = terminologyService.getValueTree(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(tree)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }
  
//
//  @ApiOperation(
//      value = "Find all values in the value set that the given value belongs to",
//      httpMethod = "GET")
//  @ApiResponses(value = {
//      @ApiResponse(code = 200, message = "Success!"),
//      @ApiResponse(code = 400, message = "Bad Request"),
//      @ApiResponse(code = 401, message = "Unauthorized"),
//      @ApiResponse(code = 500, message = "Internal Server Error")})
//  @ApiImplicitParams(value = {
//      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(name = "vs", value = "Value set identifier. Example: http%3A%2F%2Fwww.semanticweb" +
//          ".org%2Fjgraybeal%2Fontologies%2F2015%2F7%2Fcedarvaluesets%23Study_File_Type",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(name = "id", value = "Value id. Example: 42f22880-b04b-0133-848f-005056010073",
//          required = true, dataType = "string", paramType = "path")})
//  public static Result findAllValuesInValueSetByValue(String id, String vsCollection, @ApiParam(value = "Integer " +
//      "representing the page number. Default: 'page=1'", required = false) @QueryParam("page") int page, @ApiParam(value = "Integer " +
//      "representing the size of the returned page. Default: 'pageSize=50'", required = false) @QueryParam
//      ("page_size") int pageSize) {
//    if (id.isEmpty() || vsCollection.isEmpty()) {
//      return badRequest();
//    }
//    try {
//      id = Util.encodeIfNeeded(id);
//      PagedResults<Value> values = termService.findAllValuesInValueSetByValue(id, vsCollection, page, pageSize, apiKey);
//      ObjectMapper mapper = new ObjectMapper();
//      // This line ensures that @class type annotations are included for each element in the collection
//      ObjectWriter writer = mapper.writerFor(new TypeReference<PagedResults<Value>>() {});
//      return ok(mapper.readTree(writer.writeValueAsString(values)));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//  }
//
//  @ApiOperation(
//      value = "Update a provisional value",
//      httpMethod = "PATCH")
//  @ApiResponses(value = {
//      @ApiResponse(code = 204, message = "Success! (No Content)"),
//      @ApiResponse(code = 400, message = "Bad Request"),
//      @ApiResponse(code = 401, message = "Unauthorized"),
//      @ApiResponse(code = 404, message = "Not Found"),
//      @ApiResponse(code = 500, message = "Internal Server Error")})
//  @ApiImplicitParams(value = {
//      @ApiImplicitParam(name = "id", value = "Provisional value id. Example: 720f50f0-ae6f-0133-848f-005056010073",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(value = "Updated information for the value", required = true, dataType = "org.metadatacenter" +
//          ".terms" +
//          ".domainObjects.OntologyClass", paramType = "body")})
//  public static Result updateValue(String id) {
//    if (id.isEmpty()) {
//      return badRequest();
//    }
//    ObjectMapper mapper = new ObjectMapper();
//    Value v = mapper.convertValue(request().body().asJson(), Value.class);
//    v.setId(id);
//    try {
//      termService.updateProvisionalValue(v, apiKey);
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//    return internalServerError();
//  }

  @DELETE
  @Path("values/{id}")
  //  @ApiOperation(
  //      value = "Delete a provisional value",
  //      httpMethod = "DELETE")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 204, message = "Success! (No Content)"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Provisional value id. Example: 720f50f0-ae6f-0133-848f-005056010073",
  //          required = true, dataType = "string", paramType = "path")})
  public Response deleteValue(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      terminologyService.deleteProvisionalValue(id, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

}