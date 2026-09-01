package org.metadatacenter.cedar.terminology.resources;

import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
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
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.search.HierarchyLookup;
import org.metadatacenter.terms.search.SearchRequest;
import org.metadatacenter.terms.search.SearchResponse;
import org.metadatacenter.terms.search.VersionAwareSearchService;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.json.JsonMapper;

import java.sql.SQLException;

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
          + "the version that answered, and whether a constraint on it can be pinned.",
      tags = {"Search"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Results, and the sources that produced them"),
      @ApiResponse(responseCode = "400", description = "A request the server will not answer"),
      @ApiResponse(responseCode = "503", description = "No local terminology store is configured")
  })
  public Response search(SearchRequest request) throws CedarException {
    // Deliberately not authenticated, matching integrated-search: both are read paths a CEDAR
    // frontend calls directly, and requiring a credential here would be a third answer to a question
    // the terminology server already gives two of.
    if (searchService == null) {
      return CedarResponse.status(CedarResponseStatus.SERVICE_UNAVAILABLE)
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .errorMessage("Version-aware search needs the local terminology store, and no catalog is configured.")
          .build();
    }
    if (request == null) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .errorMessage("A search needs a JSON body.")
          .build();
    }
    try {
      SearchResponse response = searchService.search(request);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(response)).build();
    } catch (VersionAwareSearchService.BadSearchRequestException e) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .errorMessage(e.getMessage())
          .build();
    } catch (SQLException e) {
      throw new CedarAssertionException(e);
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
          + "With versionId, answered from that release's snapshot: a hierarchy belongs to a release, "
          + "and a term's parent can move between two of them. Without it, from the cross-snapshot "
          + "index, which holds each ontology's current version. "
          + "Children are alphabetical and capped; offset asks for the next page of them.",
      tags = {"Search"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "The term's ancestors and children"),
      @ApiResponse(responseCode = "400", description = "A request naming no term"),
      @ApiResponse(responseCode = "404", description = "No such release, a release without that term, or a term the index does not hold"),
      @ApiResponse(responseCode = "503", description = "No local terminology store is configured")
  })
  public Response hierarchy(@QueryParam("sourceAcronym") String sourceAcronym,
                            @QueryParam("termIri") String termIri,
                            @QueryParam("versionId") String versionId,
                            @QueryParam("offset") @DefaultValue("0") int offset) throws CedarException {
    if (searchService == null) {
      return CedarResponse.status(CedarResponseStatus.SERVICE_UNAVAILABLE)
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .errorMessage("A hierarchy comes from the local terminology store, and no catalog is configured.")
          .build();
    }
    if (sourceAcronym == null || sourceAcronym.isBlank() || termIri == null || termIri.isBlank()) {
      return CedarResponse.badRequest()
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .errorMessage("A hierarchy needs sourceAcronym and termIri. An IRI addresses a term only within a source.")
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
      HierarchyLookup lookup = searchService.hierarchy(sourceAcronym, termIri, versionId, offset);
      if (lookup instanceof HierarchyLookup.Found found) {
        return Response.ok().entity(JsonMapper.MAPPER.valueToTree(found.hierarchy())).build();
      }
      if (lookup instanceof HierarchyLookup.ReleaseNotHeld unheld) {
        return CedarResponse.notFound()
            .errorKey(CedarErrorKey.INVALID_INPUT)
            .errorMessage("No release of " + unheld.acronym() + " held locally answers to "
                + unheld.versionId() + ", so nothing was read. A release is named by the whole "
                + "content hash the catalog reports, not an abbreviation of it.")
            .build();
      }
      if (lookup instanceof HierarchyLookup.TermNotInRelease absent) {
        return CedarResponse.notFound()
            .errorKey(CedarErrorKey.INVALID_INPUT)
            .errorMessage("Release " + absent.versionId() + " of " + absent.acronym()
                + " does not contain " + absent.termIri()
                + ". Another release of the same source may contain it.")
            .build();
      }
      if (lookup instanceof HierarchyLookup.TermNotInIndex absent) {
        return CedarResponse.notFound()
            .errorKey(CedarErrorKey.INVALID_INPUT)
            .errorMessage("The store holds no term " + absent.termIri() + " in "
                + absent.acronym() + ".")
            .build();
      }
      // Every permitted case is handled above, so this is a case added to the sealed type without a
      // message of its own. A guard for each rather than a cast for the last: the cast would compile
      // for exactly as long as the assumption held and then fail here at runtime instead.
      throw new CedarAssertionException(
          new IllegalStateException("unhandled hierarchy lookup: " + lookup));
    } catch (SQLException e) {
      throw new CedarAssertionException(e);
    }
  }
}
