package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.metadatacenter.terms.util.OffsetPaging;
import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirementEntry;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel.IntegratedRetrieveResults;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.IntegratedRetrieveBody;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.util.http.CedarError;
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
          "those fields. <br /> <br />A credential is optional. The route has served anonymous callers in every " +
          "release since it was introduced in 2024.", tags = {"Classes", "Value sets", "Values"})
  @RequestBody(description = "Object that encapsulates the information needed to run the " +
          "search query. The \"valueConstraints\" field specification is based on CEDAR's \"_valueConstraints\" " +
          "field. See https://more.metadatacenter.org/tools-training/outreach/cedar-template-model for more details.", required = true, content = @Content(schema = @Schema(implementation = org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel.IntegratedRetrieveRequestBody.class)))
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "A paginated list of search results", content = @Content(schema = @Schema(implementation = IntegratedRetrieveResults.class))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Bad request"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Not found"),
      @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "Internal server error")
  })
  // swagger-core gives every method the class's api_key requirement. An entry with a blank name adds
  // the empty alternative beside it, which is how OpenAPI says that a credential is optional.
  @SecurityRequirement(combine = @SecurityRequirementEntry(name = ""))
  public Response cedarIntegratedRetrieve(@Valid IntegratedRetrieveBody body) throws CedarException {

    // This route is anonymous because it always has been, not because a client needs it. The credential
    // check was disabled when the route was written, and the route has answered anonymous callers in
    // every release since. The embeddable editor, whose third-party hosts are why integrated-search is
    // anonymous, never calls it, and the one CEDAR client that does, the artifact library, sends an API
    // key. Restoring the check would therefore refuse only callers outside CEDAR, whom nothing here can
    // count. The BioPortal lookups the route performs run on the API key this server holds, so an
    // anonymous caller spends CEDAR's BioPortal quota, and nothing here bounds that.
    // CedarRequestContext c = buildRequestContext();
    // c.must(c.user()).be(LoggedIn);

    try {
      OffsetPaging.Request paging = OffsetPaging.resolve(body.getLimit(), body.getOffset(),
          body.getPage() > 0 ? body.getPage() : null, extractPageSize(body), body.getPageSize() > 0,
          defaultPageSize);

      PagedResults<?> results = OffsetPaging.fetch(paging, (p, s) ->
          terminologyService.integratedRetrieve(body.getValueConstraints(), p, s, apiKey))
          .envelope(paging.limit(), paging.offset(), null);

      return Response.ok().entity(JsonMapper.STRICT_MAPPER.valueToTree(results)).build();

    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw bioPortalUnusable(e);
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
