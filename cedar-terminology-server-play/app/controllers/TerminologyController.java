package controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.wordnik.swagger.annotations.*;
import org.metadatacenter.terms.TerminologyService;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;
import org.metadatacenter.terms.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Configuration;
import play.Play;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;
import utils.Utils;

import javax.ws.rs.QueryParam;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.*;

@Api(value = "/bioportal", description = "BioPortal operations")
public class TerminologyController extends Controller {
  private static Logger log = LoggerFactory.getLogger(TerminologyController.class);

  public static final TerminologyService termService;

  static {
    Configuration config = Play.application().configuration();
    termService = new TerminologyService(config.getInt("bioportal.connectTimeout"), config.getInt("bioportal" +
        ".socketTimeout"));
  }

  @ApiOperation(
      value = "Find classes, value sets and value set items",
      //notes = "The search scope can be specified using comma separated strings",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header")})

  public static Result search(
      @ApiParam(value = "Search query. Example: 'melanoma'", required = true) @QueryParam("q") String q,
      @ApiParam(value = "Comma-separated list of search scopes. Accepted values={all,classes,value_sets,values}. "
          + "Default: 'scope=all'", required = false) @QueryParam("scope") String scope,
      @ApiParam(value = "Comma-separated list of target ontologies and/or value sets. "
          + "Example: 'ontologies=CEDARVS,NCIT'. By default, all BioPortal ontologies and value sets are considered. "
          + "The value of 'scope' overrides the list of sources specified using this parameter",
          required = false) @QueryParam("sources") String sources,
      @ApiParam(value = "Integer representing the page number. Default: 'page=1'", required = false) @QueryParam
          ("page") int page,
      @ApiParam(value = "Integer representing the size of the returned page. Default: 'pageSize=50'", required =
          false) @QueryParam("page_size") int pageSize) {
    //log.info("Received BioPortal search request");
    try {
      if (q.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
        return badRequest();
      }
      // Review and clean scope
      List<String> scopeList = new ArrayList<String>();
      List<String> referenceScopeList = Arrays
          .asList(BP_SEARCH_SCOPE_ALL, BP_SEARCH_SCOPE_CLASSES, BP_SEARCH_SCOPE_VALUE_SETS, BP_SEARCH_SCOPE_VALUES);
      for (String s : Arrays.asList(scope.split("\\s*,\\s*"))) {
        if (!referenceScopeList.contains(s)) {
          return badRequest("Wrong scope value(s)");
        } else {
          scopeList.add(s);
        }
      }
      // Sources list
      List<String> sourcesList = new ArrayList<String>();
      if (sources != null && sources.length() > 0) {
        sourcesList = Arrays.asList(sources.split("\\s*,\\s*"));
      }
      PagedResults results = termService.search(q, scopeList, sourcesList, page, pageSize, false, true,
          Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode) new ObjectMapper().valueToTree(results));

    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  /**
   * Ontologies
   **/

  @ApiOperation(
      value = "Find all ontologies (excluding value set collections)",
      //notes = "This call is not paged",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header")})
  // TODO: implement cache
  public static Result findAllOntologies(@ApiParam(value = "Include ontology details, such as the number of classes " +
      "and categories", required = true) @QueryParam("include_details") boolean includeDetails) {
    if (!Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      List<Ontology> ontologies = termService.findAllOntologies(includeDetails, Utils.getApiKeyFromHeader(request()));
      ObjectMapper mapper = new ObjectMapper();
      ObjectWriter writer = mapper.writerFor(new TypeReference<List<Ontology>>() {
      });
      return ok(mapper.readTree(writer.writeValueAsString(ontologies)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Find ontology by id. It returns ontology details, including number of classes and categories",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Ontology id. Examples: NCIT, OBI, FMA",
          required = true, dataType = "string", paramType = "path")})
  public static Result findOntology(String id, @ApiParam(value = "Include ontology details, such as the number of " +
      "classes and categories", required = true) @QueryParam("include_details") boolean includeDetails) {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      Ontology o = termService.findOntology(id, includeDetails, Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode) new ObjectMapper().valueToTree(o));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Get the root classes for a given ontology",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "ontology", value = "Ontology identifier. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result getRootClasses(String ontology) {
    if (ontology.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      List<OntologyClass> roots = termService.getRootClasses(ontology, Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode) new ObjectMapper().valueToTree(roots));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  /**
   * Classes
   **/

  @ApiOperation(
      value = "Create a provisional class",
      httpMethod = "POST")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Successful creation of a provisional class"),
      //@ApiResponse(code = 200, message = "Successful creation of a provisional class", response = OntologyClass
      // .class),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "ontology", value = "Ontology identifier. Example: CEDARPC", required = true, dataType
          = "string", paramType = "path"),
      @ApiImplicitParam(value = "Class to be created", required = true, dataType = "org.metadatacenter.terms" +
          ".domainObjects.OntologyClass", paramType = "body")})
  public static Result createClass(String ontology) {
    if (ontology.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    ObjectMapper mapper = new ObjectMapper();
    OntologyClass c = mapper.convertValue(request().body().asJson(), OntologyClass.class);
    c.setOntology(ontology);
    try {
      OntologyClass createdClass = termService.createProvisionalClass(c, Utils.getApiKeyFromHeader(request()));
      return created((JsonNode) mapper.valueToTree(createdClass));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Find class (either regular or provisional) by id and ontology",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result findClass(String id, String ontology) {
    if (id.isEmpty() || ontology.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      OntologyClass c = termService.findClass(Util.encodeIfNeeded(id), ontology, Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode) new ObjectMapper().valueToTree(c));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Get class tree (only for regular classes)",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result getClassTree(String id, String ontology) {
    if (id.isEmpty() || ontology.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      List<TreeNode> tree = termService.getClassTree(Util.encodeIfNeeded(id), ontology, Utils.getApiKeyFromHeader
          (request()));
      return ok((JsonNode) new ObjectMapper().valueToTree(tree));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Get class children (only for regular classes)",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result getClassChildren(String id, String ontology) {
    if (id.isEmpty() || ontology.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      PagedResults<OntologyClass> children = termService.getClassChildren(Util.encodeIfNeeded(id), ontology, Utils
          .getApiKeyFromHeader(request()));
      return ok((JsonNode) new ObjectMapper().valueToTree(children));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Get class parents (only for regular classes)",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result getClassParents(String id, String ontology) {
    if (id.isEmpty() || ontology.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      List<OntologyClass> parents = termService.getClassParents(Util.encodeIfNeeded(id), ontology, Utils
          .getApiKeyFromHeader(request()));
      return ok((JsonNode) new ObjectMapper().valueToTree(parents));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Get all provisional classes (including provisional value sets and provisional values)",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header")})
  public static Result getAllProvisionalClasses() {
    if (!Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      List<OntologyClass> classes = termService
          .findAllProvisionalClasses(null, Utils.getApiKeyFromHeader(request()));
      ObjectMapper mapper = new ObjectMapper();
      // This line ensures that @class type annotations are included for each element in the list
      ObjectWriter writer = mapper.writerFor(new TypeReference<List<OntologyClass>>() {
      });
      return ok(mapper.readTree(writer.writeValueAsString(classes)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Get all provisional classes for a specific ontology (including provisional value sets and provisional values)",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result getAllProvisionalClassesForOntology(String ontology) {
    if (ontology.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      List<OntologyClass> classes = termService
          .findAllProvisionalClasses(ontology, Utils.getApiKeyFromHeader(request()));
      ObjectMapper mapper = new ObjectMapper();
      // This line ensures that @class type annotations are included for each element in the list
      ObjectWriter writer = mapper.writerFor(new TypeReference<List<OntologyClass>>() {
      });
      return ok(mapper.readTree(writer.writeValueAsString(classes)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Update a provisional class",
      httpMethod = "PATCH")
  @ApiResponses(value = {
      @ApiResponse(code = 204, message = "Success! (No Content)"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Provisional class id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(value = "Updated information for the class", required = true, dataType = "org.metadatacenter" +
          ".terms" +
          ".domainObjects.OntologyClass", paramType = "body")})
  public static Result updateClass(String id) {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    ObjectMapper mapper = new ObjectMapper();
    OntologyClass c = mapper.convertValue(request().body().asJson(), OntologyClass.class);
    c.setId(id);
    try {
      termService.updateProvisionalClass(c, Utils.getApiKeyFromHeader(request()));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
    return internalServerError();
  }

  @ApiOperation(
      value = "Delete a provisional class",
      httpMethod = "DELETE")
  @ApiResponses(value = {
      @ApiResponse(code = 204, message = "Success! (No Content)"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Provisional class id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result deleteClass(String id) {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      termService.deleteProvisionalClass(id, Utils.getApiKeyFromHeader(request()));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
    return internalServerError();
  }

  /**
   * Relations
   **/

  @ApiOperation(
      value = "Create a provisional relation",
      httpMethod = "POST")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Successful creation of a provisional relation"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(value = "Relation to be created", required = true, dataType = "org.metadatacenter.terms" +
          ".domainObjects.Relation", paramType = "body")})
  public static Result createRelation() {
    if (!Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    ObjectMapper mapper = new ObjectMapper();
    Relation r = mapper.convertValue(request().body().asJson(), Relation.class);
    try {
      Relation createdRelation = termService.createProvisionalRelation(r, Utils.getApiKeyFromHeader(request()));
      return created((JsonNode) mapper.valueToTree(createdRelation));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Find provisional relation by id",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Provisional relation id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result findRelation(String id) {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      Relation r = termService.findProvisionalRelation(id, Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode) new ObjectMapper().valueToTree(r));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

//  @ApiOperation(
//      value = "Update a provisional relation",
//      httpMethod = "PATCH")
//  @ApiResponses(value = {
//      @ApiResponse(code = 204, message = "Success! (No Content)"),
//      @ApiResponse(code = 400, message = "Bad Request"),
//      @ApiResponse(code = 401, message = "Unauthorized"),
//      @ApiResponse(code = 404, message = "Not Found"),
//      @ApiResponse(code = 500, message = "Internal Server Error")})
//  @ApiImplicitParams(value = {
//      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
//          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
//          required = true, dataType = "string", paramType = "header"),
//      @ApiImplicitParam(name = "id", value = "Provisional relation id. Example: 720f50f0-ae6f-0133-848f-005056010073",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(value = "Updated information for the relation", required = true, dataType = "org
// .metadatacenter.terms" +
//          ".domainObjects.Relation", paramType = "body")})
//  public static Result updateProvisionalRelation(String id)
//  {
//    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request()))
//      return badRequest();
//    ObjectMapper mapper = new ObjectMapper();
//    Relation r = mapper.convertValue(request().body().asJson(), Relation.class);
//    r.setId(id);
//    try {
//      termService.updateProvisionalRelation(r, Utils.getApiKeyFromHeader(request()));
//    } catch (HTTPException e) {
//      return Results.status(e.getStatusCode());
//    } catch (IOException e) {
//      return internalServerError(e.getMessage());
//    }
//    return internalServerError();
//  }

  @ApiOperation(
      value = "Delete a provisional relation",
      httpMethod = "DELETE")
  @ApiResponses(value = {
      @ApiResponse(code = 204, message = "Success! (No Content)"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Provisional relation id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result deleteRelation(String id) {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      termService.deleteProvisionalRelation(id, Utils.getApiKeyFromHeader(request()));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
    return internalServerError();
  }

  /**
   * Value Set Collections
   */

  @ApiOperation(
      value = "Find all value set collections",
      //notes = "This call is not paged",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header")})
  public static Result findAllVSCollections(@ApiParam(value = "Includes details, such as the number of value sets " +
      "and categories", required = true) @QueryParam("include_details") boolean includeDetails) {
    if (!Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      List<VSCollection> vsCollections = termService.findAllVSCollections(includeDetails, Utils.getApiKeyFromHeader
          (request()));
      ObjectMapper mapper = new ObjectMapper();
      ObjectWriter writer = mapper.writerFor(new TypeReference<List<VSCollection>>() {
      });
      return ok(mapper.readTree(writer.writeValueAsString(vsCollections)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  /**
   * Value Sets
   **/

  @ApiOperation(
      value = "Create a provisional value set",
      httpMethod = "POST")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Successful creation of a provisional value set"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS", required = true,
          dataType = "string", paramType = "path"),
      @ApiImplicitParam(value = "Value set to be created", required = true, dataType = "org.metadatacenter.terms" +
          ".domainObjects.ValueSet", paramType = "body")})
  public static Result createValueSet(String vsCollection) {
    if (vsCollection.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    ObjectMapper mapper = new ObjectMapper();
    ValueSet vs = mapper.convertValue(request().body().asJson(), ValueSet.class);
    vs.setVsCollection(vsCollection);
    try {
      ValueSet createdVs = termService.createProvisionalValueSet(vs, Utils.getApiKeyFromHeader(request()));
      return created((JsonNode) mapper.valueToTree(createdVs));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Find provisional value set by id (either provisional or regular)",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "id", value = "Provisional value set id. Example: af033050-b04b-0133-981f-005056010074",
          required = true, dataType = "string", paramType = "path")})
  public static Result findValueSet(String id, String vsCollection) {
    if (vsCollection.isEmpty() || id.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      ValueSet c = termService.findValueSet(id, vsCollection, Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode) new ObjectMapper().valueToTree(c));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Find all value sets in a value set collection",
      // notes = ...
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path")})
  public static Result findValueSetsByVsCollection(String vsCollection) {
    if (!Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    if ((vsCollection == null) || (vsCollection.length() == 0)) {
      return badRequest();
    }
    try {
      PagedResults<ValueSet> valueSets = termService.
          findValueSetsByVsCollection(vsCollection, Utils.getApiKeyFromHeader(request()));
      ObjectMapper mapper = new ObjectMapper();
      // This line ensures that @class type annotations are included for each element in the collection
      ObjectWriter writer = mapper.writerFor(new TypeReference<PagedResults<Value>>() {
      });
      return ok(mapper.readTree(writer.writeValueAsString(valueSets)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Update a provisional value set",
      httpMethod = "PATCH")
  @ApiResponses(value = {
      @ApiResponse(code = 204, message = "Success! (No Content)"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Provisional value set id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(value = "Updated information for the value set", required = true, dataType = "org" +
          ".metadatacenter.terms" +
          ".domainObjects.ValueSet", paramType = "body")})
  public static Result updateValueSet(String id) {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    ObjectMapper mapper = new ObjectMapper();
    ValueSet vs = mapper.convertValue(request().body().asJson(), ValueSet.class);
    vs.setId(id);
    try {
      termService.updateProvisionalValueSet(vs, Utils.getApiKeyFromHeader(request()));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
    return internalServerError();
  }

  @ApiOperation(
      value = "Delete a provisional value set",
      httpMethod = "DELETE")
  @ApiResponses(value = {
      @ApiResponse(code = 204, message = "Success! (No Content)"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Provisional value set id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result deleteValueSet(String id) {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      termService.deleteProvisionalValueSet(id, Utils.getApiKeyFromHeader(request()));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
    return internalServerError();
  }

  @ApiOperation(
      value = "Find all values in a value set (regular or provisional)",
      // notes = ...
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "vs", value = "Value set identifier. Example: http%3A%2F%2Fwww.semanticweb" +
          ".org%2Fjgraybeal%2Fontologies%2F2015%2F7%2Fcedarvaluesets%23Study_File_Type",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path")})
  public static Result findValuesByValueSet(String vsId, String vsCollection) {
    if (!Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    if ((vsId == null) || (vsId.length() == 0) || (vsCollection == null) || (vsCollection.length() == 0)) {
      return badRequest();
    }
    try {
      vsId = Utils.encodeIfNeeded(vsId);
      PagedResults<Value> values = termService.findValuesByValueSet(vsId, vsCollection, Utils.getApiKeyFromHeader
          (request()));
      ObjectMapper mapper = new ObjectMapper();
      // This line ensures that @class type annotations are included for each element in the collection
      ObjectWriter writer = mapper.writerFor(new TypeReference<PagedResults<Value>>() {
      });
      return ok(mapper.readTree(writer.writeValueAsString(values)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  /**
   * Values
   **/

  @ApiOperation(
      value = "Create a value",
      httpMethod = "POST")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Successful creation of a provisional value"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "vs", value = "Value set identifier. Example: http%3A%2F%2Fwww.semanticweb" +
          ".org%2Fjgraybeal%2Fontologies%2F2015%2F7%2Fcedarvaluesets%23Study_File_Type",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(value = "Value to be created", required = true, dataType = "org.metadatacenter.terms" +
          ".domainObjects.Value", paramType = "body")})
  public static Result createValue(String vsCollection, String vs) {
    if (vsCollection.isEmpty() || vs.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      Value v = mapper.convertValue(request().body().asJson(), Value.class);
      v.setVsCollection(vsCollection);
      v.setVsId(vs);
      Value createdValue = termService.createProvisionalValue(v, Utils.getApiKeyFromHeader(request()));
      return created((JsonNode) mapper.valueToTree(createdValue));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Find value by id",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "vs", value = "Value set identifier. Example: http%3A%2F%2Fwww.semanticweb" +
          ".org%2Fjgraybeal%2Fontologies%2F2015%2F7%2Fcedarvaluesets%23Study_File_Type",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "id", value = "Value id. Example: 42f22880-b04b-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result findValue(String id, String vsCollection) {
    if (id.isEmpty() || vsCollection.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      Value c = termService.findValue(id, vsCollection, Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode) new ObjectMapper().valueToTree(c));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Update a provisional value",
      httpMethod = "PATCH")
  @ApiResponses(value = {
      @ApiResponse(code = 204, message = "Success! (No Content)"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Provisional value id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(value = "Updated information for the value", required = true, dataType = "org.metadatacenter" +
          ".terms" +
          ".domainObjects.OntologyClass", paramType = "body")})
  public static Result updateValue(String id) {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    ObjectMapper mapper = new ObjectMapper();
    Value v = mapper.convertValue(request().body().asJson(), Value.class);
    v.setId(id);
    try {
      termService.updateProvisionalValue(v, Utils.getApiKeyFromHeader(request()));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
    return internalServerError();
  }

  @ApiOperation(
      value = "Delete a provisional value",
      httpMethod = "DELETE")
  @ApiResponses(value = {
      @ApiResponse(code = 204, message = "Success! (No Content)"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Provisional value id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result deleteValue(String id) {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request())) {
      return badRequest();
    }
    try {
      termService.deleteProvisionalValue(id, Utils.getApiKeyFromHeader(request()));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IllegalArgumentException e) {
      return badRequest();
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
    return internalServerError();
  }

}
