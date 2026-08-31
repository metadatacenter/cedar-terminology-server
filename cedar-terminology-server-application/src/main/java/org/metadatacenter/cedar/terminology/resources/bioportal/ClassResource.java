package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.cedar.util.dw.AnonymousAccess;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Classes")
@SecurityRequirement(name = "api_key")
public class ClassResource extends AbstractTerminologyServerResource {

  public ClassResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @GET
  @Path("ontologies/{ontology}/classes/{id}")
  @Operation(summary = "Find class", description = "Find class (either regular or provisional) by ontology and class id.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findClass(
      @Parameter(description = "Class identifier. Examples: http://data.bioontology.org/provisional_classes/" +
          "4f82a7f0-bbba-0133-b23e-005056010074 (provisional class). " +
          "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C3224 (regular class).", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology,
      @Parameter(description = "Optional BCP-47 language for the returned label (e.g. fr). Honored for "
          + "locally-served ontologies; ignored for BioPortal-proxied ones.")
      @QueryParam("lang") String lang) throws
      CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      OntologyClass c = terminologyService.findClass(id, ontology, apiKey, lang);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(c)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes")
  @Operation(summary = "Get classes", description = "Get all classes from a specific ontology (including both regular and provisional classes).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findAllClassesForOntology(
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology,
      @Parameter(description = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @Parameter(description = "Number of results per page. Example: 10.")
      @QueryParam("pageSize") int pageSize,
      @Parameter(description = "Alias for the page size, accepted in either spelling.")
      @QueryParam("page_size") int pageSizeAlias) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    pageSize = resolvePageSize(pageSize, pageSizeAlias);
    try {
      PagedResults<OntologyClass> classes =
          terminologyService.findAllClassesInOntology(ontology, page, pageSize, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(classes)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/{id}/tree")
  @Operation(summary = "Get class tree", description = "Get class tree (only for regular classes).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findClassTree(
      @Parameter(description = "Class identifier. Example: http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C3224.", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology) throws
      CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      boolean isFlat = Cache.isFlat(ontology);
      List<TreeNode> tree = terminologyService.getClassTree(id, ontology, isFlat, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(tree)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException | ExecutionException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/{id}/children")
  @Operation(summary = "Get class children", description = "Get class children (only for regular classes).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findClassChildren(
      @Parameter(description = "Class identifier. Example: http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C3224.", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology,
      @Parameter(description = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @Parameter(description = "Number of results per page. Example: 10.")
      @QueryParam("pageSize") int pageSize,
      @Parameter(description = "Alias for the page size, accepted in either spelling.")
      @QueryParam("page_size") int pageSizeAlias) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    pageSize = resolvePageSize(pageSize, pageSizeAlias);
    try {
      PagedResults<OntologyClass> children = terminologyService.getClassChildren(id, ontology, page,
          pageSize, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(children)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }


  @GET
  @Path("ontologies/{ontology}/classes/{id}/descendants")
  @Operation(summary = "Get class descendants", description = "Get class descendants.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  @AnonymousAccess
  public Response findClassDescendants(
      @Parameter(description = "Class identifier. Examples: http://data.bioontology.org/provisional_classes/" +
          "4f82a7f0-bbba-0133-b23e-005056010074 (provisional class). " +
          "http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C3224 (regular class).", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology,
      @Parameter(description = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @Parameter(description = "Number of results per page. Example: 10.")
      @QueryParam("pageSize") int pageSize,
      @Parameter(description = "Alias for the page size, accepted in either spelling.")
      @QueryParam("page_size") int pageSizeAlias)
      throws CedarException {
    CedarRequestContext ctx = buildAnonymousRequestContext();
    pageSize = resolvePageSize(pageSize, pageSizeAlias);
    try {
      PagedResults<OntologyClass> descendants = terminologyService.getClassDescendants(id, ontology,
          page, pageSize, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(descendants)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/{id}/parents")
  @Operation(summary = "Get class parents", description = "Get class parents.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findClassParents(
      @Parameter(description = "Class identifier. Example: http://ncicb.nci.nih.gov/xml/owl/EVS/Thesaurus.owl#C3224.", required = true)
      @PathParam("id") @Encoded String id,
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology)
      throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<OntologyClass> descendants = terminologyService.getClassParents(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(descendants)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("classes/provisional")
  @Operation(summary = "Get provisional classes", description = "Get provisional classes (including provisional value sets and provisional values).")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findAllProvisionalClasses(
      @Parameter(description = "Page to be returned. Example: 7.")
      @QueryParam("page") @DefaultValue("1") int page,
      @Parameter(description = "Number of results per page. Example: 10.")
      @QueryParam("pageSize") int pageSize,
      @Parameter(description = "Alias for the page size, accepted in either spelling.")
      @QueryParam("page_size") int pageSizeAlias) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    pageSize = resolvePageSize(pageSize, pageSizeAlias);
    try {
      PagedResults<OntologyClass> classes = terminologyService.findAllProvisionalClasses(null, page, pageSize, apiKey);
      // This line ensures that @class type annotations are included for each element in the list
      //ObjectWriter writer = JsonMapper.MAPPER.writerFor(new TypeReference<PagedResults<OntologyClass>>() {});
      //return Response.ok().entity(JsonMapper.MAPPER.readTree(writer.writeValueAsString(classes))).build();
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(classes)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/provisional")
  @Operation(summary = "Get all provisional classes in a particular ontology", description = "Get all provisional classes in a particular ontology (including provisional value sets and " +
          "provisional values)")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful operation"),
      @ApiResponse(responseCode = "400", description = "Bad request"),
      @ApiResponse(responseCode = "401", description = "Unauthorized"),
      @ApiResponse(responseCode = "403", description = "Forbidden"),
      @ApiResponse(responseCode = "404", description = "Not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  public Response findAllProvisionalClassesForOntology(
      @Parameter(description = "BioPortal ontology identifier. Examples: NCIT, FMA, OBI.", required = true)
      @PathParam("ontology") String ontology,
      @Parameter(description = "Page to be returned. Example: 7.") @QueryParam
      ("page") @DefaultValue("1") int page,
      @Parameter(description = "Number of results per page. Example: 10.")
      @QueryParam("pageSize") int pageSize,
      @Parameter(description = "Alias for the page size, accepted in either spelling.")
      @QueryParam("page_size") int pageSizeAlias) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    pageSize = resolvePageSize(pageSize, pageSizeAlias);
    try {
      PagedResults<OntologyClass> classes =
          terminologyService.findAllProvisionalClasses(ontology, page, pageSize, apiKey);
      // This line ensures that @class type annotations are included for each element in the list
      ObjectWriter writer = JsonMapper.MAPPER.writerFor(new TypeReference<PagedResults<OntologyClass>>() {
      });
      return Response.ok().entity(JsonMapper.MAPPER.readTree(writer.writeValueAsString(classes))).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

}
