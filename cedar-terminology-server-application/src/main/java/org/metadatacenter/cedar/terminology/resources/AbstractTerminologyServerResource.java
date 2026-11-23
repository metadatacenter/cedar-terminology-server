package org.metadatacenter.cedar.terminology.resources;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.io.IOException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.terms.util.HTTPException;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.terms.ITerminologyService;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.paging.PageLimits;
import org.metadatacenter.terms.util.OffsetPaging;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.exception.CedarException;

public abstract class AbstractTerminologyServerResource extends CedarMicroserviceResource {

  public static ITerminologyService terminologyService;
  protected static String apiKey;

  /** The {@code limit} parameter of every offset-paged GET route. */
  protected static final String LIMIT_DESCRIPTION = "How many results to return, from 1 to " + PageLimits.MAX_LIMIT
      + ". Defaults to " + PageLimits.DEFAULT_LIMIT + ". A larger limit is refused with 400.";

  /** The {@code offset} parameter of every offset-paged GET route. */
  protected static final String OFFSET_DESCRIPTION = "How many results to skip. Defaults to 0.";

  /** What the answer of an offset-paged GET route carries besides its results. */
  protected static final String PAGED_GET_ENVELOPE = ". The answer carries totalCount, request (the limit and "
      + "offset served), currentOffset, and paging, which holds links to the first, previous, next and last pages.";

  /** What the answer of an offset-paged POST route carries besides its results. */
  protected static final String PAGED_POST_ENVELOPE = ". The answer carries totalCount, request (the limit and "
      + "offset served) and currentOffset. A POST has no URL to link to, so the answer carries no paging links: "
      + "ask for the next page by sending the next offset.";

  /** The query parameters of the withdrawn page-number paging. A GET request carrying one is refused. */
  private static final List<String> PAGE_NUMBER_PARAMETERS = List.of("page", "pageSize", "page_size");

  protected AbstractTerminologyServerResource(CedarConfig cedarConfig) {
    super(cedarConfig);
    apiKey = cedarConfig.getTerminologyConfig().getBioPortal().getApiKey();
  }

  public static void injectTerminologyService(ITerminologyService terminologyService) {
    AbstractTerminologyServerResource.terminologyService = terminologyService;
  }

  /**
   * Renders a BioPortal failure this server is relaying, with a body saying so.
   *
   * <p>Thirty-three catch blocks answered {@code Response.status(e.getStatusCode()).build()} and nothing
   * else, so a caller received a bare status with no indication that it came from BioPortal rather than
   * from CEDAR. The worst case is the server's own API key expiring: the client gets a 401 about a
   * credential it does not hold and has no way to act on.
   *
   * <p>Not every upstream status is the caller's answer. A 401 or 403 from BioPortal means the key this
   * server holds was refused, which the caller can do nothing about and must not be told to retry
   * authentication over; an upstream 5xx is an outage. Both become 502, which is what a gateway says
   * when the service behind it failed. A 404 is relayed: the term really was not found, and that is the
   * caller's answer. A 400 is relayed too, since it usually reflects a parameter the caller supplied.
   */
  protected static Response relayedBioPortalFailure(HTTPException e) {
    int upstreamStatus = e.getStatusCode();
    // BioPortal's quota belongs to the deployment's shared account, not this caller. Keep this
    // policy explicit: adding a status to the common enum must not change gateway classification.
    boolean upstreamFault = upstreamStatus == 401 || upstreamStatus == 403 || upstreamStatus == 429
        || upstreamStatus >= 500;

    CedarResponseStatus status;
    String explanation;
    if (upstreamFault) {
      status = CedarResponseStatus.BAD_GATEWAY;
      explanation = upstreamStatus == 401 || upstreamStatus == 403
          ? "BioPortal refused the API key this server holds, which is a CEDAR configuration problem "
              + "rather than anything the caller can authenticate its way past."
          : "BioPortal failed with " + upstreamStatus + ".";
    } else {
      status = CedarResponseStatus.fromStatusCode(upstreamStatus);
      if (status == null) {
        status = CedarResponseStatus.BAD_GATEWAY;
      }
      explanation = "BioPortal answered " + upstreamStatus + ", which is the caller's answer.";
    }

    return CedarResponse.status(status)
        .errorKey(CedarErrorKey.UPSTREAM_SERVER_ERROR)
        .message(explanation)
        .parameter("upstreamStatusCode", upstreamStatus)
        .parameter("upstreamService", "BioPortal")
        .build();
  }

  /**
   * A BioPortal call that got no answer this server could use.
   *
   * <p>Every route turned this into a 500, which says this server failed. No answer at all, a refused
   * connection or a timeout, is an outage of the service behind it, so a 503. An answer whose body
   * could not be read is a gateway's upstream failing, so a 502, as an upstream 5xx already is.
   */
  protected static CedarProcessingException bioPortalUnusable(IOException e) {
    if (e instanceof JsonProcessingException) {
      CedarProcessingException unreadable =
          new CedarProcessingException("BioPortal answered with something that could not be read", e);
      unreadable.getErrorPack().status(CedarResponseStatus.BAD_GATEWAY);
      return unreadable;
    }
    return new CedarDependencyUnavailableException("BioPortal did not answer", e);
  }

  /**
   * A failure the cache reports for a BioPortal call it made. It carries an IOException, or a list
   * that came back far short of the catalogue, which is a load that failed upstream and so a 502.
   */
  protected static CedarProcessingException bioPortalUnusable(ExecutionException e) {
    if (e.getCause() instanceof IOException io) {
      return bioPortalUnusable(io);
    }
    CedarProcessingException failed = new CedarProcessingException(
        "BioPortal's answer could not be used: " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()), e);
    failed.getErrorPack().status(CedarResponseStatus.BAD_GATEWAY);
    return failed;
  }

  /**
   * The page a GET request asks for by {@code limit} and {@code offset}. A request carrying
   * {@code page}, {@code pageSize} or {@code page_size} is refused.
   */
  protected OffsetPaging.Request pagingOf(Integer limit, Integer offset) throws CedarException {
    refusePageNumbers();
    return OffsetPaging.resolve(limit, offset);
  }

  /** Refuses a GET request carrying {@code page}, {@code pageSize} or {@code page_size}. */
  protected void refusePageNumbers() throws CedarException {
    Optional<String> name = pageNumberParameter(uriInfo);
    if (name.isPresent()) {
      throw new CedarAssertionException(PageLimits.PAGE_NUMBERS_WITHDRAWN)
          .parameter(name.get(), uriInfo.getQueryParameters().getFirst(name.get())).badRequest();
    }
  }

  /** The first withdrawn page-number parameter a request carries, if it carries one. */
  public static Optional<String> pageNumberParameter(UriInfo info) {
    MultivaluedMap<String, String> query = info.getQueryParameters();
    return PAGE_NUMBER_PARAMETERS.stream().filter(query::containsKey).findFirst();
  }

  /** The page with CEDAR's paging envelope filled in, its links built from this request's own URL. */
  protected <T> PagedResults<T> enveloped(PagedResults<T> results, OffsetPaging.Request paging) {
    return results.envelope(paging.limit(), paging.offset(), uriInfo.getRequestUri().toString());
  }
}
