package org.metadatacenter.cedar.terminology.resources.bioportal;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.domainObjects.OntologyProperty;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.List;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/bioportal", tags = "Properties", authorizations = {@Authorization("api_key")})
public class PropertyResource extends AbstractTerminologyServerResource {

  public PropertyResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Path("ontologies/{ontology}/properties/{id}")
  @ApiOperation(value = "Find property", notes = "Find property by id.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findProperty(
      @ApiParam(value = "Property identifier. Examples: http://id.loc.gov/ontologies/bibframe/place.", required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      OntologyProperty p = terminologyService.findProperty(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(p)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties")
  @ApiOperation(value = "Get properties", notes = "Get all properties from a specific ontology.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  // Note that this endpoint is not paged
  public Response findAllPropertiesForOntology(
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyProperty> properties = terminologyService.findAllPropertiesInOntology(ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(properties)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties/{id}/tree")
  @ApiOperation(value = "Get property tree", notes = "Get property tree.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findPropertyTree(
      @ApiParam(value = "Property identifier. Examples: http://id.loc.gov/ontologies/bibframe/place.", required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology) throws
      CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<TreeNode> tree = terminologyService.getPropertyTree(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(tree)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties/{id}/children")
  @ApiOperation(value = "Get property children", notes = "Get property children (only for regular classes).")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findPropertyChildren(
      @ApiParam(value = "Property identifier. Examples: http://id.loc.gov/ontologies/bibframe/place.", required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyProperty> children = terminologyService.getPropertyChildren(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(children)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties/{id}/descendants")
  @ApiOperation(value = "Get property descendants", notes = "Get property descendants.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findPropertyDescendants(
      @ApiParam(value = "Property identifier. Examples: http://id.loc.gov/ontologies/bibframe/place.", required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyProperty> descendants = terminologyService.getPropertyDescendants(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(descendants)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties/{id}/parents")
  @ApiOperation(value = "Get property parents", notes = "Get property parents.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findPropertyParents(
      @ApiParam(value = "Property identifier. Examples: http://id.loc.gov/ontologies/bibframe/place.", required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyProperty> descendants = terminologyService.getPropertyParents(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(descendants)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

}