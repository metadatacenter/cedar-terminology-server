package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.metadatacenter.cedar.terminology.resources.AbstractResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
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
public class ValueSetCollectionResource extends AbstractResource {

  @GET
  @Path("vs-collections")
  //  @ApiOperation(
  //      value = "Find all value set collections",
  //      //notes = "This call is not paged",
  //      httpMethod = "GET")
  //  @ApiResponses(value = {
  //      @ApiResponse(code = 200, message = "Success!"),
  //      @ApiResponse(code = 400, message = "Bad Request"),
  //      @ApiResponse(code = 401, message = "Unauthorized"),
  //      @ApiResponse(code = 500, message = "Internal Server Error")})
  public Response findAllVSCollections(@QueryParam("include_details") @DefaultValue("false") boolean includeDetails)
      throws CedarAssertionException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
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