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
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.domainObjects.OntologyProperty;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.List;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Properties")
@SecurityRequirement(name = "api_key")
public class PropertyResource extends AbstractTerminologyServerResource {

  public PropertyResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Path("ontologies/{ontology}/properties/{id}")
  @Operation(summary = "Find property", description = "Find property by id.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findProperty(
      @Parameter(description = "Property identifier. Examples: http://id.loc.gov/ontologies/bibframe/place.", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      OntologyProperty p = terminologyService.findProperty(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(p)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties")
  @Operation(summary = "Get properties", description = "Get all properties from a specific ontology.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  // Note that this endpoint is not paged
  public Response findAllPropertiesForOntology(
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyProperty> properties = terminologyService.findAllPropertiesInOntology(ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(properties)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties/{id}/tree")
  @Operation(summary = "Get property tree", description = "Get property tree.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findPropertyTree(
      @Parameter(description = "Property identifier. Examples: http://id.loc.gov/ontologies/bibframe/place.", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology) throws
      CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<TreeNode> tree = terminologyService.getPropertyTree(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(tree)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties/{id}/children")
  @Operation(summary = "Get property children", description = "Get property children (only for regular classes).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findPropertyChildren(
      @Parameter(description = "Property identifier. Examples: http://id.loc.gov/ontologies/bibframe/place.", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyProperty> children = terminologyService.getPropertyChildren(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(children)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties/{id}/descendants")
  @Operation(summary = "Get property descendants", description = "Get property descendants.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findPropertyDescendants(
      @Parameter(description = "Property identifier. Examples: http://id.loc.gov/ontologies/bibframe/place.", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyProperty> descendants = terminologyService.getPropertyDescendants(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(descendants)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties/{id}/parents")
  @Operation(summary = "Get property parents", description = "Get property parents.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findPropertyParents(
      @Parameter(description = "Property identifier. Examples: http://id.loc.gov/ontologies/bibframe/place.", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyProperty> descendants = terminologyService.getPropertyParents(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(descendants)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

}