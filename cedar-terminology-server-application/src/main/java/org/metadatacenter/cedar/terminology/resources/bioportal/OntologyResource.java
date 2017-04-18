package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.OntologyProperty;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
public class OntologyResource extends AbstractTerminologyServerResource {

  public OntologyResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Path("ontologies")
  public Response findAllOntologies() throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<Ontology> ontologies = new ArrayList<>(Cache.ontologiesCache.get("ontologies").values());
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(ontologies)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (ExecutionException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{id}")
  public Response findOntology(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      // Retrieve ontology from ontologies cache
      Ontology ontologies = Cache.ontologiesCache.get("ontologies").get(id);
      if (ontologies == null) {
        return CedarResponse.notFound().build();
      }
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(ontologies)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (ExecutionException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/roots")
  public Response findRootClasses(@PathParam("ontology") String ontology) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      boolean isFlat = Cache.ontologiesCache.get("ontologies").get(ontology).getIsFlat();
      List<OntologyClass> roots = terminologyService.getRootClasses(ontology, isFlat, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(roots)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException | ExecutionException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties/roots")
  public Response findRootProperties(@PathParam("ontology") String ontology) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyProperty> roots = terminologyService.getRootProperties(ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(roots)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }
  
}