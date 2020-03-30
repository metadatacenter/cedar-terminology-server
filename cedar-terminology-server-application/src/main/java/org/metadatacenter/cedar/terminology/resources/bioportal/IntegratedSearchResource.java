package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.utils.validation.integratedsearch.IntegratedSearchBody;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.context.CedarRequestContext;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

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
  @Path("/cedar-integrated-search")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response cedarIntegratedSearch(@Valid IntegratedSearchBody body) throws CedarException {

    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);

    return Response.ok().build();

//    CedarRequestContext c = buildAnonymousRequestContext();
//    JsonNode requestBody = c.request().getRequestBody().asJson();
//
//    if ((!requestBody.hasNonNull(BP_INTEGRATED_SEARCH_PARAMS_FIELD)) ||
//        (!requestBody.hasNonNull(BP_INTEGRATED_SEARCH_PARAMS_FIELD)))
//
//
//
//      public static final String BP_INTEGRATED_SEARCH_PARAM_VALUE_CONSTRAINTS = "valueConstraints";
//    public static final String BP_INTEGRATED_SEARCH_PARAM_INPUT_TEXT = "inputText";
//
//
//    try {
//      // If pageSize not defined, set default value
//      if (pageSize == 0) {
//        pageSize = defaultPageSize;
//      }
//      // Review and clean scope
//      List<String> scopeList = new ArrayList<>();
//      List<String> referenceScopeList = Arrays
//          .asList(BP_SEARCH_SCOPE_ALL, BP_SEARCH_SCOPE_CLASSES, BP_SEARCH_SCOPE_VALUE_SETS, BP_SEARCH_SCOPE_VALUES);
//      for (String s : Arrays.asList(scope.split("\\s*,\\s*"))) {
//        if (!referenceScopeList.contains(s)) {
//          return CedarResponse.badRequest()
//              .errorKey(CedarErrorKey.INVALID_INPUT)
//              .errorMessage("Wrong scope. Accepted values = {all, classes, value_sets, values}")
//              .build();
//        } else {
//          scopeList.add(s);
//        }
//      }
//      // Sources list
//      List<String> sourcesList = new ArrayList<>();
//      if (sources != null && sources.length() > 0) {
//        sourcesList = Arrays.asList(sources.split("\\s*,\\s*"));
//      }
//      List<String> valueSetsIds = new ArrayList<>(Cache.valueSetsCache.get("value-sets").keySet());
//      // TODO: The valueSetsIds parameter is passed to the service to avoid making additional calls to BioPortal.
//      // These ids are used to know if a particular result returned by BioPortal is a value or a value set.
//      // BioPortal should provide this information and this parameter should be removed
//      PagedResults results = terminologyService.search(q, scopeList, sourcesList, suggest, source, subtreeRootId,
//          maxDepth,
//          page, pageSize, false, true, apiKey, valueSetsIds);
//      JsonNode output = JsonMapper.MAPPER.valueToTree(results);
//      return Response.ok().entity(output).build();
//    } catch (HTTPException e) {
//      return Response.status(e.getStatusCode()).build();
//    } catch (IOException | ExecutionException e) {
//      throw new CedarAssertionException(e);
//    }
  }
}
