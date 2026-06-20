package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.domainObjects.ValueSet;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/bioportal", tags = "Value sets", authorizations = {@Authorization("api_key")})
public class ValueSetResource extends AbstractTerminologyServerResource {

  public ValueSetResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Path("vs-collections/{vs_collection}/value-sets")
  @ApiOperation(value = "Create a provisional value set",
      notes = "Create a provisional value set in a particular value set collection.")
  @ApiResponses({
      @ApiResponse(code = 204, message = "Successful operation (no content)"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response createValueSet(
      @ApiParam(value = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
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
  @ApiOperation(value = "Find value set by id", notes = "Find provisional value set by id (either provisional or " +
      "regular).")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findValueSet(
      @ApiParam(value = "Provisional value set short identifier. Example: af033050-b04b-0133-981f-005056010074",
          required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
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
  @ApiOperation(value = "Get all value sets in a value set collection",
      notes = "Get all value sets in a value set collection.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findValueSetsByVsCollection(
      @ApiParam(value = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection,
      @ApiParam(value = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @ApiParam(value = "Number of results per page. Example: 10.")
      @QueryParam("pageSize") int pageSize) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
    try {
      PagedResults<ValueSet> valueSets =
          terminologyService.findValueSetsByVsCollection(vsCollection, page, pageSize, apiKey);
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
  @ApiOperation(value = "Find the value set that contains a particular value",
      notes = "Find the value set that contains a particular value.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findValueSetByValue(
      @ApiParam(value = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "Value set collection. Example: CEDARVS.", required = true)
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

  @GET
  @Path("vs-collections/{vs_collection}/value-sets/{id}/tree")
  @ApiOperation(value = "Get value set tree", notes = "Get value set tree (only for regular value sets).")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findValueSetTree(
      @ApiParam(value = "Provisional value set short identifier. Example: af033050-b04b-0133-981f-005056010074",
          required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      TreeNode tree = terminologyService.getValueSetTree(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(tree)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("value-sets")
  @ApiOperation(value = "Find all value sets", notes = "Find all value sets.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findAllValueSets() throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<ValueSet> valueSets = new ArrayList<>(Cache.valueSetsCache.get("value-sets").values());
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(valueSets)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (ExecutionException e) {
      throw new CedarAssertionException(e);
    }
  }

  @PUT
  @Path("value-sets/{id}")
  @ApiOperation(value = "Update a provisional value set", notes = "Update a provisional value set.")
  @ApiResponses({
      @ApiResponse(code = 204, message = "Successful operation (no content)"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response updateValueSet(
      @ApiParam(value = "Provisional value set short identifier. Example: af033050-b04b-0133-981f-005056010074",
          required = true)
      @PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      ValueSet vs = JsonMapper.MAPPER.readValue(request.getInputStream(), ValueSet.class);
      terminologyService.updateProvisionalValueSet(vs, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @DELETE
  @Path("value-sets/{id}")
  @ApiOperation(value = "Delete a provisional value set", notes = "Update a provisional value set.")
  @ApiResponses({
      @ApiResponse(code = 204, message = "Successful operation (no content)"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response deleteValueSet(
      @ApiParam(value = "Provisional value set short identifier. Example: af033050-b04b-0133-981f-005056010074",
          required = true)
      @PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
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

}