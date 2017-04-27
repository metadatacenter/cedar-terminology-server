package org.metadatacenter.cedar.terminology.utils.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import java.io.IOException;

public class LogRequestFilter implements ContainerRequestFilter {

protected final Logger log = LoggerFactory.getLogger("Requests");

  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    String message = "[*** Request ***]: ";
    message += containerRequestContext.getMethod() + " ";
    message += containerRequestContext.getUriInfo().getAbsolutePath();
    log.info(message);
  }
}