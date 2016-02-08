package org.metadatacenter.terms.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

public class Util
{

  public static String getBioPortalAuthHeader(String apikey) {
    return "apikey token=" + apikey;
  }

  public static String encodeIfNeeded(String uri) throws UnsupportedEncodingException
  {
    String decodedUri = URLDecoder.decode(uri, "UTF-8");
    // It is necessary to encode it
    if (uri.compareTo(decodedUri)==0)
      return URLEncoder.encode(uri, "UTF-8");
      // If was already encoded
    else
      return uri;
  }

}
