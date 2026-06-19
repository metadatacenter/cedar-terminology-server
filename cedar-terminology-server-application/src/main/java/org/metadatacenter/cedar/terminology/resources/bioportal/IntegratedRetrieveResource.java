package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel.IntegratedRetrieveResults;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.IntegratedRetrieveBody;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.util.json.JsonMapper;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/bioportal", tags = "Classes", authorizations = {@Authorization("api_key")})
public class IntegratedRetrieveResource extends AbstractTerminologyServerResource {

  public IntegratedRetrieveResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  /**
   * Get all values for a specified value constraint
   */
  @POST
  @Timed
  @Path("/integrated-retrieve")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Retrieve classes and values based on CEDAR value constraints",
      notes = "Retrieve ontology classes, value sets, and values based on CEDAR value constraints. This endpoint " +
          "takes a controlled term field specification and any user-supplied initial characters and returns " +
          "conforming values. <br /> <br /> Some sample calls in Insomnia (https://insomnia.rest/) format are " +
          "available at https://github.com/metadatacenter/cedar-util/blob/master/api-calls/" +
          "CEDAR_Insomnia_API_calls.json. <br /> <br />Note that in some cases, the server will need to sort the " +
          "results obtained from BioPortal and the original pagination information will not be valid any more. In " +
          "those situations, the values of some of the pagination fields returned as part of the results (e.g., " +
          "pageCount, nextPage, etc.) cannot be computed consistently, and the server will assign a 'null' value to " +
          "those fields.",
      response = IntegratedRetrieveResults.class, responseContainer = "List",
      tags = {"Classes", "Value sets", "Values"})
  @ApiImplicitParams({
      @ApiImplicitParam(name = "request body", value = "Object that encapsulates the information needed to run the " +
          "search query. The \"valueConstraints\" field specification is based on CEDAR's \"_valueConstraints\" " +
          "field. See https://more.metadatacenter.org/tools-training/outreach/cedar-template-model for more details.",
          required = true,
          dataType = "org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel.IntegratedRetrieveRequestBody",
          paramType = "body")
  })
  @ApiResponses({
      @ApiResponse(code = 200, message = "A paginated list of search results",
          response = IntegratedRetrieveResults.class, responseContainer = "List"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response cedarIntegratedRetrieve(@Valid IntegratedRetrieveBody body) throws CedarException {

    //TODO
    // CedarRequestContext c = buildRequestContext();
    // c.must(c.user()).be(LoggedIn);

    try {
      int page = extractPage(body);
      int pageSize = extractPageSize(body);

      PagedResults results =
        terminologyService.integratedRetrieve(body.getValueConstraints(), page, pageSize, apiKey);

      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(results)).build();

    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException /*| ExecutionException*/ e) {
      throw new CedarAssertionException(e);
    }
  }

  public int extractPage(IntegratedRetrieveBody body) {
    int page = body.getPage();
    // If page not defined or invalid, set it to the first page
    if (page <= 0) {
      page = 1;
    }
    return page;
  }

  public int extractPageSize(IntegratedRetrieveBody body) {
    int pageSize = body.getPageSize();
    // If pageSize not defined or invalid, set it to the default value
    if (pageSize <= 0) {
      pageSize = defaultPageSize;
    }
    return pageSize;
  }
}
