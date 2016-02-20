package controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.wordnik.swagger.annotations.*;
import org.metadatacenter.terms.TerminologyService;
import org.metadatacenter.terms.customObjects.SearchResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.terms.domainObjects.ValueSet;
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
public class TerminologyController extends Controller
{
  private static Logger log = LoggerFactory.getLogger(TerminologyController.class);

  public static final TerminologyService termService;

  static {
    Configuration config = Play.application().configuration();
    termService = new TerminologyService(config.getInt("bioportal.connectTimeout"), config.getInt("bioportal.socketTimeout"));
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
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header")})

  public static Result search(
    @ApiParam(value = "Search query. Example: 'melanoma'", required = true) @QueryParam("q") String q,
    @ApiParam(value = "Comma-separated list of search scopes. Accepted values={all,classes,value_sets,values}. "
      + "Default: 'scope=all'", required = false) @QueryParam("scope") String scope,
    @ApiParam(value = "Comma-separated list of target ontologies and/or value sets. "
    + "Example: 'ontologies=CEDARVS,NCIT'. By default, all BioPortal ontologies and value sets are considered. "
    + "The value of 'scope' overrides the list of sources specified using this parameter",
    required=false) @QueryParam("sources") String sources,
    @ApiParam(value = "Integer representing the page number. Default: 'page=1'", required = false) @QueryParam("page") int page,
    @ApiParam(value = "Integer representing the size of the returned page. Default: 'pageSize=50'", required = false) @QueryParam("page_size") int pageSize)
  {
    //log.info("Received BioPortal search request");
    try {
      if (q.isEmpty() || !Utils.isValidAuthorizationHeader(request()))
        return badRequest();
      // Review and clean scope
      List<String> scopeList = new ArrayList<String>();
      List<String> referenceScopeList = Arrays
        .asList(BP_SEARCH_SCOPE_ALL, BP_SEARCH_SCOPE_CLASSES, BP_SEARCH_SCOPE_VALUE_SETS, BP_SEARCH_SCOPE_VALUES);
      for (String s : Arrays.asList(scope.split("\\s*,\\s*"))) {
        if (!referenceScopeList.contains(s))
          return badRequest("Wrong scope value(s)");
        else
          scopeList.add(s);
      }
      // Sources list
      List<String> sourcesList = new ArrayList<String>();
      if (sources != null && sources.length()>0)
        sourcesList = Arrays.asList(sources.split("\\s*,\\s*"));
      SearchResults results = termService.search(q, scopeList, sourcesList, page, pageSize, false, true,
        Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode)new ObjectMapper().valueToTree(results));

    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  /** Classes **/

  @ApiOperation(
    value = "Create a provisional class",
    httpMethod = "POST")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "Successful creation of a provisional class"),
    //@ApiResponse(code = 200, message = "Successful creation of a provisional class", response = OntologyClass.class),
    @ApiResponse(code = 400, message = "Bad Request"),
    @ApiResponse(code = 401, message = "Unauthorized"),
    @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header"),
    @ApiImplicitParam(value="Class to be created", required = true, dataType = "org.metadatacenter.terms.domainObjects.OntologyClass", paramType = "body")})
  public static Result createProvisionalClass()
  {
    if (!Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    ObjectMapper mapper = new ObjectMapper();
    OntologyClass c = mapper.convertValue(request().body().asJson(), OntologyClass.class);
    try {
      OntologyClass createdClass = termService.createProvisionalClass(c, Utils.getApiKeyFromHeader(request()));
      return created((JsonNode)mapper.valueToTree(createdClass));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
    value = "Find provisional class by id",
    httpMethod = "GET")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "Success!"),
    @ApiResponse(code = 400, message = "Bad Request"),
    @ApiResponse(code = 401, message = "Unauthorized"),
    @ApiResponse(code = 404, message = "Not Found"),
    @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header"),
  @ApiImplicitParam(name = "id", value="Provisional class id. Example: 93966640-ab48-0133-d0ff-005056010088",
    required = true, dataType = "string", paramType = "path")})
  public static Result findProvisionalClass(String id)
  {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    try {
      OntologyClass c = termService.findProvisionalClass(id, Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode)new ObjectMapper().valueToTree(c));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
    value = "Find all provisional classes (including provisional value sets and provisional values)",
    //notes = "This call is not paged",
    httpMethod = "GET")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "Success!"),
    @ApiResponse(code = 400, message = "Bad Request"),
    @ApiResponse(code = 401, message = "Unauthorized"),
    @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header")})
  public static Result findAllProvisionalClasses(@ApiParam(value = "Ontology", required = false) @QueryParam("ontology") String ontology)
  {
    if (!Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    if (ontology.length() == 0)
      ontology = null;
    try {
      List<OntologyClass> classes = termService
        .findAllProvisionalClasses(ontology, Utils.getApiKeyFromHeader(request()));
      ObjectMapper mapper = new ObjectMapper();
      // This line ensures that @class type annotations are included for each element in the list
      ObjectWriter writer = mapper.writerFor(new TypeReference<List<OntologyClass>>(){ });
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
      @ApiImplicitParam(value = "Updated information for the class", required = true, dataType = "org.metadatacenter.terms" +
          ".domainObjects.OntologyClass", paramType = "body")})
  public static Result updateProvisionalClass(String id)
  {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    ObjectMapper mapper = new ObjectMapper();
    OntologyClass c = mapper.convertValue(request().body().asJson(), OntologyClass.class);
    c.setId(id);
    try {
      termService.updateProvisionalClass(c, Utils.getApiKeyFromHeader(request()));
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
      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
          required = true, dataType = "string", paramType = "header"),
      @ApiImplicitParam(name = "id", value = "Provisional class id. Example: 720f50f0-ae6f-0133-848f-005056010073",
          required = true, dataType = "string", paramType = "path")})
  public static Result deleteProvisionalClass(String id)
  {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    try {
      termService.deleteProvisionalClass(id, Utils.getApiKeyFromHeader(request()));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
    return internalServerError();
  }

  /** Relations **/

  @ApiOperation(
    value = "Create a provisional relation",
    httpMethod = "POST")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "Successful creation of a provisional relation"),
    @ApiResponse(code = 400, message = "Bad Request"),
    @ApiResponse(code = 401, message = "Unauthorized"),
    @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header"),
    @ApiImplicitParam(value="Relation to be created", required = true, dataType = "org.metadatacenter.terms.domainObjects.Relation", paramType = "body")})
  public static Result createProvisionalRelation()
  {
    if (!Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    ObjectMapper mapper = new ObjectMapper();
    Relation r = mapper.convertValue(request().body().asJson(), Relation.class);
    try {
      Relation createdRelation = termService.createProvisionalRelation(r, Utils.getApiKeyFromHeader(request()));
      return created((JsonNode)mapper.valueToTree(createdRelation));
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
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header"),
    @ApiImplicitParam(name = "id", value="Provisional relation id. Example: 720f50f0-ae6f-0133-848f-005056010073",
      required = true, dataType = "string", paramType = "path")})
  public static Result findProvisionalRelation(String id)
  {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    try {
      Relation r = termService.findProvisionalRelation(id, Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode)new ObjectMapper().valueToTree(r));
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
//      @ApiImplicitParam(name = "Authorization", value = "Format: apikey token={your_bioportal_apikey}. "
//          + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
//          required = true, dataType = "string", paramType = "header"),
//      @ApiImplicitParam(name = "id", value = "Provisional relation id. Example: 720f50f0-ae6f-0133-848f-005056010073",
//          required = true, dataType = "string", paramType = "path"),
//      @ApiImplicitParam(value = "Updated information for the relation", required = true, dataType = "org.metadatacenter.terms" +
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
  public static Result deleteProvisionalRelation(String id)
  {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    try {
      termService.deleteProvisionalRelation(id, Utils.getApiKeyFromHeader(request()));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
    return internalServerError();
  }

  /** Value Sets **/

  @ApiOperation(
    value = "Create a provisional value set",
    httpMethod = "POST")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "Successful creation of a provisional value set"),
    @ApiResponse(code = 400, message = "Bad Request"),
    @ApiResponse(code = 401, message = "Unauthorized"),
    @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header"),
    @ApiImplicitParam(value="Value set to be created", required = true, dataType = "org.metadatacenter.terms.domainObjects.ValueSet", paramType = "body")})
  public static Result createProvisionalValueSet()
  {
    if (!Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    ObjectMapper mapper = new ObjectMapper();
    ValueSet vs = mapper.convertValue(request().body().asJson(), ValueSet.class);
    try {
      ValueSet createdVs = termService.createProvisionalValueSet(vs, Utils.getApiKeyFromHeader(request()));
      return created((JsonNode)mapper.valueToTree(createdVs));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
    value = "Find provisional value set by id",
    httpMethod = "GET")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "Success!"),
    @ApiResponse(code = 400, message = "Bad Request"),
    @ApiResponse(code = 401, message = "Unauthorized"),
    @ApiResponse(code = 404, message = "Not Found"),
    @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header"),
    @ApiImplicitParam(name = "id", value="Provisional value set id. Example: af033050-b04b-0133-981f-005056010074",
      required = true, dataType = "string", paramType = "path")})
  public static Result findProvisionalValueSet(String id)
  {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    try {
      ValueSet c = termService.findProvisionalValueSet(id, Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode)new ObjectMapper().valueToTree(c));
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
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header"),
    @ApiImplicitParam(name = "vs_collection", value="Value set collection. Example: CEDARVS",
      required = true, dataType = "string", paramType = "path")})
  public static Result findValueSetsByVsCollection(String vsCollection)
  {
    if (!Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    if ((vsCollection == null) || (vsCollection.length() == 0))
      return badRequest();
    try {
      SearchResults<ValueSet> valueSets = termService.
        findValueSetsByVsCollection(vsCollection, Utils.getApiKeyFromHeader(request()));
      ObjectMapper mapper = new ObjectMapper();
      // This line ensures that @class type annotations are included for each element in the collection
      ObjectWriter writer = mapper.writerFor(new TypeReference<SearchResults<Value>>(){ });
      return ok(mapper.readTree(writer.writeValueAsString(valueSets)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
    value = "Update provisional value set",
    notes = "TO BE IMPLEMENTED",
    httpMethod = "PUT")
  public static Result updateProvisionalValueSet()
  {
    //TODO:
    return null;
  }

  @ApiOperation(
    value = "Delete provisional value set",
    notes = "TO BE IMPLEMENTED",
    httpMethod = "DELETE")
  public static Result deleteProvisionalValueSet(String id)
  {
    //TODO
    return null;
  }


  @ApiOperation(
    value = "Find all values in a value set",
    // notes = ...
    httpMethod = "GET")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "Success!"),
    @ApiResponse(code = 400, message = "Bad Request"),
    @ApiResponse(code = 401, message = "Unauthorized"),
    @ApiResponse(code = 404, message = "Not Found"),
    @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header"),
    @ApiImplicitParam(name = "vs", value="Value set identifier",
      required = true, dataType = "string", paramType = "path"),
    @ApiImplicitParam(name = "vs_collection", value="Value set collection. Example: CEDARVS",
      required = true, dataType = "string", paramType = "path")})
  public static Result findValuesByValueSet(String vsId, String vsCollection)
  {
    if (!Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    if ((vsId == null) || (vsId.length() == 0) || (vsCollection == null) || (vsCollection.length() == 0)  )
      return badRequest();
    try {
      vsId = Utils.encodeIfNeeded(vsId);
      SearchResults<Value> values = termService.findValuesByValueSet(vsId, vsCollection, Utils.getApiKeyFromHeader(request()));
      ObjectMapper mapper = new ObjectMapper();
      // This line ensures that @class type annotations are included for each element in the collection
      ObjectWriter writer = mapper.writerFor(new TypeReference<SearchResults<Value>>(){ });
      return ok(mapper.readTree(writer.writeValueAsString(values)));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  /** Values **/

  @ApiOperation(
    value = "Create a provisional value",
    httpMethod = "POST")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "Successful creation of a provisional value"),
    @ApiResponse(code = 400, message = "Bad Request"),
    @ApiResponse(code = 401, message = "Unauthorized"),
    @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header"),
    @ApiImplicitParam(value="Value to be created", required = true, dataType = "org.metadatacenter.terms.domainObjects.Value", paramType = "body")})
  public static Result createProvisionalValue()
  {
    if (!Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    try {
      ObjectMapper mapper = new ObjectMapper();
      Value v = mapper.convertValue(request().body().asJson(), Value.class);
      Value createdValue = termService.createProvisionalValue(v, Utils.getApiKeyFromHeader(request()));
      return created((JsonNode)mapper.valueToTree(createdValue));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
    value = "Find provisional value by id",
    httpMethod = "GET")
  @ApiResponses(value = {
    @ApiResponse(code = 200, message = "Success!"),
    @ApiResponse(code = 400, message = "Bad Request"),
    @ApiResponse(code = 401, message = "Unauthorized"),
    @ApiResponse(code = 500, message = "Internal Server Error")})
  @ApiImplicitParams(value = {
    @ApiImplicitParam(name = "Authorization", value="Format: apikey token={your_bioportal_apikey}. "
      + "To obtain an API key, login to BioPortal and go to \"Account\" where your API key will be displayed",
      required = true, dataType = "string", paramType = "header"),
    @ApiImplicitParam(name = "id", value="Provisional value id. Example: 42f22880-b04b-0133-848f-005056010073",
      required = true, dataType = "string", paramType = "path")})
  public static Result findProvisionalValue(String id)
  {
    if (id.isEmpty() || !Utils.isValidAuthorizationHeader(request()))
      return badRequest();
    try {
      Value c = termService.findProvisionalValue(id, Utils.getApiKeyFromHeader(request()));
      return ok((JsonNode)new ObjectMapper().valueToTree(c));
    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }

  @ApiOperation(
    value = "Update provisional value",
    notes = "TO BE IMPLEMENTED",
    httpMethod = "PUT")
  public static Result updateProvisionalValue()
  {
    //TODO:
    return null;
  }

  @ApiOperation(
    value = "Delete provisional value",
    notes = "TO BE IMPLEMENTED",
    httpMethod = "DELETE")
  public static Result deleteProvisionalValue(String id)
  {
    //TODO
    return null;
  }

}
