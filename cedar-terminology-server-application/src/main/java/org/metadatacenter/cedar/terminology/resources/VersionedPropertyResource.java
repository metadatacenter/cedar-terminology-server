package org.metadatacenter.cedar.terminology.resources;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.sql.SQLException;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.util.http.CedarResponse;
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
      return CedarResponse.status(503)
          .message("The local terminology store is not configured.")
          .build();
    try {
      return Response.ok(read.get()).build();
    } catch (IllegalArgumentException e) {
      return CedarResponse.status(400).message(e.getMessage()).build();
    } catch (VersionedPropertyService.NotHeld e) {
      return CedarResponse.status(404).message(e.getMessage()).build();
    } catch (IllegalStateException e) {
      return CedarResponse.status(503).message(e.getMessage()).build();
    } catch (SQLException e) {
      // The body carries the message and an errorId. The database exception is logged under that
      // errorId and stays out of the body, because its message names the store's files and tables.
      return CedarResponse.internalServerError()
          .message("Could not read the ontology snapshot")
          .exception(e)
          .build();
    }
  }

  @POST
  @Path("/search")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Search properties in local ontology snapshots")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Local ontology property data",
          content = @Content(schema = @Schema(implementation = VersionedPropertyService.Result.class))),
      @ApiResponse(responseCode = "400", description = "The body is missing or is not JSON "
          + "(malformedJsonRequestBody), or the search is refused: the query is empty, longer than 500 "
          + "characters or holds no letter or number, a kind is unknown, the paging is out of range or mixes "
          + "limit and offset with page and pageSize, more than 20 sources are named, or a source names no "
          + "sourceAcronym. The message names the problem.",
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "404", description = "A named source's release, or its snapshot, is not held "
          + "locally",
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "406", description = NOT_ACCEPTABLE,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "415", description = "The body is not declared as JSON",
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "500", description = STORE_FAILED,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "503", description = NOT_CONFIGURED + ", a named release has no extracted "
          + "properties, or a search naming no source finds no property tables in the search index",
          content = @Content(schema = @Schema(implementation = CedarError.class)))
  })
  public Response search(VersionedPropertyService.Request request) {
    return read(() -> service.search(request));
  }

  @GET
  @Path("/versions")
  @Operation(summary = "Ontology releases and their property extraction availability")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Ontology versions",
          content = @Content(array = @ArraySchema(
              schema = @Schema(implementation = VersionedPropertyService.Version.class)))),
      @ApiResponse(responseCode = "404", description = "No sourceAcronym is given, or the ontology is not served "
          + "locally",
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "406", description = NOT_ACCEPTABLE,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "500", description = STORE_FAILED,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "503", description = NOT_CONFIGURED,
          content = @Content(schema = @Schema(implementation = CedarError.class)))
  })
  public Response versions(@QueryParam("sourceAcronym") String acronym) {
    return read(() -> service.versions(acronym));
  }

  @GET
  @Operation(summary = "Find a property at an ontology version")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Local ontology property data",
          content = @Content(schema = @Schema(implementation = VersionedPropertyService.Detail.class))),
      @ApiResponse(responseCode = "400", description = "sourceAcronym, propertyIri or kind is missing, or kind is "
          + "unknown. The message names the problem.",
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "404", description = PROPERTY_NOT_HELD,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "406", description = NOT_ACCEPTABLE,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "500", description = STORE_FAILED,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "503", description = NOT_EXTRACTED,
          content = @Content(schema = @Schema(implementation = CedarError.class)))
  })
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
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Local ontology property data",
          content = @Content(schema = @Schema(implementation = VersionedPropertyService.Hierarchy.class))),
      @ApiResponse(responseCode = "400", description = "sourceAcronym, propertyIri or kind is missing, kind is "
          + "unknown, or limit or offset is out of range. The message names the problem. " + NOT_A_NUMBER,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "404", description = PROPERTY_NOT_HELD,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "406", description = NOT_ACCEPTABLE,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "500", description = STORE_FAILED,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "503", description = NOT_EXTRACTED,
          content = @Content(schema = @Schema(implementation = CedarError.class)))
  })
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
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Local ontology property data",
          content = @Content(schema = @Schema(implementation = VersionedPropertyService.Roots.class))),
      @ApiResponse(responseCode = "400", description = "sourceAcronym or kind is missing, kind is unknown, or "
          + "limit or offset is out of range. The message names the problem. " + NOT_A_NUMBER,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "404", description = "The release, or its snapshot, is not held locally",
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "406", description = NOT_ACCEPTABLE,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "500", description = STORE_FAILED,
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "503", description = NOT_EXTRACTED,
          content = @Content(schema = @Schema(implementation = CedarError.class)))
  })
  public Response roots(
      @QueryParam("sourceAcronym") String acronym,
      @QueryParam("versionId") String version,
      @QueryParam("kind") String kind,
      @QueryParam("offset") @DefaultValue("0") int offset,
      @QueryParam("limit") Integer limit) {
    return read(() -> service.roots(acronym, version, kind, offset, childLimit(limit, offset))
        .paged(uriInfo.getRequestUri().toString()));
  }

  private static final String NOT_ACCEPTABLE = "The request does not accept a JSON response";
  private static final String NOT_A_NUMBER = "A limit or offset that is not a 32-bit integer is refused with a "
      + "message that identifies the parameter and says what form it takes.";
  private static final String NOT_CONFIGURED = "No local terminology store is configured";
  private static final String NOT_EXTRACTED = NOT_CONFIGURED + ", or the release has no extracted properties";
  private static final String PROPERTY_NOT_HELD = "The release, or its snapshot, is not held locally, or the "
      + "release does not contain the property";
  private static final String STORE_FAILED = "The ontology snapshot could not be read, which the message "
      + "reports, or the server failed in another way. The body's errorId identifies the failure in the server log.";

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
