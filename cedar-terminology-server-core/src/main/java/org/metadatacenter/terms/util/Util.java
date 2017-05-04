package org.metadatacenter.terms.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.terms.bioportal.domainObjects.BpProperty;
import org.metadatacenter.terms.bioportal.domainObjects.BpTreeNode;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.*;
import static org.metadatacenter.util.json.JsonMapper.MAPPER;

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
      validCollections = Arrays.asList(BP_VS_COLLECTIONS_WRITE);
    else
      validCollections = Arrays.asList(BP_VS_COLLECTIONS_READ);

    for (String vc : validCollections) {
      if ((vsCollection.compareTo(vc) == 0) || (vsCollection.compareTo(BP_API_BASE + BP_VS_COLLECTIONS + vc) == 0)) {
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
      String shortId = id.substring(id.lastIndexOf("/") + 1, id.length());
      if (shortId.contains("#")) {
        shortId = shortId.substring(shortId.lastIndexOf("#") + 1, shortId.length());
      }
      return shortId;
    }
    return id;
  }

  /**
   * http://www.w3.org/2002/07/owl#ObjectProperty" -> ObjectProperty
   */
  public static String getShortType(String type) {
    if (isUrl(type)) {
      return type.substring(type.lastIndexOf("#") + 1, type.length());
    }
    return type;
  }

  /**
   * Generates  property preferred label from a JsonNode that contains a property tree node
   */
  public static String generatePreferredLabel(JsonNode propertyNode) {
    final String prefLabelProperty = "prefLabel";
    final String labelsProperty = "label";
    final String idProperty = "@id";
    if (propertyNode.hasNonNull(prefLabelProperty) && propertyNode.get(prefLabelProperty).asText().length() > 0) {
      return propertyNode.get(prefLabelProperty).asText();
    }
    if (propertyNode.hasNonNull(labelsProperty) && propertyNode.get(labelsProperty).size() > 0) {
      return propertyNode.get(labelsProperty).get(0).asText();
    }
    else if (propertyNode.hasNonNull(idProperty)) {
      return Util.getShortIdentifier(propertyNode.get(idProperty).asText());
    }
    else return "Label not available";
  }

  public static boolean isProperty(String type) {
    type = getShortType(type);
    if (type.equals(OBJECT_PROPERTY) || type.equals(ANNOTATION_PROPERTY) || type.equals(DATATYPE_PROPERTY)) {
      return true;
    }
    else {
      return false;
    }
  }

}
