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
  //  @ApiOperation(
  //      value = "Find all ontologies (excluding value set collections)",
  //      //notes = "This call is not paged",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
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
  //  @ApiOperation(
  //      value = "Find ontology by id. It returns ontology details, including number of classes and categories",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Ontology id. Examples: NCIT, OBI, FMA",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findOntology(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      //Cache.apiKeyCache = apiKey;
      // Retrieve ontology from ontologies cache
      Ontology ontologies = Cache.ontologiesCache.get("ontologies").get(id);
      if (ontologies == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
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
  //  @ApiOperation(
  //      value = "Get the root classes for a given ontology. If the ontology is CEDARPC, all provisional classes in
  // this" +
  //          " ontology will be returned",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "ontology", value = "Ontology identifier. Example: NCIT",
  //          required = true, dataType = "string", paramType = "path")})
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
  
}