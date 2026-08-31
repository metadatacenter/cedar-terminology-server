package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel.IntegratedRetrieveResults;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.IntegratedRetrieveBody;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Classes")
@SecurityRequirement(name = "api_key")
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
  @Operation(summary = "Retrieve classes and values based on CEDAR value constraints", description = "Retrieve ontology classes, value sets, and values based on CEDAR value constraints. This endpoint " +
          "takes a controlled term field specification and any user-supplied initial characters and returns " +
          "conforming values. <br /> <br /> Some sample calls in Insomnia (https://insomnia.rest/) format are " +
          "available at https://github.com/metadatacenter/cedar-util/blob/master/api-calls/" +
          "CEDAR_Insomnia_API_calls.json. <br /> <br />Note that in some cases, the server will need to sort the " +
          "results obtained from BioPortal and the original pagination information will not be valid any more. In " +
          "those situations, the values of some of the pagination fields returned as part of the results (e.g., " +
          "pageCount, nextPage, etc.) cannot be computed consistently, and the server will assign a 'null' value to " +
          "those fields.", tags = {"Classes", "Value sets", "Values"})
  @RequestBody(description = "Object that encapsulates the information needed to run the " +
          "search query. The \"valueConstraints\" field specification is based on CEDAR's \"_valueConstraints\" " +
          "field. See https://more.metadatacenter.org/tools-training/outreach/cedar-template-model for more details.", required = true, content = @Content(schema = @Schema(implementation = org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel.IntegratedRetrieveRequestBody.class)))
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "A paginated list of search results", content = @Content(schema = @Schema(implementation = IntegratedRetrieveResults.class))),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response cedarIntegratedRetrieve(@Valid IntegratedRetrieveBody body) throws CedarException {

    // Anonymous by decision, not by omission. Third-party deployments of the embeddable editor reach
    // this route without a CEDAR session, which is why the credential check below is disabled rather
    // than deleted. The price is that the BioPortal lookups it performs run on the API key this
    // server holds, so an anonymous caller spends CEDAR's BioPortal quota. Nothing here bounds that.
    // CedarRequestContext c = buildRequestContext();
    // c.must(c.user()).be(LoggedIn);

    try {
      int page = extractPage(body);
      int pageSize = extractPageSize(body);

      PagedResults results =
        terminologyService.integratedRetrieve(body.getValueConstraints(), page, pageSize, apiKey);

      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(results)).build();

    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
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
