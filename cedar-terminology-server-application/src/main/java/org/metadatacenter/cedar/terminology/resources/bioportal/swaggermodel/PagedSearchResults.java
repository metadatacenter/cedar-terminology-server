package org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel;

import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.SearchResult;

/**
 * Documentation-only binding of {@link PagedResults} to {@link SearchResult}.
 *
 * <p>A {@code @Schema} annotation cannot carry a type argument, so the generic page a handler returns
 * has no schema of its own. Extending the generic with the argument fixed lets Jackson resolve
 * {@code collection} to the element type the route really serves.</p>
 */
@Schema(name = "PagedSearchResults", description = "One page of search results.")
public class PagedSearchResults extends PagedResults<SearchResult> {
}
