package org.metadatacenter.cedar.terminology.resources;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.sql.SQLException;
import java.util.Map;
import org.metadatacenter.terms.search.VersionedPropertyService;

/** Read-only property API backed by the same ontology snapshots as version-aware class search. */
@Path("/properties")
@Produces(MediaType.APPLICATION_JSON)
public class VersionedPropertyResource {

  /** The request being answered, whose URL a page's links are built from. */
  @Context
  UriInfo uriInfo;
  private static VersionedPropertyService service;

  public static void injectService(VersionedPropertyService value) {
    service = value;
  }

  @FunctionalInterface
  private interface Read {
    Object get() throws SQLException;
  }

  private Response read(Read read) {
    if (service == null)
      return Response.status(503)
          .entity(Map.of("message", "The local terminology store is not configured."))
          .build();
    try {
      return Response.ok(read.get()).build();
    } catch (IllegalArgumentException e) {
      return Response.status(400).entity(Map.of("message", e.getMessage())).build();
    } catch (VersionedPropertyService.NotHeld e) {
      return Response.status(404).entity(Map.of("message", e.getMessage())).build();
    } catch (IllegalStateException e) {
      return Response.status(503).entity(Map.of("message", e.getMessage())).build();
    } catch (SQLException e) {
      throw new InternalServerErrorException("Could not read the ontology snapshot", e);
    }
  }

  @POST
  @Path("/search")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Search properties in local ontology snapshots")
  @ApiResponse(
      responseCode = "200",
      description = "Local ontology property data",
      content = @Content(schema = @Schema(implementation = VersionedPropertyService.Result.class)))
  public Response search(VersionedPropertyService.Request request) {
    return read(() -> service.search(request));
  }

  @GET
  @Path("/versions")
  @Operation(summary = "Ontology releases and their property extraction availability")
  @ApiResponse(
      responseCode = "200",
      description = "Ontology versions",
      content =
          @Content(
              array =
                  @ArraySchema(
                      schema = @Schema(implementation = VersionedPropertyService.Version.class))))
  public Response versions(@QueryParam("sourceAcronym") String acronym) {
    return read(() -> service.versions(acronym));
  }

  @GET
  @Operation(summary = "Find a property at an ontology version")
  @ApiResponse(
      responseCode = "200",
      description = "Local ontology property data",
      content = @Content(schema = @Schema(implementation = VersionedPropertyService.Detail.class)))
  public Response find(
      @QueryParam("sourceAcronym") String acronym,
      @QueryParam("versionId") String version,
      @QueryParam("propertyIri") String iri,
      @QueryParam("kind") String kind) {
    return read(() -> service.find(acronym, version, iri, kind));
  }

  @GET
  @Path("/hierarchy")
  @Operation(summary = "Property ancestors and paged children at an ontology version")
  @ApiResponse(
      responseCode = "200",
      description = "Local ontology property data",
      content =
          @Content(schema = @Schema(implementation = VersionedPropertyService.Hierarchy.class)))
  public Response hierarchy(
      @QueryParam("sourceAcronym") String acronym,
      @QueryParam("versionId") String version,
      @QueryParam("propertyIri") String iri,
      @QueryParam("kind") String kind,
      @QueryParam("offset") @DefaultValue("0") int offset,
      @QueryParam("limit") Integer limit) {
    return read(() -> service.hierarchy(acronym, version, iri, kind, offset, childLimit(limit, offset))
        .paged(uriInfo.getRequestUri().toString()));
  }

  @GET
  @Path("/roots")
  @Operation(summary = "Paged root properties at an ontology version")
  @ApiResponse(
      responseCode = "200",
      description = "Local ontology property data",
      content = @Content(schema = @Schema(implementation = VersionedPropertyService.Roots.class)))
  public Response roots(
      @QueryParam("sourceAcronym") String acronym,
      @QueryParam("versionId") String version,
      @QueryParam("kind") String kind,
      @QueryParam("offset") @DefaultValue("0") int offset,
      @QueryParam("limit") Integer limit) {
    return read(() -> service.roots(acronym, version, kind, offset, childLimit(limit, offset))
        .paged(uriInfo.getRequestUri().toString()));
  }

  /** The page size a hierarchy or roots request asks for, refused when out of range. */
  private static int childLimit(Integer limit, int offset) {
    int value = limit == null ? VersionedPropertyService.DEFAULT_CHILD_LIMIT : limit;
    if (value < 1 || value > VersionedPropertyService.MAX_CHILD_LIMIT || offset < 0) {
      throw new IllegalArgumentException("limit must be 1–" + VersionedPropertyService.MAX_CHILD_LIMIT
          + " and offset must not be negative");
    }
    return value;
  }
}
