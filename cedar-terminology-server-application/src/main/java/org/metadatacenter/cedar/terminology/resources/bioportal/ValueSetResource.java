package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.cedar.cache.Cache;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.TreeNode;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.terms.domainObjects.ValueSet;
import org.metadatacenter.util.json.JsonMapper;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

@Path("/bioportal")
@Produces(MediaType.APPLICATION_JSON)
public class ValueSetResource extends AbstractTerminologyServerResource {

  public ValueSetResource(CedarConfig cedarConfig) {
    super(cedarConfig);
  }

  @POST
  @Path("vs-collections/{vs_collection}/value-sets")
  public Response createValueSet(@PathParam("vs_collection") String vsCollection) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      ValueSet vs = JsonMapper.MAPPER.convertValue(ctx.request().getRequestBody().asJson(), ValueSet.class);
      vs.setVsCollection(vsCollection);
      ValueSet createdValueSet = terminologyService.createProvisionalValueSet(vs, apiKey);
      JsonNode createdValueSetJson = JsonMapper.MAPPER.valueToTree(createdValueSet);
      // Refresh value sets cache
      Cache.valueSetsCache.refresh("value-sets");
      return Response.created(new URI(createdValueSet.getLdId())).entity(createdValueSetJson).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (URISyntaxException | IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/value-sets/{id}")
  public Response findValueSet(@PathParam("id") @Encoded String id, @PathParam("vs_collection") String vsCollection)
      throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      ValueSet vs = terminologyService.findValueSet(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(vs)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/value-sets")
  public Response findValueSetsByVsCollection(@PathParam("vs_collection") String vsCollection,
                                              @QueryParam("page") @DefaultValue("1") int page,
                                              @QueryParam("pageSize") int pageSize) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    // If pageSize not defined, set default value
    if (pageSize == 0) {
      pageSize = defaultPageSize;
    }
    try {
      PagedResults<ValueSet> valueSets = terminologyService.findValueSetsByVsCollection(vsCollection, page, pageSize, apiKey);
      // This line ensures that @class type annotations are included for each element in the collection
      //ObjectWriter writer = JsonMapper.MAPPER.writerFor(new TypeReference<PagedResults<ValueSet>>() {});
      //return Response.ok().entity(JsonMapper.MAPPER.valueToTree(writer.writeValueAsString(valueSets))).build();
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(valueSets)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/values/{id}/value-set")
  public Response findValueSetByValue(@PathParam("id") @Encoded String id,
                                      @PathParam("vs_collection") String vsCollection) throws CedarException {
    try {
      ValueSet vs = terminologyService.findValueSetByValue(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(vs)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("vs-collections/{vs_collection}/value-sets/{id}/tree")
  public Response findValueSetTree(@PathParam("id") @Encoded String id, @PathParam("vs_collection") String vsCollection)
      throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      TreeNode tree = terminologyService.getValueSetTree(id, vsCollection, apiKey);
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(tree)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @GET
  @Path("value-sets")
  public Response findAllValueSets() throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      List<ValueSet> valueSets = new ArrayList<>(Cache.valueSetsCache.get("value-sets").values());
      return Response.ok().entity(JsonMapper.MAPPER.valueToTree(valueSets)).build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (ExecutionException e) {
      throw new CedarAssertionException(e);
    }
  }

  @PUT
  @Path("value-sets/{id}")
  public Response updateValueSet(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      ValueSet vs = JsonMapper.MAPPER.readValue(request.getInputStream(), ValueSet.class);
      terminologyService.updateProvisionalValueSet(vs, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

  @DELETE
  @Path("value-sets/{id}")
  public Response deleteValueSet(@PathParam("id") String id) throws CedarException {
    CedarRequestContext ctx = CedarRequestContextFactory.fromRequest(request);
    ctx.must(ctx.user()).be(LoggedIn);
    try {
      terminologyService.deleteProvisionalValueSet(id, apiKey);
      return Response.noContent().build();
    } catch (HTTPException e) {
      return Response.status(e.getStatusCode()).build();
    } catch (IOException e) {
      throw new CedarAssertionException(e);
    }
  }

}