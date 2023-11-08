package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.IntegratedSearchBody;
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
import java.util.Optional;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
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
  public Response cedarIntegratedSearch(@Valid IntegratedSearchBody body) throws CedarException {

    //CedarRequestContext c = buildRequestContext();
    //c.must(c.user()).be(LoggedIn);

    try {
      int page = extractPage(body);
      int pageSize = extractPageSize(body);
      String inputText = extractInputText(body);
      Optional<String> q = inputText != null? Optional.of(inputText) : Optional.empty();

      PagedResults results =
          terminologyService.integratedSearch(q, body.getParameterObject().getValueConstraints(),
              page, pageSize, apiKey);

      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(results)).build();

    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
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
