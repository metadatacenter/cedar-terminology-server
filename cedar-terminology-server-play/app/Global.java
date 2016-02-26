import com.typesafe.config.ConfigFactory;
import play.*;
import play.libs.F.Promise;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import utils.Utils;

import java.io.File;
import java.lang.reflect.Method;

public class Global extends GlobalSettings {

  private final Logger.ALogger accessLogger = Logger.of("access");

  @Override
  public Configuration onLoadConfig(Configuration config, File path, ClassLoader classloader, Mode mode) {
    // Modifies the configuration according to the execution mode (DEV, TEST, PROD)
    if (mode.name().compareTo("TEST") == 0) {
      return new Configuration(ConfigFactory.load("application." + mode.name().toLowerCase() + ".conf"));
    } else {
      return onLoadConfig(config, path, classloader); // default implementation
    }
  }

  @Override
  public void onStart(Application app) {
  }

  @Override
  public void onStop(Application app) {
  }

  /* For CORS */

  private class ActionWrapper extends Action.Simple {
    public ActionWrapper(Action<?> action) {
      this.delegate = action;
    }

    @Override
    public Promise<Result> call(Http.Context ctx) throws java.lang.Throwable {
      Promise<Result> result = this.delegate.call(ctx);
      Http.Response response = ctx.response();
      response.setHeader("Access-Control-Allow-Origin", "*");
      return result;
    }
  }

  /* Log all requests */
  @Override
  @SuppressWarnings("rawtypes")
  public Action onRequest(Http.Request request, Method method) {
    String apiKey = null;
    if (Utils.isValidAuthorizationHeader(request)) {
      apiKey = Utils.getApiKeyFromHeader(request);
    }
    else {
      apiKey = "Invalid apiKey";
    }
    // Log request
    accessLogger.info("method=" + request.method() + " uri=" + request.uri()
        + " remote-address=" + request.remoteAddress() + " apiKey=" + apiKey);
    // The ActionWrapper is used for CORS
    return new ActionWrapper(super.onRequest(request, method));
  }

  //  @Override
//  public Action<?> onRequest(Http.Request request, java.lang.reflect.Method actionMethod) {
//    return new ActionWrapper(super.onRequest(request, actionMethod));
//  }

}