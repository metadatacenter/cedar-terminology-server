package controllers;

import cache.Cache;
import play.Configuration;
import play.Play;
import play.mvc.Controller;
import play.mvc.Result;

public class Application extends Controller
{

  public static Configuration config;

  static {
    config = Play.application().configuration();
    // Initialize cache
    Cache.init();
  }

  public static Result index()
  {
    return ok("CEDAR Terminology Server");
  }

  /* For CORS */
  public static Result preflight(String all)
  {
    response().setHeader("Access-Control-Allow-Origin", "*");
    response().setHeader("Allow", "*");
    response().setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, PATCH, DELETE, OPTIONS");
    response()
      .setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Referer, User-Agent, Authorization");
    return ok();
  }

}
