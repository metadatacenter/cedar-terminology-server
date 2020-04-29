package org.metadatacenter.cedar.terminology.resources.bioportal;

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
public class PropertyResource extends AbstractTerminologyServerResource {

  public PropertyResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Path("ontologies/{ontology}/properties/{id}")
  public Response findProperty(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology)
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
  // Note that this endpoint is not paged
  public Response findAllPropertiesForOntology(@PathParam("ontology") String ontology) throws CedarException {
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
  public Response findPropertyTree(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology) throws
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
  public Response findPropertyChildren(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology)
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
  public Response findPropertyDescendants(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology)
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
  public Response findPropertyParents(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology)
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