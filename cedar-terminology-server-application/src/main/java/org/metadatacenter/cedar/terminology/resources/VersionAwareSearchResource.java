package org.metadatacenter.cedar.terminology.resources;

import io.swagger.v3.oas.annotations.Parameter;
import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.terms.paging.PageLimits;
import org.metadatacenter.terms.search.HierarchyLookup;
import org.metadatacenter.terms.search.HierarchyResponse;
import org.metadatacenter.terms.search.SearchRequest;
import org.metadatacenter.terms.search.SearchResponse;
import org.metadatacenter.terms.search.VersionAwareSearchService;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.json.JsonMapper;

import java.sql.SQLException;

import static org.metadatacenter.constant.CedarQueryParameters.QP_LIMIT;
import static org.metadatacenter.constant.CedarQueryParameters.QP_OFFSET;
import static org.metadatacenter.constant.CedarQueryParameters.QP_SOURCE_ACRONYM;
import static org.metadatacenter.constant.CedarQueryParameters.QP_TERM_IRI;
import static org.metadatacenter.constant.CedarQueryParameters.QP_TYPE;
import static org.metadatacenter.constant.CedarQueryParameters.QP_VERSION_ID;

/**
 * Version-aware search, at a new root path rather than under {@code /bioportal}.
 *
 * That namespace exists because CEDAR proxied BioPortal, and this is not a BioPortal client: it
 * searches the local versioned store at a named version or the current one, and reports per source
 * which version answered and whether a constraint on it can be pinned. It replaces
 * {@code /bioportal/search} once that route's consumers have moved.
 *
 * The request and response shapes are designed in
 * {@code cedar-development/ops/VERSIONING-ROADMAP.md}, "The Search API".
 */
@Path("/search")
@Produces(MediaType.APPLICATION_JSON)
public class VersionAwareSearchResource extends AbstractTerminologyServerResource {

  private static VersionAwareSearchService searchService;

  public VersionAwareSearchResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  /**
   * The service, or null when no catalog is configured. Null is the whole point: this endpoint
   * assumes the local store and reports that it is unavailable rather than answering from BioPortal,
   * so a caller is never handed unpinnable results believing that pinning was available.
   */
  public static void injectSearchService(VersionAwareSearchService service) {
    VersionAwareSearchResource.searchService = service;
  }

