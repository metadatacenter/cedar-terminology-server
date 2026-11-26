package org.metadatacenter.cedar.terminology;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.UriBuilder;
import org.metadatacenter.util.http.CedarResponse;
import java.util.Map;

/** Compatibility for released term pickers until their hosts adopt the snake_case queries. */
@PreMatching
public class PublishedTerminologyQueryFilter implements ContainerRequestFilter {
  @Override public void filter(ContainerRequestContext request) {
    String path = request.getUriInfo().getPath();
    if (!request.getMethod().equals("GET") || !(path.equals("search/hierarchy")
        || path.equals("properties") || path.startsWith("properties/"))) return;
    var query = request.getUriInfo().getQueryParameters();
    UriBuilder uri = request.getUriInfo().getRequestUriBuilder();
    boolean changed = false;
    for (var name : Map.of("sourceAcronym", "source_acronym", "versionId", "version_id",
        "termIri", "term_iri", "propertyIri", "property_iri").entrySet()) {
      if (!query.containsKey(name.getKey())) continue;
      if (query.containsKey(name.getValue())) {
        request.abortWith(CedarResponse.badRequest().message("Supply only " + name.getValue()).build());
        return;
      }
      uri.replaceQueryParam(name.getValue(), query.get(name.getKey()).toArray());
      uri.replaceQueryParam(name.getKey());
      changed = true;
    }
    if (changed) request.setRequestUri(uri.build());
  }
}
