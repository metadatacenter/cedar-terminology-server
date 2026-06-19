package org.metadatacenter.cedar.terminology.resources.bioportal;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.domainObjects.ValueSetCollection;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.List;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "/bioportal", tags = "Value set collections", authorizations = {@Authorization("api_key")})
public class ValueSetCollectionResource extends AbstractTerminologyServerResource {

  public ValueSetCollectionResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Path("vs-collections")
  @ApiOperation(value = "Find all value set collections", notes = "Find all value set collections.")
  @ApiResponses({
      @ApiResponse(code = 200, message = "Successful operation"),
      @ApiResponse(code = 400, message = "Bad request"),
      @ApiResponse(code = 401, message = "Unauthorized"),
      @ApiResponse(code = 403, message = "Forbidden"),
      @ApiResponse(code = 404, message = "Not found"),
      @ApiResponse(code = 500, message = "Internal server error")
  })
  public Response findAllVSCollections(
      @ApiParam(value = "If true, additional details about each value set collection will be included in the " +
          "response. Default: false.")
      @QueryParam("include_details") @DefaultValue("false") boolean includeDetails)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<ValueSetCollection> vsCollections = terminologyService.findAllVSCollections(includeDetails, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(vsCollections)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

}