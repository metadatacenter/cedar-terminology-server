package org.metadatacenter.cedar.terminology.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/** Static compatibility asset for the existing BioPortal mark. No remote image proxying. */
@Path("/resources/icons/bioportal.png")
public class ResourceIconResource {
  @GET @Produces("image/png")
  @io.swagger.v3.oas.annotations.Operation(operationId = "terminologySourceIcon", summary = "Get the BioPortal source icon")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Source icon",
      content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "image/png",
          schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary")))
  public Response icon() throws java.io.IOException {
    try (var in = getClass().getResourceAsStream("/assets/icons/bioportal.png")) {
      return Response.ok(in.readAllBytes()).header("Cache-Control", "public, max-age=86400").build();
    }
  }
}
