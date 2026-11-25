package org.metadatacenter.cedar.terminology.resources;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.sql.SQLException;
import org.metadatacenter.cedar.util.dw.AnonymousAccess;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.terms.paging.PageLimits;
import org.metadatacenter.terms.search.VersionedPropertyService;
import org.metadatacenter.util.http.PagedQuery;

import static org.metadatacenter.constant.CedarQueryParameters.QP_KIND;
import static org.metadatacenter.constant.CedarQueryParameters.QP_LIMIT;
import static org.metadatacenter.constant.CedarQueryParameters.QP_OFFSET;
import static org.metadatacenter.constant.CedarQueryParameters.QP_PROPERTY_IRI;
import static org.metadatacenter.constant.CedarQueryParameters.QP_SOURCE_ACRONYM;
import static org.metadatacenter.constant.CedarQueryParameters.QP_VERSION_ID;

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

  @AnonymousAccess
  @POST
  @Path("/search")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Search properties in local ontology snapshots",
      description = "Paged by limit (1 to " + PageLimits.MAX_LIMIT + ", default " + PageLimits.DEFAULT_LIMIT
          + ") and offset. The answer carries totalCount, request and currentOffset. A POST has no URL to link to, "
          + "so the answer carries no paging links: ask for the next page by sending the next offset.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Local ontology property data",
          content = @Content(schema = @Schema(implementation = VersionedPropertyService.Result.class))),
      @ApiResponse(responseCode = "400", description = "The body is missing or is not JSON "
          + "(malformedJsonRequestBody), or the search is refused: the query is empty, longer than 500 "
          + "characters or holds no letter or number, a kind is unknown, the limit or offset is out of range, "
          + "the body sends a page other than 1, more than 20 sources are named, or a source names no "
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

  @AnonymousAccess
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
  public Response versions(@QueryParam(QP_SOURCE_ACRONYM) String acronym) {
    return read(() -> service.versions(acronym));
  }

  @AnonymousAccess
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
      @QueryParam(QP_SOURCE_ACRONYM) String acronym,
      @QueryParam(QP_VERSION_ID) String version,
      @QueryParam(QP_PROPERTY_IRI) String iri,
      @QueryParam(QP_KIND) String kind) {
    return read(() -> service.find(acronym, version, iri, kind));
  }

  @AnonymousAccess
  @GET
  @Path("/hierarchy")
  @Operation(summary = "Property ancestors and paged children at an ontology version",
      description = CHILD_ENVELOPE)
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Local ontology property data",
          content = @Content(schema = @Schema(implementation = VersionedPropertyService.Hierarchy.class))),
      @ApiResponse(responseCode = "400", description = "sourceAcronym, propertyIri or kind is missing, kind is "
          + "unknown, " + OUT_OF_RANGE + " The message names the problem. " + NOT_A_NUMBER,
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
      @QueryParam(QP_SOURCE_ACRONYM) String acronym,
      @QueryParam(QP_VERSION_ID) String version,
      @QueryParam(QP_PROPERTY_IRI) String iri,
      @QueryParam(QP_KIND) String kind,
      @Parameter(description = CHILD_OFFSET) @QueryParam(QP_OFFSET) @DefaultValue("0") int offset,
      @Parameter(description = CHILD_LIMIT) @QueryParam(QP_LIMIT) Integer limit) {
    return read(() -> service.hierarchy(acronym, version, iri, kind, offset, childLimit(limit, offset, uriInfo))
        .paged(uriInfo.getRequestUri().toString()));
  }

  @AnonymousAccess
  @GET
  @Path("/roots")
  @Operation(summary = "Paged root properties at an ontology version",
      description = CHILD_ENVELOPE)
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Local ontology property data",
          content = @Content(schema = @Schema(implementation = VersionedPropertyService.Roots.class))),
      @ApiResponse(responseCode = "400", description = "sourceAcronym or kind is missing, kind is unknown, "
          + OUT_OF_RANGE + " The message names the problem. " + NOT_A_NUMBER,
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
      @QueryParam(QP_SOURCE_ACRONYM) String acronym,
      @QueryParam(QP_VERSION_ID) String version,
      @QueryParam(QP_KIND) String kind,
      @Parameter(description = ROOT_OFFSET) @QueryParam(QP_OFFSET) @DefaultValue("0") int offset,
      @Parameter(description = ROOT_LIMIT) @QueryParam(QP_LIMIT) Integer limit) {
    return read(() -> service.roots(acronym, version, kind, offset, childLimit(limit, offset, uriInfo))
        .paged(uriInfo.getRequestUri().toString()));
  }

  private static final String NOT_ACCEPTABLE = "The request does not accept a JSON response";
  private static final String CHILD_ENVELOPE = "Paged by limit and offset. Nothing counts the properties, so "
      + "countCapped is always true and totalCount is a lower bound: everything up to this page, and one more when "
      + "the page came back full. The answer carries request, currentOffset and paging, whose links lead to the "
      + "first, previous and next pages and leave out the last.";
  private static final String CHILD_LIMIT = "How many children to return, from 1 to "
      + VersionedPropertyService.MAX_CHILD_LIMIT + ". Defaults to " + VersionedPropertyService.DEFAULT_CHILD_LIMIT
      + ". A larger limit is refused with 400.";
  private static final String CHILD_OFFSET = "How many children to skip. Defaults to 0.";
  private static final String ROOT_LIMIT = "How many roots to return, from 1 to "
      + VersionedPropertyService.MAX_CHILD_LIMIT + ". Defaults to " + VersionedPropertyService.DEFAULT_CHILD_LIMIT
      + ". A larger limit is refused with 400.";
  private static final String ROOT_OFFSET = "How many roots to skip. Defaults to 0.";
  private static final String OUT_OF_RANGE = "limit is outside 1 to " + VersionedPropertyService.MAX_CHILD_LIMIT
      + ", offset is negative, or the request carries page, pageSize or page_size.";
  private static final String NOT_A_NUMBER = "A limit or offset that is not a 32-bit integer is refused with a "
      + "message that identifies the parameter and says what form it takes.";
  private static final String NOT_CONFIGURED = "No local terminology store is configured";
  private static final String NOT_EXTRACTED = NOT_CONFIGURED + ", or the release has no extracted properties";
  private static final String PROPERTY_NOT_HELD = "The release, or its snapshot, is not held locally, or the "
      + "release does not contain the property";
  private static final String STORE_FAILED = "The ontology snapshot could not be read, which the message "
      + "reports, or the server failed in another way. The body's errorId identifies the failure in the server log.";

  /**
   * The page size a hierarchy or roots request asks for, refused when out of range or when the
   * request carries a withdrawn page-number parameter.
   *
   * <p>The bound is the store's own page bound, so a limit this accepts is one the store can read. A
   * larger one is refused here, with a message naming the range, rather than by the store.
   */
  private static int childLimit(Integer limit, int offset, UriInfo uriInfo) {
    if (PagedQuery.pageNumberParameter(uriInfo.getQueryParameters()).isPresent()) {
      throw new IllegalArgumentException(PageLimits.PAGE_NUMBERS_WITHDRAWN);
    }
    int value = limit == null ? VersionedPropertyService.DEFAULT_CHILD_LIMIT : limit;
    if (value < 1 || value > VersionedPropertyService.MAX_CHILD_LIMIT || offset < 0) {
      throw new IllegalArgumentException("limit must be 1–" + VersionedPropertyService.MAX_CHILD_LIMIT
          + " and offset must not be negative");
    }
    return value;
  }
}
