package org.metadatacenter.cedar.terminology.resources.bioportal;

import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
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
public class ValueSetCollectionResource extends AbstractTerminologyServerResource {

  public ValueSetCollectionResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Path("vs-collections")
  public Response findAllVSCollections(@QueryParam("include_details") @DefaultValue("false") boolean includeDetails)
      throws CedarException {
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