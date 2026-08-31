package org.metadatacenter.cedar.terminology.resources.bioportal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.domainObjects.ValueSetCollection;
import org.metadatacenter.terms.domainObjects.VersionTriple;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.List;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Value set collections")
@SecurityRequirement(name = "api_key")
public class ValueSetCollectionResource extends AbstractTerminologyServerResource {

  public ValueSetCollectionResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Path("vs-collections")
  @Operation(summary = "Find all value set collections", description = "Find all value set collections.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findAllVSCollections(
      @Parameter(description = "If true, additional details about each value set collection will be included in the " +
          "response. Default: false.")
      @QueryParam("include_details") @DefaultValue("false") boolean includeDetails)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<ValueSetCollection> vsCollections = terminologyService.findAllVSCollections(includeDetails, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(vsCollections)).build();
    } catch (HTTPException e) {
      return relayedBioPortalFailure(e);
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/version-current")
  @Operation(summary = "Resolve the current version triple for a value-set collection",
      description = "The version triple of a value-set collection's current (\"latest\") locally-stored "
          + "snapshot — the freeze-on-publish capability for a value-set-valued constraint. Value-set "
          + "collections are ingested and versioned by the same content-hash mechanism as ontologies. "
          + "404 when the collection is not ingested and served locally.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "404", description = "Value-set collection not resolvable locally"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response resolveCurrentVersionForValueSetCollection(
      @Parameter(description = "Value-set collection acronym. Example: CEDARVS.", required = true)
      @QueryParam("collection") String collection) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      VersionTriple triple = terminologyService.resolveCurrentVersionForValueSetCollection(collection);
      if (triple == null) {
        return CedarResponse.notFound().build();
      }
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(triple)).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

}