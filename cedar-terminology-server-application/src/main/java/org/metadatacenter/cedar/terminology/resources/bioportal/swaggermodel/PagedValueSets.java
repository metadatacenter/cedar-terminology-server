package org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel;

import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.ValueSet;

/**
 * Documentation-only binding of {@link PagedResults} to {@link ValueSet}.
 *
 * <p>A {@code @Schema} annotation cannot carry a type argument, so the generic page a handler returns
 * has no schema of its own. Extending the generic with the argument fixed lets Jackson resolve
 * {@code collection} to the element type the route really serves.</p>
 */
@Schema(name = "PagedValueSets", description = "One page of value sets.")
public class PagedValueSets extends PagedResults<ValueSet> {
}
