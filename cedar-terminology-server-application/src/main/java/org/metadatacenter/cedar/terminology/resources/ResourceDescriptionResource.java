package org.metadatacenter.cedar.terminology.resources;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.terms.search.ResourceDescription;
import org.metadatacenter.terms.search.ResourceDescriptionService;
import org.metadatacenter.terms.search.TerminologyPresentationContract;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.http.CedarResponse;
import java.sql.SQLException;

/** Read-only lookup using CEDAR constraint identity and one generic presentation schema. */
@Path("/resources/description")
@Produces(MediaType.APPLICATION_JSON)
public class ResourceDescriptionResource {
  private static ResourceDescriptionService service = new ResourceDescriptionService(null);
  public static void injectService(ResourceDescriptionService value) { service = value; }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(operationId = "describeTerminologyResource", summary = "Resolve presentation for a CEDAR constraint reference")
  @RequestBody(required = true, content = @Content(schema = @Schema(implementation = ResourceDescription.Reference.class)))
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Constraint identity and generic presentation; an empty presentation means no known page",
          content = @Content(schema = @Schema(implementation = ResourceDescription.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid constraint reference",
          content = @Content(schema = @Schema(implementation = org.metadatacenter.util.http.CedarError.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Source, resource or exact version unavailable",
          content = @Content(schema = @Schema(implementation = org.metadatacenter.util.http.CedarError.class)))
  })
  public Response describe(@io.swagger.v3.oas.annotations.Parameter(hidden = true) JsonNode input) {
    try {
      return Response.ok(JsonMapper.STRICT_MAPPER.valueToTree(service.describe(
          TerminologyPresentationContract.readReference(input))))
          .header("Cache-Control", "private, no-cache").build();
    } catch (ResourceDescriptionService.NotFoundException e) {
      return CedarResponse.status(404).message(e.getMessage()).build();
    } catch (IllegalArgumentException e) {
      return CedarResponse.status(400).message(e.getMessage()).build();
    } catch (SQLException e) {
      return CedarResponse.internalServerError().message("Could not describe the terminology resource").exception(e).build();
    }
  }
}
