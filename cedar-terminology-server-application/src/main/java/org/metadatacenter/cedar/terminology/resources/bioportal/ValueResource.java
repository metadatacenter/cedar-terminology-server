package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
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
@Api(value = "/bioportal", tags = "Values", authorizations = {@Authorization("api_key")})
public class ValueResource extends AbstractTerminologyServerResource {

  public ValueResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Path("vs-collections/{vs_collection}/value-sets/{vs}/values")
  @ApiOperation(value = "Create a provisional value", notes = "Create a provisional value in a given value set.")
  @ApiResponses({
      @ApiResponse(code = 204, message = "Successful operation (no content)"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response createValue(
      @ApiParam(value = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection,
      @ApiParam(value = "Value set identifier. Example: http://www.semanticweb.org/jgraybeal/ontologies/2015/7/" +
          "cedarvaluesets#Study_File_Type", required = true)
      @PathParam("vs") String vs)
      throws CedarException {
    CedarRequestContext ctx = buildAnonymousRequestContext();
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
  @ApiOperation(value = "Find value by id", notes = "Find value by id.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findValue(
      @ApiParam(value = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection) throws
      CedarException {
    CedarRequestContext ctx = buildRequestContext();
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
  @ApiOperation(value = "Get value tree", notes = "Get value tree (only for regular values).")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findValueTree(
      @ApiParam(value = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
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

  @GET
  @Path("vs-collections/{vs_collection}/value-sets/{vs}/values")
  @ApiOperation(value = "Find all values in a value set",
      notes = "Find all values in a value set (either regular or provisional).")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findValuesByValueSet(
      @ApiParam(value = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection,
      @ApiParam(value = "Value set identifier. Example: http://www.semanticweb.org/jgraybeal/ontologies/2015/7/" +
          "cedarvaluesets#Study_File_Type", required = true)
      @PathParam("vs") @Encoded String vsId,
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
      PagedResults<Value> values = terminologyService.findValuesByValueSet(vsId, vsCollection, page, pageSize, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(values)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/values/{id}/all-values")
  @ApiOperation(value = "Find all values in the value set that the given value belongs to",
      notes = "Find all values in the value set that the given value belongs to.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findAllValuesInValueSetByValue(
      @ApiParam(value = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam("id") @Encoded String id,
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
      PagedResults<Value> values =
          terminologyService.findAllValuesInValueSetByValue(id, vsCollection, page, pageSize, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(values)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @PUT
  @Path("values/{id}")
  @ApiOperation(value = "Update a provisional value", notes = "Update a provisional value.")
  @ApiResponses({
      @ApiResponse(code = 204, message = "Successful operation (no content)"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response updateValue(
      @ApiParam(value = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      Value v = JsonMapper.MAPPER.readValue(request.getInputStream(), Value.class);
      terminologyService.updateProvisionalValue(v, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @DELETE
  @Path("values/{id}")
  @ApiOperation(value = "Delete a provisional value", notes = "Delete a provisional value.")
  @ApiResponses({
      @ApiResponse(code = 204, message = "Successful operation (no content)"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response deleteValue(
      @ApiParam(value = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
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
