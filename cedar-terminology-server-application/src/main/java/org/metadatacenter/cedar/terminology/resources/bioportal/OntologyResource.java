package org.metadatacenter.cedar.terminology.resources.bioportal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.OntologyVersion;
import org.metadatacenter.terms.domainObjects.OntologyProperty;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Ontologies")
@SecurityRequirement(name = "api_key")
public class OntologyResource extends AbstractTerminologyServerResource {

  public OntologyResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Path("ontologies")
  @Operation(summary = "Find all ontologies", description = "Find all ontologies.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findAllOntologies() throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<Ontology> ontologies = new ArrayList<>(Cache.getOntologies().values());
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(ontologies)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (ExecutionException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{id}")
  @Operation(summary = "Find ontology by id", description = "Find ontology by id.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findOntology(
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      Ontology ontologies = Cache.getOntology(id);
      if (ontologies == null) {
        return CedarResponse.notFound().build();
      }
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(ontologies)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (ExecutionException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{id}/versions")
  @Operation(summary = "List local versions of an ontology",
      description = "Versions of an ontology available in the local, version-pinned store, each with "
          + "its content-hash id, self-declared version, release date, and whether it is the current "
          + "one. Empty when the ontology is served from BioPortal (which has no equivalent).",
      tags = {"Ontologies"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response getVersions(
      @Parameter(description = "Ontology acronym. Examples: DOID, INCENTIVE.", required = true)
      @PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyVersion> versions = terminologyService.getVersions(id);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(versions)).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{id}/versions/diff")
  @Operation(summary = "Diff two local versions of an ontology",
      description = "The vocabulary diff (concept and subsumption-edge additions/removals, newly "
          + "obsoleted, capped concept samples) between two versions in the local store, given by "
          + "version_id or tag. 404 when the ontology or a version is not served locally.",
      tags = {"Ontologies"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "404", description = "Ontology or version not found locally"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response diffVersions(
      @Parameter(description = "Ontology acronym.", required = true) @PathParam("id") String id,
      @Parameter(description = "Base version (version_id or tag).", required = true)
      @jakarta.ws.rs.QueryParam("from") String from,
      @Parameter(description = "Target version (version_id or tag, e.g. latest).", required = true)
      @jakarta.ws.rs.QueryParam("to") String to) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      var diff = terminologyService.diffVersions(id, from, to);
      if (diff == null) {
        return CedarResponse.notFound().build();
      }
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(diff)).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/roots")
  @Operation(summary = "Get root classes", description = "Get root classes in a particular ontology. For the CEDARPC ontology, all provisional classes in it " +
          "will be returned.", tags = {"Classes", "Ontologies"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findRootClasses(
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      boolean isFlat = Cache.isFlat(ontology);
      List<OntologyClass> roots = terminologyService.getRootClasses(ontology, isFlat, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(roots)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException | ExecutionException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/properties/roots")
  @Operation(summary = "Get root properties", description = "Get root properties in a particular ontology.", tags = {"Properties", "Ontologies"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findRootProperties(
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyProperty> roots = terminologyService.getRootProperties(ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(roots)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

}