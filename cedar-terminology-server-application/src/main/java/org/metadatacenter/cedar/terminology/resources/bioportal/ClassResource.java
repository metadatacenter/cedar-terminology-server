package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
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

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
public class ClassResource extends AbstractTerminologyServerResource {

  public ClassResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Path("ontologies/{ontology}/classes")
  public Response createClass(@PathParam("ontology") String ontology) throws CedarException {
    CedarRequestContext ctx = buildAnonymousRequestContext();
    try {
      OntologyClass c = JsonMapper.MAPPER.convertValue(ctx.request().getRequestBody().asJson(), OntologyClass.class);
      c.setOntology(ontology);
      OntologyClass createdClass = terminologyService.createProvisionalClass(c, apiKey);
      JsonNode createdClassJson = JsonMapper.MAPPER.valueToTree(createdClass);
      return Response.created(new URI(createdClass.getLdId())).entity(createdClassJson).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (URISyntaxException | IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes/{id}")
  public Response findClass(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology) throws
      CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      OntologyClass c = terminologyService.findClass(id, ontology, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(c)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("ontologies/{ontology}/classes")
  public Response findAllClassesForOntology(@PathParam("ontology") String ontology,
                                            @QueryParam("page") @DefaultValue("1") int page,
                                            @QueryParam("pageSize") int pageSize) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
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
  public Response findClassTree(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology) throws
      CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      boolean isFlat = Cache.ontologiesCache.get("ontologies").get(ontology).getIsFlat();
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
  public Response findClassChildren(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology,
                                    @QueryParam("page") @DefaultValue("1") int page, @QueryParam("pageSize")
                                        int pageSize) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
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
  public Response findClassDescendants(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology,
                                       @QueryParam("page") @DefaultValue("1") int page,
                                       @QueryParam("pageSize") int pageSize)
      throws CedarException {
    CedarRequestContext ctx = buildAnonymousRequestContext();
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
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
  public Response findClassParents(@PathParam("id") @Encoded String id, @PathParam("ontology") String ontology)
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
  public Response findAllProvisionalClasses(@QueryParam("page") @DefaultValue("1") int page,
                                            @QueryParam("pageSize") int pageSize) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
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
  public Response findAllProvisionalClassesForOntology(@PathParam("ontology") String ontology, @QueryParam
      ("page") @DefaultValue("1") int page, @QueryParam("pageSize") int pageSize) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
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

  @PUT
  @Path("classes/{id}")
  public Response updateClass(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      OntologyClass c = JsonMapper.MAPPER.readValue(request.getInputStream(), OntologyClass.class);
      //c.setId(id);
      terminologyService.updateProvisionalClass(c, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @DELETE
  @Path("classes/{id}")
  public Response deleteClass(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = buildRequestContext();
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      terminologyService.deleteProvisionalClass(id, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

}
