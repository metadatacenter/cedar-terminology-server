package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.validator.constraints.NotEmpty;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractResource;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.util.Util;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;
import static org.metadatacenter.terms.util.Constants.*;

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
  public Response createClass(@PathParam("ontology") String ontology) throws CedarAssertionException {
    try {
      OntologyClass c = JsonMapper.MAPPER.readValue(request.getInputStream(), OntologyClass.class);
      c.setOntology(ontology);
      OntologyClass createdClass = terminologyService.createProvisionalClass(c, apiKey);
      JsonNode createdClassJson = JsonMapper.MAPPER.valueToTree(createdClass);
      return Response.created(new URI(createdClass.getLdId())).entity(createdClassJson).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (URISyntaxException e) {
      throw new CedarAssertionException(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
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
  public static Response findClass(@PathParam("ontology") String ontology, @PathParam("id") String id) throws
      CedarAssertionException {
    try {
      id = Util.encodeIfNeeded(id);
      OntologyClass c = terminologyService.findClass(Util.encodeIfNeeded(id), ontology, apiKey);
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
  public static Response findAllClassesForOntology(@PathParam("ontology") String ontology,
                                                   @QueryParam("page") @DefaultValue("1") int page,
                                                   @QueryParam("pageSize") int pageSize) throws CedarAssertionException {
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
    try {
      PagedResults<OntologyClass> classes = terminologyService.findAllClassesInOntology(ontology, page, pageSize,
          apiKey);
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
  public static Response deleteClass(@PathParam("id") String id) throws CedarAssertionException {
    try {
      terminologyService.deleteProvisionalClass(id, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }



//
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
//  public static Result findClassTree(String id, String ontology) {
//    if (id.isEmpty() || ontology.isEmpty()) {
//      return badRequest();
//    }
//    try {
//      id = Util.encodeIfNeeded(id);
//      boolean isFlat = Cache.ontologiesCache.get("ontologies").get(ontology).getIsFlat();
//      List<TreeNode> tree = termService.getClassTree(Util.encodeIfNeeded(id), ontology, isFlat, apiKey);
//      return ok((JsonNode) new ObjectMapper().valueToTree(tree));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    } catch (ExecutionException e) {
//      return internalServerError(e.getMessage());
//    }
//  }
//
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
//      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
//          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
//          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
//          required = true, dataType = "string", paramType = "path")})
//  public static Result findClassChildren(String id, String ontology, @ApiParam(value = "Integer representing the " +
//      "page number. Default: 'page=1'", required = false) @QueryParam("page") int page, @ApiParam(value = "Integer " +
//      "representing the size of the returned page. Default: 'pageSize=50'", required = false) @QueryParam
//      ("page_size") int pageSize) {
//    if (id.isEmpty() || ontology.isEmpty()) {
//      return badRequest();
//    }
//    try {
//      id = Util.encodeIfNeeded(id);
//      PagedResults<OntologyClass> children = termService.getClassChildren(Util.encodeIfNeeded(id), ontology, page,
//          pageSize, apiKey);
//      return ok((JsonNode) new ObjectMapper().valueToTree(children));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//  }
//
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
//      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
//          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
//          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
//          required = true, dataType = "string", paramType = "path")})
//  public static Result findClassDescendants(String id, String ontology, @ApiParam(value = "Integer representing the
// " +
//      "page number. Default: 'page=1'", required = false) @QueryParam("page") int page, @ApiParam(value = "Integer " +
//      "representing the size of the returned page. Default: 'pageSize=50'", required = false) @QueryParam
//      ("page_size") int pageSize) {
//    if (id.isEmpty() || ontology.isEmpty()) {
//      return badRequest();
//    }
//    try {
//      id = Util.encodeIfNeeded(id);
//      PagedResults<OntologyClass> descendants = termService.getClassDescendants(Util.encodeIfNeeded(id), ontology,
//          page, pageSize, apiKey);
//      return ok((JsonNode) new ObjectMapper().valueToTree(descendants));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//  }
//
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
//  public static Result findClassParents(String id, String ontology) {
//    if (id.isEmpty() || ontology.isEmpty()) {
//      return badRequest();
//    }
//    try {
//      id = Util.encodeIfNeeded(id);
//      List<OntologyClass> parents = termService.getClassParents(Util.encodeIfNeeded(id), ontology, apiKey);
//      return ok((JsonNode) new ObjectMapper().valueToTree(parents));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//  }
//
//  @ApiOperation(
//      value = "Get all provisional classes (including provisional value sets and provisional values)",
//      httpMethod = "GET")
//  @ApiResponses(value = {
//      @ApiResponse(code = 200, message = "Success!"),
//      @ApiResponse(code = 400, message = "Bad Request"),
//      @ApiResponse(code = 401, message = "Unauthorized"),
//      @ApiResponse(code = 500, message = "Internal Server Error")})
//  public static Result findAllProvisionalClasses() {
//    try {
//      List<OntologyClass> classes = termService
//          .findAllProvisionalClasses(null, apiKey);
//      ObjectMapper mapper = new ObjectMapper();
//      // This line ensures that @class type annotations are included for each element in the list
//      ObjectWriter writer = mapper.writerFor(new TypeReference<List<OntologyClass>>() {
//      });
//      return ok(mapper.readTree(writer.writeValueAsString(classes)));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//  }
//
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
//  public static Result findAllProvisionalClassesForOntology(String ontology) {
//    if (ontology.isEmpty()) {
//      return badRequest();
//    }
//    try {
//      List<OntologyClass> classes = termService
//          .findAllProvisionalClasses(ontology, apiKey);
//      ObjectMapper mapper = new ObjectMapper();
//      // This line ensures that @class type annotations are included for each element in the list
//      ObjectWriter writer = mapper.writerFor(new TypeReference<List<OntologyClass>>() {
//      });
//      return ok(mapper.readTree(writer.writeValueAsString(classes)));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//  }
//
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
//  public static Result updateClass(String id) {
//    if (id.isEmpty()) {
//      return badRequest();
//    }
//    ObjectMapper mapper = new ObjectMapper();
//    OntologyClass c = mapper.convertValue(request().body().asJson(), OntologyClass.class);
//    c.setId(id);
//    try {
//      termService.updateProvisionalClass(c, apiKey);
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//    return internalServerError();
//  }
//




}