package org.metadatacenter.cedar.terminology.resources;

import com.codahale.metrics.annotation.Timed;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.IdentityHashMap;
import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class IndexResource {

  private final static Map<String, Object> info;

  static {
    info = new IdentityHashMap<>();
    info.put("name", "CEDAR ValueRecommender Server");
  }

  public IndexResource() {
  }

  @GET
  @Timed
  public Map<String, Object> showInfo() {
    return info;
  }
}