package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiImplicitParam;
import com.wordnik.swagger.annotations.ApiImplicitParams;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.metadatacenter.terminology.services.BioPortalService;
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

import static org.metadatacenter.terminology.services.util.Constants.BP_SEARCH_SCOPE_ALL;
import static org.metadatacenter.terminology.services.util.Constants.BP_SEARCH_SCOPE_CLASSES;
import static org.metadatacenter.terminology.services.util.Constants.BP_SEARCH_SCOPE_VALUE_SETS;
import static org.metadatacenter.terminology.services.util.Constants.BP_SEARCH_SCOPE_VALUES;

@Api(value = "/bioportal", description = "BioPortal operations")
public class BioPortalController extends Controller
{
  private static Logger log = LoggerFactory.getLogger(BioPortalController.class);

  public static final BioPortalService bioPortalService;

  static {
    Configuration config = Play.application().configuration();
    bioPortalService = new BioPortalService(config.getInt("bioportal.connectTimeout"), config.getInt("bioportal.socketTimeout"));
  }

  /* General */
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
    @ApiParam(value = "Integer representing the size of the returned page. Default: 'pageSize=50'", required = false) @QueryParam("pagesize") int pageSize)
  {
    //log.info("Received BioPortal search request");
    try {
      if (q.isEmpty() || Utils.isValidAuthorizationHeader(request()) == false)
        return badRequest();
      // Review and clean scope
      List<String> scopeList = new ArrayList<String>();
      List<String> referenceScopeList = Arrays.asList(BP_SEARCH_SCOPE_ALL, BP_SEARCH_SCOPE_CLASSES,
        BP_SEARCH_SCOPE_VALUE_SETS, BP_SEARCH_SCOPE_VALUES);
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
      JsonNode result = bioPortalService.search(q, scopeList, sourcesList, page, pageSize, false, true,
        Utils.getApiKeyFromHeader(request()));
      return ok(result);

    } catch (HTTPException e) {
      return Results.status(e.getStatusCode());
    } catch (IOException e) {
      return internalServerError(e.getMessage());
    }
  }
}
