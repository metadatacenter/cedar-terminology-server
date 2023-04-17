package org.metadatacenter.cedar.terminology.utils.logging;

import org.metadatacenter.http.CedarResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Response;
import java.io.IOException;

public class LogResponseFilter implements ContainerResponseFilter {

protected final Logger log = LoggerFactory.getLogger("HTTP Response");

  @Override
  public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext
      containerResponseContext) throws IOException {
    int statusCode = containerResponseContext.getStatus();
    String message = "Status: " + containerResponseContext.getStatus();

    if (containerResponseContext.getEntity() != null) {
      message += "; " + containerResponseContext.getEntity().toString();
    }

    if (statusCode == CedarResponseStatus.INTERNAL_SERVER_ERROR.getStatusCode()) {
      log.error(message);
    }
    else {
      //log.info(message);
    }

  }
}
