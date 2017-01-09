package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.TreeNode;
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
public class OntologyResource extends AbstractResource {

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
  public Response findAllOntologies() throws CedarAssertionException {
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

//
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
//  public Result findOntology(String id) {
//    if (id.isEmpty()) {
//      return badRequest();
//    }
//    try {
//      Cache.apiKeyCache = apiKey;
//      // Retrieve ontology from ontologies cache
//      Ontology o = Cache.ontologiesCache.get("ontologies").get(id);
//      if (o == null) {
//        // Not found
//        return notFound();
//      }
//      return ok((JsonNode) new ObjectMapper().valueToTree(o));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (ExecutionException e) {
//      return internalServerError(e.getMessage());
//    }
//  }
//
//  @ApiOperation(
//      value = "Get the root classes for a given ontology. If the ontology is CEDARPC, all provisional classes in this" +
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
//  public Result findRootClasses(String ontology) {
//    if (ontology.isEmpty()) {
//      return badRequest();
//    }
//    try {
//      boolean isFlat = Cache.ontologiesCache.get("ontologies").get(ontology).getIsFlat();
//      List<OntologyClass> roots = termService.getRootClasses(ontology, isFlat, apiKey);
//      return ok((JsonNode) new ObjectMapper().valueToTree(roots));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    } catch (ExecutionException e) {
//      return internalServerError(e.getMessage());
//    }
//  }
  
}