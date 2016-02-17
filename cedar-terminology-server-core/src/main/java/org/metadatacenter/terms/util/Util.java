package org.metadatacenter.terms.util;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.BP_VS_COLLECTIONS_BASE_URL;
import static org.metadatacenter.terms.util.Constants.BP_VS_COLLECTIONS;
import static org.metadatacenter.terms.util.Constants.BP_VS_CREATION_COLLECTIONS;

public class Util
{

  public static String getBioPortalAuthHeader(String apikey)
  {
    return "apikey token=" + apikey;
  }

  public static String encodeIfNeeded(String uri) throws UnsupportedEncodingException
  {
    String decodedUri = URLDecoder.decode(uri, "UTF-8");
    // It is necessary to encode it
    if (uri.compareTo(decodedUri) == 0)
      return URLEncoder.encode(uri, "UTF-8");
      // If was already encoded
    else
      return uri;
  }

  /**
   * Check that the value set collection is appropriate. Currently, creation is restricted to
   * the CEDARVS collection
   */
  public static boolean validVsCollection(String vsCollection, boolean creation)
  {
    if ((vsCollection == null) || (vsCollection.length() == 0))
      return false;
    List<String> validCollections;
    if (creation)
      validCollections = Arrays.asList(BP_VS_CREATION_COLLECTIONS);
    else
      validCollections = Arrays.asList(BP_VS_COLLECTIONS);

    for (String vc : validCollections) {
      if ((vsCollection.compareTo(vc) == 0) || (vsCollection.compareTo(BP_VS_COLLECTIONS_BASE_URL + vc) == 0)) {
        return true;
      }
    }
    return false;
  }

  // TODO: This is not a good practice. Use org.apache.commons.validator.routines.UrlValidator instead.
  public static boolean isUrl(String url)
  {
    try {
      new URL(url);
    } catch (MalformedURLException e) {
      return false;
    }
    return true;
  }

  /**
   * This method checks if a resource identifier is in the short format expected by the BioPortal API (i.e.,
   * a909bab0-b0d1-0133-981f-005056010074 instead of http://data.bioontology
   * .org/provisional_classes/a909bab0-b0d1-0133-981f-005056010074". If the id is expressed in long format, the
   * method returns the short version of it.
   */
  public static String getShortIdentifier(String id) {
    if (isUrl(id)) {
      if (id.contains("http://data.bioontology.org/provisional")) {
        return id.substring(id.lastIndexOf("/") + 1, id.length());
      }
    }
    return id;
  }

}
