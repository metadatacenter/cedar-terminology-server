package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
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
