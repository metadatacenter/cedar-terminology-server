package org.metadatacenter.cedar.terminology.resources.bioportal.swaggermodel;

import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;

/**
 * Documentation-only binding of {@link PagedResults} to {@link OntologyClass}.
 *
 * <p>A {@code @Schema} annotation cannot carry a type argument, so the generic page a handler returns
 * has no schema of its own. Extending the generic with the argument fixed lets Jackson resolve
 * {@code collection} to the element type the route really serves.</p>
 */
@Schema(name = "PagedOntologyClasses", description = "One page of ontology classes.")
public class PagedOntologyClasses extends PagedResults<OntologyClass> {
}
