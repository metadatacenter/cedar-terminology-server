package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractResource;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
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
public class ClassResource extends AbstractResource {

  @POST
  @Path("ontologies/{ontology}/classes")
  //  @ApiOperation(
  //      value = "Create a provisional class",
  //      httpMethod = "POST")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Successful creation of a provisional class"),
  //      //@ApiResponse(code = 200, message = "Successful creation of a provisional class", response = OntologyClass
  //      // .class),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "ontology", value = "Ontology identifier. Example: CEDARPC", required = true, dataType
  //          = "string", paramType = "path"),
  //      @ApiImplicitParam(value = "Class to be created", required = true, dataType = "org.metadatacenter.terms" +
  //          ".domainObjects.OntologyClass", paramType = "body")})
  public Response createClass(@PathParam("ontology") String ontology) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      OntologyClass c = JsonMapper.MAPPER.readValue(request.getInputStream(), OntologyClass.class);
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
  //  @ApiOperation(
  //      value = "Find class (either regular or provisional) by id and ontology",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
  //          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
  //          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
  //          required = true, dataType = "string", paramType = "path"),
  //      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findClass(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology) throws
      CedarAssertionException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
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
  //  @ApiOperation(
  //      value = "Get all classes from a specific ontology (including provisional classes)",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findAllClassesForOntology(@PathParam("ontology") String ontology,
                                                   @QueryParam("page") @DefaultValue("1") int page,
                                                   @QueryParam("pageSize") int pageSize) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
    try {
      PagedResults<OntologyClass> classes = terminologyService.findAllClassesInOntology(ontology, page, pageSize, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(classes)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @DELETE
  @Path("classes/{id}")
  //  @ApiOperation(
  //      value = "Delete a provisional class",
  //      httpMethod = "DELETE")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 204, message = "Success! (No Content)"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Provisional class id. Example: 720f50f0-ae6f-0133-848f-005056010073",
  //          required = true, dataType = "string", paramType = "path")})
  public Response deleteClass(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
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

  @GET
  @Path("ontologies/{ontology}/classes/{id}/tree")
//  @ApiOperation(
//      value = "Get class tree (only for regular classes)",
//      httpMethod = "GET")
//  @ApiResponses(value = {
//      @ApiResponse(code = 200, message = "Success!"),
//      @ApiResponse(code = 400, message = "Bad Request"),
//      @ApiResponse(code = 401, message = "Unauthorized"),
//      @ApiResponse(code = 404, message = "Not Found"),
//      @ApiResponse(code = 500, message = "Internal Server Error")})
//  @ApiImplicitParams(value = {
//      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
//          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
//          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
//          required = true, dataType = "string", paramType = "path")})
  public Response findClassTree(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology) throws
      CedarAssertionException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
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
  //  @ApiOperation(
  //      value = "Get class children (only for regular classes)",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata
  // .bioontology" +
  //          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
  //          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
  //          required = true, dataType = "string", paramType = "path"),
  //      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findClassChildren(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology,
                                           @QueryParam("page") @DefaultValue("1") int page, @QueryParam("pageSize")
                                             int pageSize) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
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
  //  @ApiOperation(
  //      value = "Find descendants of a given class",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata
  // .bioontology" +
  //          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
  //          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
  //          required = true, dataType = "string", paramType = "path"),
  //      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findClassDescendants(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology,
                                              @QueryParam("page") @DefaultValue("1") int page, @QueryParam("pageSize") int pageSize) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
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
//  @ApiOperation(
//      value = "Get class parents (only for regular classes)",
//      httpMethod = "GET")
//  @ApiResponses(value = {
//      @ApiResponse(code = 200, message = "Success!"),
//      @ApiResponse(code = 400, message = "Bad Request"),
//      @ApiResponse(code = 401, message = "Unauthorized"),
//      @ApiResponse(code = 404, message = "Not Found"),
//      @ApiResponse(code = 500, message = "Internal Server Error")})
//  @ApiImplicitParams(value = {
//      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
//          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
//          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
//          required = true, dataType = "string", paramType = "path")})
  public Response findClassParents(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology)
      throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
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
  //  @ApiOperation(
  //      value = "Get all provisional classes (including provisional value sets and provisional values)",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  public Response findAllProvisionalClasses(@QueryParam("page") @DefaultValue("1") int page,
                                                   @QueryParam("pageSize") int pageSize) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
    try {
      PagedResults<OntologyClass> classes = terminologyService.findAllProvisionalClasses(null, page, pageSize, apiKey);
      // This line ensures that @class type annotations are included for each element in the list
      ObjectWriter writer = JsonMapper.MAPPER.writerFor(new TypeReference<PagedResults<OntologyClass>>() {});
      return Response.ok().entity(JsonMapper.MAPPER.readTree(writer.writeValueAsString(classes))).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/provisional")
  //  @ApiOperation(
  //      value = "Get all provisional classes for a specific ontology (including provisional value sets and
  // provisional " +
  //          "values)",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
  //          required = true, dataType = "string", paramType = "path")})
  public Response findAllProvisionalClassesForOntology(@PathParam("ontology") String ontology, @QueryParam
      ("page") @DefaultValue("1") int page, @QueryParam("pageSize") int pageSize) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
    try {
      PagedResults<OntologyClass> classes = terminologyService.findAllProvisionalClasses(ontology, page, pageSize, apiKey);
      // This line ensures that @class type annotations are included for each element in the list
      ObjectWriter writer = JsonMapper.MAPPER.writerFor(new TypeReference<PagedResults<OntologyClass>>() {});
      return Response.ok().entity(JsonMapper.MAPPER.readTree(writer.writeValueAsString(classes))).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @PUT
  @Path("classes/{id}")
  //  @ApiOperation(
  //      value = "Update a provisional class",
  //      httpMethod = "PATCH")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 204, message = "Success! (No Content)"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 404, message = "Not Found"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  //  @ApiImplicitParams(value = {
  //      @ApiImplicitParam(name = "id", value = "Provisional class id. Example: 720f50f0-ae6f-0133-848f-005056010073",
  //          required = true, dataType = "string", paramType = "path"),
  //      @ApiImplicitParam(value = "Updated information for the class", required = true, dataType = "org
  // .metadatacenter" +
  //          ".terms" +
  //          ".domainObjects.OntologyClass", paramType = "body")})
  public Response updateClass(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
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

}