package controllers;

import cache.Cache;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.wordnik.swagger.annotations.*;
import org.metadatacenter.terms.TerminologyService;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;
import org.metadatacenter.terms.util.Util;
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
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.terms.util.Constants.*;

@Api(value = "/bioportal", description = "BioPortal operations")
public class TerminologyController extends Controller {

  public static final TerminologyService termService;
  public static String apiKey;

  static {
    termService = new TerminologyService(
        Application.config.getString("bioportal.apiBasePath"),
        Application.config.getInt("bioportal.connectTimeout"),
        Application.config.getInt("bioportal.socketTimeout"));
    apiKey = Application.config.getString("bioportal.apiKeys.cedar");
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
  public static Result search(
      @ApiParam(value = "Search query. Example: 'melanoma'", required = true) @QueryParam("q") String q,
      @ApiParam(value = "Comma-separated list of search scopes. Accepted values={all,classes,value_sets,values}. "
          + "Default: 'scope=all'", required = false) @QueryParam("scope") String scope,
      @ApiParam(value = "Comma-separated list of target ontologies and/or value sets. "
          + "Example: 'ontologies=CEDARVS,NCIT'. By default, all BioPortal ontologies and value sets are considered. "
          + "The value of 'scope' overrides the list of sources specified using this parameter",
          required = false) @QueryParam("sources") String sources,
      @ApiParam(value = "Will perform a search specifically geared towards type-ahead suggestions. Default: false",
          required = false) @QueryParam
          ("suggest") boolean suggest,
      @ApiParam(value = "Ontology for which the subtree search will be performed", required = false) @QueryParam
          ("source") String source,
      @ApiParam(value = "Limits the search to a specific ontology branch", required = false) @QueryParam
          ("subtree_root_id") String subtreeRootId,
      @ApiParam(value = "Max depth of subtree. Default: 1", required = false) @QueryParam
          ("max_depth") int maxDepth,
      @ApiParam(value = "Integer representing the page number. Default: 'page=1'", required = false) @QueryParam
          ("page") int page,
      @ApiParam(value = "Integer representing the size of the returned page. Default: 'pageSize=50'", required =
          false) @QueryParam("page_size") int pageSize) {
    //log.info("Received BioPortal search request");
    try {
      if (q.isEmpty()) {
        return badRequest();
      }
      if (subtreeRootId.isEmpty()) {
        subtreeRootId = null;
      }
      if (source.isEmpty()) {
        source = null;
      } else {
        if (subtreeRootId == null) {
          return badRequest();
        }
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
      List<String> valueSetsIds = new ArrayList<>(Cache.valueSetsCache.get("value-sets").keySet());
      // TODO: The valueSetsIds parameter is passed to the service to avoid making additional calls to BioPortal.
      // These ids
      // are used to know if a particular result returned by BioPortal is a value or a value set. BioPortal should
      // provide this information and this parameter should be removed
      PagedResults results = termService.search(q, scopeList, sourcesList, suggest, source, subtreeRootId, maxDepth,
          page, pageSize, false, true, apiKey, valueSetsIds);
      return ok((JsonNode) new ObjectMapper().valueToTree(results));

    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    } catch (ExecutionException e) {
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
  public static Result findAllOntologies() {
    try {
      List<Ontology> ontologies = new ArrayList<>(Cache.ontologiesCache.get("ontologies").values());
      ObjectMapper mapper = new ObjectMapper();
      ObjectWriter writer = mapper.writerFor(new TypeReference<List<Ontology>>() {
      });
      return ok(mapper.readTree(writer.writeValueAsString(ontologies)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    } catch (ExecutionException e) {
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
      @ApiImplicitParam(name = "id", value = "Ontology id. Examples: NCIT, OBI, FMA",
          required = true, dataType = "string", paramType = "path")})
  public static Result findOntology(String id) {
    if (id.isEmpty()) {
      return badRequest();
    }
    try {
      Cache.apiKeyCache = apiKey;
      // Retrieve ontology from ontologies cache
      Ontology o = Cache.ontologiesCache.get("ontologies").get(id);
      if (o == null) {
        // Not found
        return notFound();
      }
      return ok((JsonNode) new ObjectMapper().valueToTree(o));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (ExecutionException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Get the root classes for a given ontology. If the ontology is CEDARPC, all provisional classes in this" +
          " ontology will be returned",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "ontology", value = "Ontology identifier. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result findRootClasses(String ontology) {
    if (ontology.isEmpty()) {
      return badRequest();
    }
    try {
      boolean isFlat = Cache.ontologiesCache.get("ontologies").get(ontology).getIsFlat();
      List<OntologyClass> roots = termService.getRootClasses(ontology, isFlat, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(roots));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    } catch (ExecutionException e) {
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
      @ApiImplicitParam(name = "ontology", value = "Ontology identifier. Example: CEDARPC", required = true, dataType
          = "string", paramType = "path"),
      @ApiImplicitParam(value = "Class to be created", required = true, dataType = "org.metadatacenter.terms" +
          ".domainObjects.OntologyClass", paramType = "body")})
  public static Result createClass(String ontology) {
    if (ontology.isEmpty()) {
      return badRequest();
    }
    ObjectMapper mapper = new ObjectMapper();
    OntologyClass c = mapper.convertValue(request().body().asJson(), OntologyClass.class);
    c.setOntology(ontology);
    try {
      OntologyClass createdClass = termService.createProvisionalClass(c, apiKey);
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
      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result findClass(String id, String ontology) {
    if (id.isEmpty() || ontology.isEmpty()) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      OntologyClass c = termService.findClass(Util.encodeIfNeeded(id), ontology, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(c));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
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
      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result findClassTree(String id, String ontology) {
    if (id.isEmpty() || ontology.isEmpty()) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      boolean isFlat = Cache.ontologiesCache.get("ontologies").get(ontology).getIsFlat();
      List<TreeNode> tree = termService.getClassTree(Util.encodeIfNeeded(id), ontology, isFlat, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(tree));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    } catch (ExecutionException e) {
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
      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result findClassChildren(String id, String ontology, @ApiParam(value = "Integer representing the " +
      "page number. Default: 'page=1'", required = false) @QueryParam("page") int page, @ApiParam(value = "Integer " +
      "representing the size of the returned page. Default: 'pageSize=50'", required = false) @QueryParam
      ("page_size") int pageSize) {
    if (id.isEmpty() || ontology.isEmpty()) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      PagedResults<OntologyClass> children = termService.getClassChildren(Util.encodeIfNeeded(id), ontology, page,
          pageSize, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(children));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Find descendants of a given class",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result findClassDescendants(String id, String ontology, @ApiParam(value = "Integer representing the " +
      "page number. Default: 'page=1'", required = false) @QueryParam("page") int page, @ApiParam(value = "Integer " +
      "representing the size of the returned page. Default: 'pageSize=50'", required = false) @QueryParam
      ("page_size") int pageSize) {
    if (id.isEmpty() || ontology.isEmpty()) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      PagedResults<OntologyClass> descendants = termService.getClassDescendants(Util.encodeIfNeeded(id), ontology,
          page, pageSize, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(descendants));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
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
      @ApiImplicitParam(name = "id", value = "Class id. It must be encoded. Example: http%3A%2F%2Fdata.bioontology" +
          ".org%2Fprovisional_classes%2F4f82a7f0-bbba-0133-b23e-005056010074 (provisional), " +
          "http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23C3224 (regular) ",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result findClassParents(String id, String ontology) {
    if (id.isEmpty() || ontology.isEmpty()) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      List<OntologyClass> parents = termService.getClassParents(Util.encodeIfNeeded(id), ontology, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(parents));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
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
  public static Result findAllProvisionalClasses() {
    try {
      List<OntologyClass> classes = termService
          .findAllProvisionalClasses(null, apiKey);
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
      value = "Get all provisional classes for a specific ontology (including provisional value sets and provisional " +
          "values)",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "ontology", value = "Ontology. Example: NCIT",
          required = true, dataType = "string", paramType = "path")})
  public static Result findAllProvisionalClassesForOntology(String ontology) {
    if (ontology.isEmpty()) {
      return badRequest();
    }
    try {
      List<OntologyClass> classes = termService
          .findAllProvisionalClasses(ontology, apiKey);
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
      @ApiImplicitParam(name = "id", value = "Provisional class id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(value = "Updated information for the class", required = true, dataType = "org.metadatacenter" +
          ".terms" +
          ".domainObjects.OntologyClass", paramType = "body")})
  public static Result updateClass(String id) {
    if (id.isEmpty()) {
      return badRequest();
    }
    ObjectMapper mapper = new ObjectMapper();
    OntologyClass c = mapper.convertValue(request().body().asJson(), OntologyClass.class);
    c.setId(id);
    try {
      termService.updateProvisionalClass(c, apiKey);
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
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
      @ApiImplicitParam(name = "id", value = "Provisional class id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result deleteClass(String id) {
    if (id.isEmpty()) {
      return badRequest();
    }
    try {
      termService.deleteProvisionalClass(id, apiKey);
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
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
      @ApiImplicitParam(value = "Relation to be created", required = true, dataType = "org.metadatacenter.terms" +
          ".domainObjects.Relation", paramType = "body")})
  public static Result createRelation() {
    ObjectMapper mapper = new ObjectMapper();
    Relation r = mapper.convertValue(request().body().asJson(), Relation.class);
    try {
      Relation createdRelation = termService.createProvisionalRelation(r, apiKey);
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
      @ApiImplicitParam(name = "id", value = "Provisional relation id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result findRelation(String id) {
    if (id.isEmpty()) {
      return badRequest();
    }
    try {
      Relation r = termService.findProvisionalRelation(id, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(r));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
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
//      @ApiImplicitParam(name = "Authorization", value = "Format: apikey={your_bioportal_apikey}. "
//          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
//          required = true, dataType = "string", paramType = "header"),
//      @ApiImplicitParam(name = "id", value = "Provisional relation id. Example: 720f50f0-ae6f-0133-848f-005056010073",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(value = "Updated information for the relation", required = true, dataType = "org
// .metadatacenter.terms" +
//          ".domainObjects.Relation", paramType = "body")})
//  public static Result updateProvisionalRelation(String id)
//  {
//    if (id.isEmpty())
//      return badRequest();
//    ObjectMapper mapper = new ObjectMapper();
//    Relation r = mapper.convertValue(request().body().asJson(), Relation.class);
//    r.setId(id);
//    try {
//      termService.updateProvisionalRelation(r, apiKey);
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
      @ApiImplicitParam(name = "id", value = "Provisional relation id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result deleteRelation(String id) {
    if (id.isEmpty()) {
      return badRequest();
    }
    try {
      termService.deleteProvisionalRelation(id, apiKey);
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
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
  public static Result findAllVSCollections(@ApiParam(value = "Includes details, such as the number of value sets " +
      "and categories", required = true) @QueryParam("include_details") boolean includeDetails) {
    try {
      List<ValueSetCollection> vsCollections = termService.findAllVSCollections(includeDetails, apiKey);
      ObjectMapper mapper = new ObjectMapper();
      ObjectWriter writer = mapper.writerFor(new TypeReference<List<ValueSetCollection>>() {
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
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS", required = true,
          dataType = "string", paramType = "path"),
      @ApiImplicitParam(value = "Value set to be created", required = true, dataType = "org.metadatacenter.terms" +
          ".domainObjects.ValueSet", paramType = "body")})
  public static Result createValueSet(String vsCollection) {
    if (vsCollection.isEmpty()) {
      return badRequest();
    }
    ObjectMapper mapper = new ObjectMapper();
    ValueSet vs = mapper.convertValue(request().body().asJson(), ValueSet.class);
    vs.setVsCollection(vsCollection);
    try {
      ValueSet createdVs = termService.createProvisionalValueSet(vs, apiKey);
      // Refresh value sets cache
      Cache.valueSetsCache.refresh("value-sets");
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
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "id", value = "Provisional value set id. Example: af033050-b04b-0133-981f-005056010074",
          required = true, dataType = "string", paramType = "path")})
  public static Result findValueSet(String id, String vsCollection) {
    if (vsCollection.isEmpty() || id.isEmpty()) {
      return badRequest();
    }
    try {
      ValueSet c = termService.findValueSet(id, vsCollection, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(c));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
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
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path")})
  public static Result findValueSetsByVsCollection(String vsCollection, @ApiParam(value = "Integer representing the " +
      "page number. Default: 'page=1'", required = false) @QueryParam("page") int page, @ApiParam(value = "Integer " +
      "representing the size of the returned page. Default: 'pageSize=50'", required = false) @QueryParam
      ("page_size") int pageSize) {
    if ((vsCollection == null) || (vsCollection.length() == 0)) {
      return badRequest();
    }
    try {
      PagedResults<ValueSet> valueSets = termService.
          findValueSetsByVsCollection(vsCollection, page, pageSize, apiKey);
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
      value = "Find the value set that contains a particular value",
      // notes = ...
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "id", value = "Value id. Example: 42f22880-b04b-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path")})
  public static Result findValueSetByValue(String id, String vsCollection) {
    if (id.isEmpty() || vsCollection.isEmpty()) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      ValueSet vs = termService.findValueSetByValue(id, vsCollection, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(vs));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Get value set tree",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "id", value = "Value set id. It must be encoded",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path")})
  public static Result findValueSetTree(String id, String vsCollection) {
    if (id.isEmpty() || vsCollection.isEmpty()) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      TreeNode tree = termService.getValueSetTree(Util.encodeIfNeeded(id), vsCollection, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(tree));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Find all value sets",
      // notes = ...
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  public static Result findAllValueSets() {
    try {
      List<ValueSet> valueSets = new ArrayList<ValueSet>(Cache.valueSetsCache.get("value-sets").values());
      ObjectMapper mapper = new ObjectMapper();
      // This line ensures that @class type annotations are included for each element in the collection
      ObjectWriter writer = mapper.writerFor(new TypeReference<List<ValueSet>>() {
      });
      return ok(mapper.readTree(writer.writeValueAsString(valueSets)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    } catch (ExecutionException e) {
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
      @ApiImplicitParam(name = "id", value = "Provisional value set id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(value = "Updated information for the value set", required = true, dataType = "org" +
          ".metadatacenter.terms" +
          ".domainObjects.ValueSet", paramType = "body")})
  public static Result updateValueSet(String id) {
    if (id.isEmpty()) {
      return badRequest();
    }
    ObjectMapper mapper = new ObjectMapper();
    ValueSet vs = mapper.convertValue(request().body().asJson(), ValueSet.class);
    vs.setId(id);
    try {
      termService.updateProvisionalValueSet(vs, apiKey);
    } catch (HTTPException e) {
      if (e.getStatusCode() == 204) {
        // Refresh value sets cache
        Cache.valueSetsCache.refresh("value-sets");
      }
      return Results.status(e.getStatusCode());
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
      @ApiImplicitParam(name = "id", value = "Provisional value set id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result deleteValueSet(String id) {
    if (id.isEmpty()) {
      return badRequest();
    }
    try {
      termService.deleteProvisionalValueSet(id, apiKey);
    } catch (HTTPException e) {
      if (e.getStatusCode() == 204) {
        // Refresh value sets cache
        Cache.valueSetsCache.refresh("value-sets");
      }
      return Results.status(e.getStatusCode());
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
      @ApiImplicitParam(name = "vs", value = "Value set identifier. Example: http%3A%2F%2Fwww.semanticweb" +
          ".org%2Fjgraybeal%2Fontologies%2F2015%2F7%2Fcedarvaluesets%23Study_File_Type",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path")})
  public static Result findValuesByValueSet(String vsId, String vsCollection, @ApiParam(value = "Integer representing" +
      " the " +
      "page number. Default: 'page=1'", required = false) @QueryParam("page") int page, @ApiParam(value = "Integer " +
      "representing the size of the returned page. Default: 'pageSize=50'", required = false) @QueryParam
      ("page_size") int pageSize) {
    if ((vsId == null) || (vsId.length() == 0) || (vsCollection == null) || (vsCollection.length() == 0)) {
      return badRequest();
    }
    try {
      vsId = Utils.encodeIfNeeded(vsId);
      PagedResults<Value> values = termService.findValuesByValueSet(vsId, vsCollection, page, pageSize, apiKey);
      ObjectMapper mapper = new ObjectMapper();
      // This line ensures that @class type annotations are included for each element in the collection
      ObjectWriter writer = mapper.writerFor(new TypeReference<PagedResults<Value>>() {
      });
      return ok(mapper.readTree(writer.writeValueAsString(values)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
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
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "vs", value = "Value set identifier. Example: http%3A%2F%2Fwww.semanticweb" +
          ".org%2Fjgraybeal%2Fontologies%2F2015%2F7%2Fcedarvaluesets%23Study_File_Type",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(value = "Value to be created", required = true, dataType = "org.metadatacenter.terms" +
          ".domainObjects.Value", paramType = "body")})
  public static Result createValue(String vsCollection, String vs) {
    if (vsCollection.isEmpty() || vs.isEmpty()) {
      return badRequest();
    }
    try {
      ObjectMapper mapper = new ObjectMapper();
      Value v = mapper.convertValue(request().body().asJson(), Value.class);
      v.setVsCollection(vsCollection);
      v.setVsId(vs);
      Value createdValue = termService.createProvisionalValue(v, apiKey);
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
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "vs", value = "Value set identifier. Example: http%3A%2F%2Fwww.semanticweb" +
          ".org%2Fjgraybeal%2Fontologies%2F2015%2F7%2Fcedarvaluesets%23Study_File_Type",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "id", value = "Value id. Example: 42f22880-b04b-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result findValue(String id, String vsCollection) {
    if (id.isEmpty() || vsCollection.isEmpty()) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      Value c = termService.findValue(id, vsCollection, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(c));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Get value tree (only for regular classes)",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 404, message = "Not Found"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "id", value = "Value id. It must be encoded",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path")})
  public static Result findValueTree(String id, String vsCollection) {
    if (id.isEmpty() || vsCollection.isEmpty()) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      TreeNode tree = termService.getValueTree(Util.encodeIfNeeded(id), vsCollection, apiKey);
      return ok((JsonNode) new ObjectMapper().valueToTree(tree));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
      value = "Find all values in the value set that the given value belongs to",
      httpMethod = "GET")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Success!"),
      @ApiResponse(code = 400, message = "Bad Request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
      @ApiImplicitParam(name = "vs_collection", value = "Value set collection. Example: CEDARVS",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "vs", value = "Value set identifier. Example: http%3A%2F%2Fwww.semanticweb" +
          ".org%2Fjgraybeal%2Fontologies%2F2015%2F7%2Fcedarvaluesets%23Study_File_Type",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(name = "id", value = "Value id. Example: 42f22880-b04b-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result findAllValuesInValueSetByValue(String id, String vsCollection, @ApiParam(value = "Integer " +
      "representing the page number. Default: 'page=1'", required = false) @QueryParam("page") int page, @ApiParam(value = "Integer " +
      "representing the size of the returned page. Default: 'pageSize=50'", required = false) @QueryParam
      ("page_size") int pageSize) {
    if (id.isEmpty() || vsCollection.isEmpty()) {
      return badRequest();
    }
    try {
      id = Util.encodeIfNeeded(id);
      PagedResults<Value> values = termService.findAllValuesInValueSetByValue(id, vsCollection, page, pageSize, apiKey);
      ObjectMapper mapper = new ObjectMapper();
      // This line ensures that @class type annotations are included for each element in the collection
      ObjectWriter writer = mapper.writerFor(new TypeReference<PagedResults<Value>>() {});
      return ok(mapper.readTree(writer.writeValueAsString(values)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
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
      @ApiImplicitParam(name = "id", value = "Provisional value id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path"),
      @ApiImplicitParam(value = "Updated information for the value", required = true, dataType = "org.metadatacenter" +
          ".terms" +
          ".domainObjects.OntologyClass", paramType = "body")})
  public static Result updateValue(String id) {
    if (id.isEmpty()) {
      return badRequest();
    }
    ObjectMapper mapper = new ObjectMapper();
    Value v = mapper.convertValue(request().body().asJson(), Value.class);
    v.setId(id);
    try {
      termService.updateProvisionalValue(v, apiKey);
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
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
      @ApiImplicitParam(name = "id", value = "Provisional value id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result deleteValue(String id) {
    if (id.isEmpty()) {
      return badRequest();
    }
    try {
      termService.deleteProvisionalValue(id, apiKey);
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
    return internalServerError();
  }

}
