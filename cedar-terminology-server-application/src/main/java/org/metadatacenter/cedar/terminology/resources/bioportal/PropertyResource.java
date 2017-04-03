package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
public class PropertyResource extends AbstractTerminologyServerResource {

  public PropertyResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  // TODO
  @GET
  @Path("ontologies/{ontology}/properties/{id}")
  public Response findProperty(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology) throws
      CedarException {
//    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
//    ctx.must(ctx.user()).be(LoggedIn);
//    try {
//      OntologyClass c = terminologyService.findClass(id, ontology, apiKey);
//      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(c)).build();
//    } catch (HTTPException e) {
//      return Response.status(e.getStatusCode()).build();
//    } catch (IOException e) {
//      throw new CedarAssertionException(e);
//    }
    return null;
  }

}