package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel.IntegratedSearchResults;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.IntegratedSearchBody;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.PinnedVersionUnavailableException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.Optional;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Classes")
@SecurityRequirement(name = "api_key")
public class IntegratedSearchResource extends AbstractTerminologyServerResource {

  public IntegratedSearchResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  /**
   * Search for classes, value sets and values
   */
  @POST
  @Timed
  @Path("/integrated-search")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Search for classes and values based on CEDAR value constraints", description = "Search for ontology classes, value sets, and values based on CEDAR value constraints. This endpoint " +
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
          "field. See https://more.metadatacenter.org/tools-training/outreach/cedar-template-model for more details.", required = true, content = @Content(schema = @Schema(implementation = org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel.IntegratedSearchRequestBody.class)))
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "A paginated list of search results", content = @Content(schema = @Schema(implementation = IntegratedSearchResults.class))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "422", description = "A constraint pins a vocabulary version that cannot be served"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  public Response cedarIntegratedSearch(@Valid IntegratedSearchBody body,
      @Parameter(description = "Optional BCP-47 language for result labels (e.g. fr). Honored for "
          + "locally-served, single-source constraints; ignored for BioPortal-proxied ones.")
      @QueryParam("lang") String lang) throws CedarException {

    // Anonymous by decision, not by omission. Third-party deployments of the embeddable editor reach
    // this route without a CEDAR session, which is why the credential check below is disabled rather
    // than deleted. The price is that the BioPortal lookups it performs run on the API key this
    // server holds, so an anonymous caller spends CEDAR's BioPortal quota. Nothing here bounds that.
    // CedarRequestContext c = buildRequestContext();
    // c.must(c.user()).be(LoggedIn);

    try {
      int page = extractPage(body);
      int pageSize = extractPageSize(body);
      String inputText = extractInputText(body);
      Optional<String> q = inputText != null? Optional.of(inputText) : Optional.empty();

      PagedResults results =
          terminologyService.integratedSearch(q, body.getParameterObject().getValueConstraints(),
              page, pageSize, apiKey, lang);

      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(results)).build();

    } catch (PinnedVersionUnavailableException e) {
      // A frozen constraint pins a vocabulary version that cannot be served; the server fails the read
      // rather than resolving against latest. 422 Unprocessable Entity: the request is well-formed but
      // the pinned snapshot is unavailable.
      return Response.status(422)
          .entity(JsonMapper.MAPPER.createObjectNode()
              .put("errorType", "PinnedVersionUnavailable")
              .put("message", e.getMessage()))
          .type(MediaType.APPLICATION_JSON)
          .build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException /*| ExecutionException*/ e) {
      throw new CedarAssertionException(e);
    }
  }

  /**
   * Utility Methods
   **/

  public int extractPage(IntegratedSearchBody body) {
    int page = body.getPage();
    // If page not defined or invalid, set it to the first page
    if (page <= 0) {
      page = 1;
    }
    return page;
  }

  public int extractPageSize(IntegratedSearchBody body) {
    int pageSize = body.getPageSize();
    // If pageSize not defined or invalid, set it to the default value
    if (pageSize <= 0) {
      pageSize = defaultPageSize;
    }
    return pageSize;
  }

  public String extractInputText(IntegratedSearchBody body) {
    String inputText = body.getParameterObject().getInputText();
    if (inputText != null && inputText.trim().length() > 0) {
      return inputText;
    } else {
      return null;
    }
  }

}
