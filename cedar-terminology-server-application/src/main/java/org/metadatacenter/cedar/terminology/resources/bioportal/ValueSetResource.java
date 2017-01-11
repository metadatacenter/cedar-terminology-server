package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.ValueSet;
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
public class ValueSetResource extends AbstractTerminologyServerResource {

  public ValueSetResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Path("vs-collections/{vs_collection}/value-sets")
  //  @ApiOperation(
  //      value = "Create a provisional value set",
  //      httpMethod = "POST")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Successful creation of a provisional value set"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS", required = true,
  //          dataType = "string", paramType = "path"),
  //      @ApiImplicitParam(value = "Value set to be created", required = true, dataType = "org.metadatacenter.terms" +
  //          ".domainObjects.ValueSet", paramType = "body")})
  public Response createValueSet(@PathParam("vs_collection") String vsCollection) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      ValueSet vs = JsonMapper.MAPPER.convertValue(ctx.request().getRequestBody().asJson(), ValueSet.class);
      vs.setVsCollection(vsCollection);
      ValueSet createdValueSet = terminologyService.createProvisionalValueSet(vs, apiKey);
      JsonNode createdValueSetJson = JsonMapper.MAPPER.valueToTree(createdValueSet);
      // Refresh value sets cache
      Cache.valueSetsCache.refresh("value-sets");
      return Response.created(new URI(createdValueSet.getLdId())).entity(createdValueSetJson).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (URISyntaxException | IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/value-sets/{id}")
  //  @ApiOperation(
  //      value = "Find provisional value set by id (either provisional or regular)",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
  //          required = true, dataType = "string", paramType = "path"),
  //      @ApiImplicitParam(name = "id", value = "Provisional value set id. Example:
  // af033050-b04b-0133-981f-005056010074",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findValueSet(@PathParam("id") @Encoded String id, @PathParam("vs_collection") String vsCollection)
      throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      ValueSet vs = terminologyService.findValueSet(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(vs)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/value-sets")
  //  @ApiOperation(
  //      value = "Find all value sets in a value set collection",
  //      // notes = ...
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findValueSetsByVsCollection(@PathParam("vs_collection") String vsCollection,
                                              @QueryParam("page") @DefaultValue("1") int page,
                                              @QueryParam("pageSize") int pageSize) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
    try {
      PagedResults<ValueSet> valueSets = terminologyService.findValueSetsByVsCollection(vsCollection, page, pageSize, apiKey);
      // This line ensures that @class type annotations are included for each element in the collection
      //ObjectWriter writer = JsonMapper.MAPPER.writerFor(new TypeReference<PagedResults<ValueSet>>() {});
      //return Response.ok().entity(JsonMapper.MAPPER.valueToTree(writer.writeValueAsString(valueSets))).build();
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(valueSets)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/values/{id}/value-set")
  //  @ApiOperation(
  //      value = "Find the value set that contains a particular value",
  //      // notes = ...
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Value id. Example: 42f22880-b04b-0133-848f-005056010073",
  //          required = true, dataType = "string", paramType = "path"),
  //      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findValueSetByValue(@PathParam("id") @Encoded String id,
                                      @PathParam("vs_collection") String vsCollection) throws CedarException {
    try {
      ValueSet vs = terminologyService.findValueSetByValue(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(vs)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

//
//  @ApiOperation(
//      value = "Get value set tree",
//      httpMethod = "GET")
//  @ApiResponses(value = {
//      @ApiResponse(code = 200, message = "Success!"),
//      @ApiResponse(code = 400, message = "Bad Request"),
//      @ApiResponse(code = 401, message = "Unauthorized"),
//      @ApiResponse(code = 404, message = "Not Found"),
//      @ApiResponse(code = 500, message = "Internal Server Error")})
//  @ApiImplicitParams(value = {
//      @ApiImplicitParam(name = "id", value = "Value set id. It must be encoded",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
//          required = true, dataType = "string", paramType = "path")})
//  public static Result findValueSetTree(String id, String vsCollection) {
//    if (id.isEmpty() || vsCollection.isEmpty()) {
//      return badRequest();
//    }
//    try {
//      id = Util.encodeIfNeeded(id);
//      TreeNode tree = termService.getValueSetTree(Util.encodeIfNeeded(id), vsCollection, apiKey);
//      return ok((JsonNode) new ObjectMapper().valueToTree(tree));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//  }
//
//  @ApiOperation(
//      value = "Find all value sets",
//      // notes = ...
//      httpMethod = "GET")
//  @ApiResponses(value = {
//      @ApiResponse(code = 200, message = "Success!"),
//      @ApiResponse(code = 400, message = "Bad Request"),
//      @ApiResponse(code = 401, message = "Unauthorized"),
//      @ApiResponse(code = 500, message = "Internal Server Error")})
//  public static Result findAllValueSets() {
//    try {
//      List<ValueSet> valueSets = new ArrayList<ValueSet>(Cache.valueSetsCache.get("value-sets").values());
//      ObjectMapper mapper = new ObjectMapper();
//      // This line ensures that @class type annotations are included for each element in the collection
//      ObjectWriter writer = mapper.writerFor(new TypeReference<List<ValueSet>>() {
//      });
//      return ok(mapper.readTree(writer.writeValueAsString(valueSets)));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    } catch (ExecutionException e) {
//      return internalServerError(e.getMessage());
//    }
//  }
//
//  @ApiOperation(
//      value = "Update a provisional value set",
//      httpMethod = "PATCH")
//  @ApiResponses(value = {
//      @ApiResponse(code = 204, message = "Success! (No Content)"),
//      @ApiResponse(code = 400, message = "Bad Request"),
//      @ApiResponse(code = 401, message = "Unauthorized"),
//      @ApiResponse(code = 404, message = "Not Found"),
//      @ApiResponse(code = 500, message = "Internal Server Error")})
//  @ApiImplicitParams(value = {
//      @ApiImplicitParam(name = "id", value = "Provisional value set id. Example: 720f50f0-ae6f-0133-848f-005056010073",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(value = "Updated information for the value set", required = true, dataType = "org" +
//          ".metadatacenter.terms" +
//          ".domainObjects.ValueSet", paramType = "body")})
//  public static Result updateValueSet(String id) {
//    if (id.isEmpty()) {
//      return badRequest();
//    }
//    ObjectMapper mapper = new ObjectMapper();
//    ValueSet vs = mapper.convertValue(request().body().asJson(), ValueSet.class);
//    vs.setId(id);
//    try {
//      termService.updateProvisionalValueSet(vs, apiKey);
//    } catch (HTTPException e) {
//      if (e.getStatusCode() == 204) {
//        // Refresh value sets cache
//        Cache.valueSetsCache.refresh("value-sets");
//      }
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//    return internalServerError();
//  }
//

  @DELETE
  @Path("value-sets/{id}")
  //  @ApiOperation(
  //      value = "Delete a provisional value set",
  //      httpMethod = "DELETE")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 204, message = "Success! (No Content)"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Provisional value set id. Example: 720f50f0-ae6f-0133-848f-005056010073",
  //          required = true, dataType = "string", paramType = "path")})
  public Response deleteValueSet(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      terminologyService.deleteProvisionalValueSet(id, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

//
//  @ApiOperation(
//      value = "Find all values in a value set (regular or provisional)",
//      // notes = ...
//      httpMethod = "GET")
//  @ApiResponses(value = {
//      @ApiResponse(code = 200, message = "Success!"),
//      @ApiResponse(code = 400, message = "Bad Request"),
//      @ApiResponse(code = 401, message = "Unauthorized"),
//      @ApiResponse(code = 404, message = "Not Found"),
//      @ApiResponse(code = 500, message = "Internal Server Error")})
//  @ApiImplicitParams(value = {
//      @ApiImplicitParam(name = "vs", value = "Value set identifier. Example: http%3A%2F%2Fwww.semanticweb" +
//          ".org%2Fjgraybeal%2Fontologies%2F2015%2F7%2Fcedarvaluesets%23Study_File_Type",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
//          required = true, dataType = "string", paramType = "path")})
//  public static Result findValuesByValueSet(String vsId, String vsCollection, @ApiParam(value = "Integer representing" +
//      " the " +
//      "page number. Default: 'page=1'", required = false) @QueryParam("page") int page, @ApiParam(value = "Integer " +
//      "representing the size of the returned page. Default: 'pageSize=50'", required = false) @QueryParam
//      ("page_size") int pageSize) {
//    if ((vsId == null) || (vsId.length() == 0) || (vsCollection == null) || (vsCollection.length() == 0)) {
//      return badRequest();
//    }
//    try {
//      vsId = Utils.encodeIfNeeded(vsId);
//      PagedResults<Value> values = termService.findValuesByValueSet(vsId, vsCollection, page, pageSize, apiKey);
//      ObjectMapper mapper = new ObjectMapper();
//      // This line ensures that @class type annotations are included for each element in the collection
//      ObjectWriter writer = mapper.writerFor(new TypeReference<PagedResults<Value>>() {
//      });
//      return ok(mapper.readTree(writer.writeValueAsString(values)));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//  }



}