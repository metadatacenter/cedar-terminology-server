package org.metadatacenter.terms.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.SearchResult;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

import static org.metadatacenter.cedar.terminology.util.Constants.*;

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

  // Generation of paginated results
  public static <T> PagedResults<T> generatePaginatedResults(List<T> allResults, int page, int pageSize) {
    List<T> relevantResults = new ArrayList<>();
    int pageCount = 0;
    int prevPage = 0;
    int nextPage = 0;
    if (allResults.size() > 0) {
      pageCount = (int) Math.ceil((double)allResults.size() / pageSize); // round up
      int startIndex = (page * pageSize) - pageSize;
      int endIndex = Math.min(startIndex + pageSize, allResults.size());
      if (startIndex >= endIndex) {
        page = 1;
        startIndex = 0;
      }
      relevantResults = allResults.subList(startIndex, endIndex); // Note that endIndex is exclusive
      prevPage = page > 1 ? page - 1 : 0;
      nextPage = (page * pageSize <= allResults.size()) ? (page + 1) : 0;
    }
    else {
      page = 0;
    }
    return new PagedResults(page, pageCount, relevantResults.size(), allResults.size(), prevPage, nextPage, relevantResults);
  }

  public static boolean isProvisionalClass(String classId) {
    if (classId.toLowerCase().contains("provisional_classes")) {
      return true;
    }
    if (!classId.contains("http")) { // Id in the form "5e785190-5cb9-0138-e099-005056010088"
      return true;
    }
    else {
      return false;
    }
  }

  public static List<SearchResult> filterByQuery(String query, List<SearchResult> searchResults) {
    List<SearchResult> selectedResults = new ArrayList<>();
    for (SearchResult result : searchResults) {
      if (result.getPrefLabel().toLowerCase().contains(query.toLowerCase())) { // Label contains the query
        selectedResults.add(result);
      }
    }
    return selectedResults;
  }

  /**
   * Sorts by prefLabel length
   * @param searchResults
   * @return
   */
  public static List<SearchResult> sortByPrefLabelLength(List<SearchResult> searchResults) {
    Collections.sort(searchResults,
        Comparator.comparing(SearchResult::getPrefLabel, Comparator.comparing(String::length)));
    return searchResults;
  }

}
