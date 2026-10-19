package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.validation.IriReference;
import org.metadatacenter.terms.TermVerification;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.util.json.JsonMapper;

import java.util.Set;

/** Anonymous like integrated-search, for embedded clients. No labels enter the membership proof. */
@Path("/bioportal/validate-term")
@Produces(MediaType.APPLICATION_JSON)
public class TermVerificationResource extends AbstractTerminologyServerResource {
  public record VerificationRequest(String iri, ValueConstraints valueConstraints) {}
  private static final ObjectMapper MAPPER = JsonMapper.STRICT_MAPPER.copy()
      .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
      .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
  private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
  private static final Set<String> ARRAYS = Set.of("ontologies", "branches", "classes", "valueSets");

  public TermVerificationResource(CedarConfig config) { super(config); }

  @POST @Timed @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "Verify an exact term identifier against complete field value constraints",
      description = "Returns passed, failed, notChecked or unavailable independently of save policy. "
          + "An unavailable release, incomplete lookup or outage is never a failed membership check. "
          + "No constraints means notChecked, not proof of existence. Identifier spelling is preserved.")
  @RequestBody(required = true, content = @Content(schema = @Schema(implementation = VerificationRequest.class)))
  @ApiResponse(responseCode = "200", description = "Semantic verification facts", content = @Content(schema = @Schema(implementation = TermVerification.class)))
  @ApiResponse(responseCode = "400", description = "Malformed identifier or value-constraint shape", content = @Content(schema = @Schema(implementation = CedarError.class)))
  public Response verify(@Parameter(hidden = true) JsonNode body) {
    try {
      if (body == null || !body.isObject() || body.size() != 2 || !body.has("iri")
          || !body.path("iri").isTextual() || !body.path("valueConstraints").isObject()) {
        return invalid("Expected an object containing iri (string) and valueConstraints (object).");
      }
      String iri = body.path("iri").textValue();
      if (!IriReference.toUri(iri).isAbsolute()) return invalid("iri must be an absolute IRI.");
      ObjectNode input = body.path("valueConstraints").deepCopy();
      for (String array : ARRAYS) {
        if (!input.has(array)) input.putArray(array);
        if (!input.path(array).isArray() || input.path(array).size() > 1000) return invalid(array + " must be an array of at most 1000 constraints.");
        for (JsonNode entry : input.path(array)) {
          if (!entry.isObject()) return invalid("Each constraint must be an object.");
          for (String key : Set.of("uri", "iri")) {
            if (entry.has(key) && (!entry.path(key).isTextual() || !IriReference.toUri(entry.path(key).textValue()).isAbsolute())) return invalid("Constraint identifiers must be absolute IRIs.");
          }
          if ("classes".equals(array) && !Set.of("OntologyClass", "Value").contains(entry.path("type").asText())) return invalid("A class constraint must name an OntologyClass or Value.");
        }
      }
      if (input.has("actions")) {
        if (!input.path("actions").isArray() || input.path("actions").size() > 1000) return invalid("actions must be an array of at most 1000 actions.");
        for (JsonNode action : input.path("actions")) {
          if (!action.isObject()) return invalid("Each action must be an object.");
          if (!action.path("termUri").isTextual() || !IriReference.toUri(action.path("termUri").textValue()).isAbsolute()) return invalid("Action termUri must be an absolute IRI.");
          if (!Set.of("OntologyClass", "Value").contains(action.path("type").asText())) return invalid("An action must name an OntologyClass or Value.");
          if ("move".equals(action.path("action").asText()) && (!action.path("to").isIntegralNumber()
              || !action.path("to").canConvertToInt() || action.path("to").intValue() < 0)) return invalid("A move action requires a nonnegative integer position.");
        }
      }
      for (JsonNode branch : input.path("branches")) {
        if (branch.has("maxDepth") && (!branch.path("maxDepth").isIntegralNumber()
            || !branch.path("maxDepth").canConvertToInt() || branch.path("maxDepth").intValue() < 0)) return invalid("maxDepth must be a nonnegative integer.");
      }
      ValueConstraints constraints = MAPPER.treeToValue(input, ValueConstraints.class);
      if (!VALIDATOR.validate(constraints).isEmpty()) return invalid("The value constraints are incomplete or malformed.");
      if (terminologyService == null) return Response.ok(TermVerification.unavailable(iri, "Terminology verification is not configured.")).build();
      return Response.ok(terminologyService.verifyTerm(iri, constraints, apiKey)).build();
    } catch (java.io.IOException | java.net.URISyntaxException | IllegalArgumentException e) {
      return invalid("The term verification request is malformed.");
    }
  }

  private static Response invalid(String message) { return CedarResponse.badRequest().message(message).build(); }
}
