package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
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
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/bioportal", tags = "Classes", authorizations = {@Authorization("api_key")})
public class ClassResource extends AbstractTerminologyServerResource {

  public ClassResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Path("ontologies/{ontology}/classes")
  @ApiOperation(value = "Create class", notes = "Create a provisional class.")
  @ApiResponses({
      @ApiResponse(code = 204, message = "Successful operation (no content)"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response createClass(
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology) throws CedarException {
    CedarRequestContext ctx = buildAnonymousRequestContext();
    try {
      OntologyClass c = JsonMapper.MAPPER.convertValue(ctx.request().getRequestBody().asJson(), OntologyClass.class);
      c.setOntology(ontology);
      OntologyClass createdClass = terminologyService.createProvisionalClass(c, apiKey);
      JsonNode createdClassJson = JsonMapper.MAPPER.valueToTree(createdClass);
      return Response.created(new URI(createdClass.getLdId())).entity(createdClassJson).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (URISyntaxException | IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/{id}")
  @ApiOperation(value = "Find class", notes = "Find class (either regular or provisional) by ontology and class id.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findClass(
      @ApiParam(value = "Class identifier. Examples: http://data.bioontology.org/provisional_classes/" +
          "4f82a7f0-bbba-0133-b23e-005056010074 (provisional class). " +
          "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C3224 (regular class).", required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology) throws
      CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      OntologyClass c = terminologyService.findClass(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(c)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes")
  @ApiOperation(value = "Get classes",
      notes = "Get all classes from a specific ontology (including both regular and provisional classes).")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findAllClassesForOntology(
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology,
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
      PagedResults<OntologyClass> classes =
          terminologyService.findAllClassesInOntology(ontology, page, pageSize, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(classes)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/{id}/tree")
  @ApiOperation(value = "Get class tree", notes = "Get class tree (only for regular classes).")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findClassTree(
      @ApiParam(value = "Class identifier. Example: http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C3224.",
          required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology) throws
      CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      boolean isFlat = Cache.ontologiesCache.get("ontologies").get(ontology).getIsFlat();
      List<TreeNode> tree = terminologyService.getClassTree(id, ontology, isFlat, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(tree)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException | ExecutionException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/{id}/children")
  @ApiOperation(value = "Get class children", notes = "Get class children (only for regular classes).")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findClassChildren(
      @ApiParam(value = "Class identifier. Example: http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C3224.",
          required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology,
      @ApiParam(value = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @ApiParam(value = "Number of results per page. Example: 10.")
      @QueryParam("pageSize")
                                        int pageSize) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
    try {
      PagedResults<OntologyClass> children = terminologyService.getClassChildren(id, ontology, page,
          pageSize, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(children)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }


  @GET
  @Path("ontologies/{ontology}/classes/{id}/descendants")
  @ApiOperation(value = "Get class descendants", notes = "Get class descendants.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findClassDescendants(
      @ApiParam(value = "Class identifier. Examples: http://data.bioontology.org/provisional_classes/" +
          "4f82a7f0-bbba-0133-b23e-005056010074 (provisional class). " +
          "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C3224 (regular class).", required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology,
      @ApiParam(value = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @ApiParam(value = "Number of results per page. Example: 10.")
      @QueryParam("pageSize") int pageSize)
      throws CedarException {
    CedarRequestContext ctx = buildAnonymousRequestContext();
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
    try {
      PagedResults<OntologyClass> descendants = terminologyService.getClassDescendants(id, ontology,
          page, pageSize, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(descendants)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/{id}/parents")
  @ApiOperation(value = "Get class parents", notes = "Get class parents.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findClassParents(
      @ApiParam(value = "Class identifier. Example: http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C3224.",
          required = true)
      @PathParam("id") @Encoded String id,
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyClass> descendants = terminologyService.getClassParents(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(descendants)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("classes/provisional")
  @ApiOperation(value = "Get provisional classes",
      notes = "Get provisional classes (including provisional value sets and provisional values).")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findAllProvisionalClasses(
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
      PagedResults<OntologyClass> classes = terminologyService.findAllProvisionalClasses(null, page, pageSize, apiKey);
      // This line ensures that @class type annotations are included for each element in the list
      //ObjectWriter writer = JsonMapper.MAPPER.writerFor(new TypeReference<PagedResults<OntologyClass>>() {});
      //return Response.ok().entity(JsonMapper.MAPPER.readTree(writer.writeValueAsString(classes))).build();
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(classes)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/provisional")
  @ApiOperation(value = "Get all provisional classes in a particular ontology",
      notes = "Get all provisional classes in a particular ontology (including provisional value sets and " +
          "provisional values)")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findAllProvisionalClassesForOntology(
      @ApiParam(value = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology,
      @ApiParam(value = "Page to be returned. Example: 7.") @QueryParam
      ("page") @DefaultValue("1") int page,
      @ApiParam(value = "Number of results per page. Example: 10.")
      @QueryParam("pageSize") int pageSize) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
    try {
      PagedResults<OntologyClass> classes =
          terminologyService.findAllProvisionalClasses(ontology, page, pageSize, apiKey);
      // This line ensures that @class type annotations are included for each element in the list
      ObjectWriter writer = JsonMapper.MAPPER.writerFor(new TypeReference<PagedResults<OntologyClass>>() {
      });
      return Response.ok().entity(JsonMapper.MAPPER.readTree(writer.writeValueAsString(classes))).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @PUT
  @Path("classes/{id}")
  @ApiOperation(value = "Update a provisional class", notes = "Update a provisional class.")
  @ApiResponses({
      @ApiResponse(code = 204, message = "Successful operation (no content)"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response updateClass(
      @ApiParam(value = "Provisional class identifier. Example: http://data.bioontology.org/provisional_classes/" +
          "4f82a7f0-bbba-0133-b23e-005056010074.", required = true)
      @PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      OntologyClass c = JsonMapper.MAPPER.readValue(request.getInputStream(), OntologyClass.class);
      //c.setId(id);
      terminologyService.updateProvisionalClass(c, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @DELETE
  @Path("classes/{id}")
  @ApiOperation(value = "Delete a provisional class", notes = "Update a provisional class.")
  @ApiResponses({
      @ApiResponse(code = 204, message = "Successful operation (no content)"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response deleteClass(
      @ApiParam(value = "Provisional class identifier. Example: http://data.bioontology.org/provisional_classes/" +
          "4f82a7f0-bbba-0133-b23e-005056010074.", required = true)
      @PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      terminologyService.deleteProvisionalClass(id, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

}
