package org.metadatacenter.cedar.terminology.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
//import play.mvc.Http.Request;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class Utils
{
  public static String prettyPrint(Object o) throws IOException
  {
    ObjectMapper mapper = new ObjectMapper();
    mapper.enable(SerializationFeature.INDENT_OUTPUT);
    StringWriter sw = new StringWriter();
    mapper.writeValue(sw, o);
    return sw.toString();
  }

//  public static boolean isValidAuthorizationHeader(Request request)
//  {
//    if ((request.getHeader("Authorization") != null) && (request.getHeader("Authorization").split("=").length == 2))
//      return true;
//    else
//      return false;
//  }
//
//  public static String getApiKeyFromHeader(Request request)
//  {
//    return request.getHeader("Authorization").split("=")[1].trim();
//  }
//
//  public static String encodeIfNeeded(String uri) throws UnsupportedEncodingException
//  {
//    String decodedUri = URLDecoder.decode(uri, "UTF-8");
//    // It is necessary to encode it
//    if (uri.compareTo(decodedUri)==0)
//      return URLEncoder.encode(uri, "UTF-8");
//    // If was already encoded
//    else
//      return uri;
//  }
}


