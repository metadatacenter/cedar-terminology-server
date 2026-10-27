package org.metadatacenter.cedar.terminology.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.swagger.v3.oas.annotations.Operation;
import org.metadatacenter.terms.search.ResourceDescription;
import org.metadatacenter.terms.search.ResourceDescriptionService;
import org.metadatacenter.util.json.JsonMapper;
import java.sql.SQLException;

/** Read-only presentation lookup; it neither expands constraints nor validates membership. */
@Path("/resources/description")
@Produces(MediaType.APPLICATION_JSON)
public class ResourceDescriptionResource {
  private static ResourceDescriptionService service = new ResourceDescriptionService(null);
  public static void injectService(ResourceDescriptionService value) { service = value; }

  @GET
  @Operation(operationId = "describeTerminologyResource", summary = "Describe a terminology resource and its public links")
  @io.swagger.v3.oas.annotations.responses.ApiResponses({
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Public resource description; unavailable references are explicit",
          content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResourceDescription.class))),
      @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing or invalid reference",
          content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = org.metadatacenter.util.http.CedarError.class)))
  })
  public Response describe(@QueryParam("kind") @DefaultValue("term") String kind,
                           @QueryParam("sourceSystem") String sourceSystem,
                           @QueryParam("sourceAcronym") String sourceAcronym,
                           @QueryParam("sourceIri") String sourceIri,
                           @QueryParam("resourceIri") String resourceIri,
                           @QueryParam("versionId") String versionId,
                           @QueryParam("language") String language) throws SQLException {
    try {
      return Response.ok(JsonMapper.STRICT_MAPPER.valueToTree(service.describe(
          new ResourceDescription.Reference(kind, sourceSystem, sourceAcronym, sourceIri, resourceIri, versionId), language)))
          .header("Cache-Control", "private, no-cache").build();
    } catch (IllegalArgumentException e) {
      return org.metadatacenter.util.http.CedarResponse.status(400).message(e.getMessage()).build();
    } catch (SQLException e) {
      return org.metadatacenter.util.http.CedarResponse.internalServerError()
          .message("Could not describe the terminology resource").exception(e).build();
    }
  }
}