  @POST
  @Timed
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(operationId = "versionAwareSearch",
      summary = "Version-aware search",
      description = "Search the local terminology store at a named version or the current one, across the "
          + "constraint types a controlled-term field can carry: ontology, branch, class and valueSet. "
          + "Returns per-type counts and each type's first page, and describes every source it searched — "
          + "the version that answered, and whether a constraint on it can be pinned. Each type's block is paged by "
          + "limit (1 to " + PageLimits.MAX_LIMIT + ", default " + PageLimits.DEFAULT_LIMIT + "; a larger limit is "
          + "refused with 400) and offset, and carries totalCount, countCapped, request and currentOffset. A POST "
          + "has no URL to link to, so a block carries no paging links: ask for the next page by sending the next "
          + "offset. A body with a page other than 1 is refused with 400.",
      tags = {"Search"})
  @RequestBody(description = "The query, the constraint types to answer, and the sources to search. A source "
      + "names the version to search it at, or none to search the current one. Keys are the versioned "
      + "value-constraint specification's, so a result can become a constraint entry without translation.",
      required = true, content = @Content(schema = @Schema(implementation = SearchRequest.class)))
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Results, and the sources that produced them", content = @Content(schema = @Schema(implementation = SearchResponse.class))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "A request the server will not answer"),
      @ApiResponse(responseCode = "503", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "No local terminology store is configured")
  })
  public Response search(SearchRequest request) throws CedarException {
    // Deliberately not authenticated, matching integrated-search: both are read paths a CEDAR
    // frontend calls directly, and requiring a credential here would be a third answer to a question
    // the terminology server already gives two of.
    if (searchService == null) {
      return CedarResponse.status(CedarResponseStatus.SERVICE_UNAVAILABLE)
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .message("Version-aware search needs the local terminology store, and no catalog is configured.")
          .build();
    }
    if (request == null) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .message("A search needs a JSON body.")
          .build();
    }
    try {
      SearchResponse response = searchService.search(request);
      return Response.ok().entity(JsonMapper.STRICT_MAPPER.valueToTree(response)).build();
    } catch (VersionAwareSearchService.BadSearchRequestException e) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .message(e.getMessage())
          .build();
    } catch (SQLException e) {
      throw new CedarProcessingException(e);
    }
  }

  @GET
  @Timed
  @Path("/hierarchy")
  @Operation(operationId = "versionAwareSearchHierarchy",
      summary = "Where a term sits in its ontology",
      description = "The chain of ancestors above a term, root first, and what hangs directly below it. "
          + "A search result names a term; whether it is the right term is a question about its "
          + "neighbourhood, and two concepts with one label are told apart by nothing else. "
          + "Always answered from one release's snapshot, because a hierarchy belongs to a release "
          + "and a term's parent can move between two of them. With versionId, the release is that one. "
          + "Without it, the release is the one an unpinned search of the source reads. "
          + "Children are alphabetical and paged by limit and offset; the answer carries totalCount, request, "
          + "currentOffset and paging, which holds links to the first, previous, next and last pages of children. "
          + "With type=valueSet, the term is a value set and the children are its values. The source must be "
          + "a value-set collection, and a release holds the value set only where values hang below it.",
      tags = {"Search"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The term's ancestors and children", content = @Content(schema = @Schema(implementation = HierarchyResponse.class))),
      @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "A request naming no term, or a type other than class, branch or valueSet"),
      @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "No such release, a release without that term, a term the current release does not hold, or a value set asked of a source that is not a value-set collection"),
      @ApiResponse(responseCode = "503", content = @Content(schema = @Schema(implementation = CedarError.class)), description = "No local terminology store is configured")
  })
  public Response hierarchy(@QueryParam(QP_SOURCE_ACRONYM) String sourceAcronym,
                            @QueryParam(QP_TERM_IRI) String termIri,
                            @QueryParam(QP_VERSION_ID) String versionId,
                            @Parameter(description = "How many children to skip. Defaults to 0.")
                            @QueryParam(QP_OFFSET) @DefaultValue("0") int offset,
                            @Parameter(description = "How many children to return, from 1 to "
                                + VersionAwareSearchService.MAX_CHILD_LIMIT + ". Defaults to "
                                + VersionAwareSearchService.CHILD_LIMIT + ". A larger limit is refused with 400.")
                            @QueryParam(QP_LIMIT) Integer limit,
                            @Parameter(description = "The constraint type the term is asked about: class, branch or "
                                + "valueSet. Defaults to a class or branch, which read the same tree.")
                            @QueryParam(QP_TYPE) String type) throws CedarException {
    if (searchService == null) {
      return CedarResponse.status(CedarResponseStatus.SERVICE_UNAVAILABLE)
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .message("A hierarchy comes from the local terminology store, and no catalog is configured.")
          .build();
    }
    if (sourceAcronym == null || sourceAcronym.isBlank() || termIri == null || termIri.isBlank()) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .message("A hierarchy needs sourceAcronym and termIri. An IRI addresses a term only within a source.")
          .build();
    }
    try {
      // Each way of having no tree is reported as itself. One message covered all three, and it
      // named the term in the case that had not looked at any term: a release identifier nothing
      // matches was reported as the store holding no such term, which sent a reader looking for a
      // missing ingest instead of a bad request.
      //
      // An `instanceof` chain rather than a switch over the sealed type: patterns in switch are a
      // preview feature on the Java this stack is pinned to, and a preview flag is not worth one
      // conditional. The chain covers every permitted case, and the compiler would not tell us if a
      // fifth were added — the sealed declaration is where to look for the full list.
      refusePageNumbers();
      int childLimit = limit == null ? VersionAwareSearchService.CHILD_LIMIT : limit;
      if (childLimit < 1 || childLimit > VersionAwareSearchService.MAX_CHILD_LIMIT || offset < 0) {
        return CedarResponse.badRequest()
            .errorKey(CedarErrorKey.INVALID_INPUT)
            .message("limit must be between 1 and " + VersionAwareSearchService.MAX_CHILD_LIMIT
                + ", and offset must not be negative.")
            .build();
      }
      // A type the endpoint does not know is refused rather than read as a tree. Reading `valueset`
      // as a class would answer a question the caller did not ask.
      boolean valueSet = SearchRequest.TYPE_VALUE_SET.equals(type);
      if (type != null && !type.isBlank() && !valueSet
          && !SearchRequest.TYPE_CLASS.equals(type) && !SearchRequest.TYPE_BRANCH.equals(type)) {
        return CedarResponse.badRequest()
            .errorKey(CedarErrorKey.INVALID_INPUT)
            .message("type must be class, branch or valueSet.")
            .build();
      }
      HierarchyLookup lookup = searchService.hierarchy(sourceAcronym, termIri, versionId, offset, childLimit,
          valueSet);
      if (lookup instanceof HierarchyLookup.Found found) {
        return Response.ok().entity(JsonMapper.STRICT_MAPPER.valueToTree(
            found.hierarchy().paged(childLimit, uriInfo.getRequestUri().toString()))).build();
      }
      if (lookup instanceof HierarchyLookup.ReleaseNotHeld unheld) {
        return CedarResponse.notFound()
            .errorKey(CedarErrorKey.INVALID_INPUT)
            .message("No release of " + unheld.acronym() + " held locally answers to "
                + unheld.versionId() + ", so nothing was read. A release is named by the whole "
                + "content hash the catalog reports, not an abbreviation of it.")
            .build();
      }
      if (lookup instanceof HierarchyLookup.TermNotInRelease absent) {
        return CedarResponse.notFound()
            .errorKey(CedarErrorKey.INVALID_INPUT)
            .message("Release " + absent.versionId() + " of " + absent.acronym()
                + " does not contain " + absent.termIri()
                + ". Another release of the same source may contain it.")
            .build();
      }
      if (lookup instanceof HierarchyLookup.TermNotHeld absent) {
        return CedarResponse.notFound()
            .errorKey(CedarErrorKey.INVALID_INPUT)
            .message("The store holds no term " + absent.termIri() + " in "
                + absent.acronym() + ".")
            .build();
      }
      if (lookup instanceof HierarchyLookup.NotValueSetCollection notCollection) {
        return CedarResponse.notFound()
            .errorKey(CedarErrorKey.INVALID_INPUT)
            .message(notCollection.acronym() + " is not a value-set collection, so no release of it holds "
                + "the value set " + notCollection.termIri() + ".")
            .build();
      }
      // Every permitted case is handled above, so this is a case added to the sealed type without a
      // message of its own. A guard for each rather than a cast for the last: the cast would compile
      // for exactly as long as the assumption held and then fail here at runtime instead.
      throw new CedarProcessingException(
          new IllegalStateException("unhandled hierarchy lookup: " + lookup));
    } catch (SQLException e) {
      throw new CedarProcessingException(e);
    }
  }
}
