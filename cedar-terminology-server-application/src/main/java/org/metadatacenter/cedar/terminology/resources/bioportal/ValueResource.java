package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
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

  @POST
  @Path("vs-collections/{vs_collection}/value-sets/{vs}/values")
  @Operation(summary = "Create a provisional value", description = "Create a provisional value in a given value set.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Successful operation (no content)"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response createValue(
      @Parameter(description = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection,
      @Parameter(description = "Value set identifier. Example: http://www.semanticweb.org/jgraybeal/ontologies/2015/7/" +
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
  @Operation(summary = "Find value by id", description = "Find value by id.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
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
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/values/{id}/tree")
  @Operation(summary = "Get value tree", description = "Get value tree (only for regular values).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
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
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/value-sets/{vs}/values")
  @Operation(summary = "Find all values in a value set", description = "Find all values in a value set (either regular or provisional).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
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
  @Operation(summary = "Find all values in the value set that the given value belongs to", description = "Find all values in the value set that the given value belongs to.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findAllValuesInValueSetByValue(
      @Parameter(description = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "Value set collection. Example: CEDARVS.", required = true)
      @PathParam("vs_collection") String vsCollection,
      @Parameter(description = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @Parameter(description = "Number of results per page. Example: 10.")
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
  @Operation(summary = "Update a provisional value", description = "Update a provisional value.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Successful operation (no content)"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response updateValue(
      @Parameter(description = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
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
  @Operation(summary = "Delete a provisional value", description = "Delete a provisional value.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Successful operation (no content)"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response deleteValue(
      @Parameter(description = "Value identifier. Example: 42f22880-b04b-0133-848f-005056010073", required = true)
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
