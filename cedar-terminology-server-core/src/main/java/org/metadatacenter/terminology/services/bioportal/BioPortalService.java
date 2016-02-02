package org.metadatacenter.terminology.services.bioportal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terminology.services.bioportal.dao.OntologyClassDAO;
import org.metadatacenter.terminology.services.bioportal.dao.RelationDAO;
import org.metadatacenter.terminology.services.bioportal.domainObjects.OntologyClass;
import org.metadatacenter.terminology.services.bioportal.domainObjects.Relation;
import org.metadatacenter.terminology.services.bioportal.domainObjects.SearchResults;
import org.metadatacenter.terminology.services.util.Util;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.stream.Collectors;

import static org.metadatacenter.terminology.services.util.Constants.BP_SEARCH_BASE_URL;
import static org.metadatacenter.terminology.services.util.Constants.BP_SEARCH_SCOPE_ALL;
import static org.metadatacenter.terminology.services.util.Constants.BP_SEARCH_SCOPE_CLASSES;
import static org.metadatacenter.terminology.services.util.Constants.BP_SEARCH_SCOPE_VALUES;
import static org.metadatacenter.terminology.services.util.Constants.BP_SEARCH_SCOPE_VALUE_SETS;

public class BioPortalService implements org.metadatacenter.terminology.services.bioportal.IBioPortalService
{
  private final int connectTimeout;
  private final int socketTimeout;

  /**
   * @param connectTimeout
   * @param socketTimeout
   */
  public BioPortalService(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
  }

  /**
   * Search for ontology classes and value set items. Provisional classes are included.
   *
   * @param q
   * @param scope          Search scope
   * @param displayContext Turn off the JSON-LD context serialization. This will reduce the response size
   *                       significantly for some calls, speeding up transmission and parse time.
   * @param displayLinks   Turn off the hypermedia link serialization. This will reduce the response size
   *                       significantly for some calls, speeding up transmission and parse time.
   * @param apiKey
   * @return
   * @throws IOException
   */
  public SearchResults search(String q, List<String> scope, List<String> sources, int page, int pageSize,
    boolean displayContext, boolean displayLinks, String apiKey) throws IOException
  {
    // Encode query
    q = URLEncoder.encode(q, "UTF-8");
    // TODO: Check that provisional classes are actually returned
    // TODO: Add attribute with result type on the BioPortal side
    /** Build url **/
    String url = "";
    // Search for ontology classes, value sets and values
    if (scope.contains(BP_SEARCH_SCOPE_ALL) || (!scope.contains(BP_SEARCH_SCOPE_ALL) && scope.size() == 3)) {
      url = BP_SEARCH_BASE_URL + "?q=" + q + "&also_search_provisional=true&valueset_exclude_roots=false";
    } else if (scope.size() == 1) {
      // Search for ontology classes
      if (scope.contains(BP_SEARCH_SCOPE_CLASSES)) {
        url = BP_SEARCH_BASE_URL + "?q=" + q + "&also_search_provisional=true&ontology_types=ONTOLOGY";
      }
      // Search for value sets
      else if (scope.contains(BP_SEARCH_SCOPE_VALUE_SETS)) {
        url = BP_SEARCH_BASE_URL + "?q=" + q
          + "&also_search_provisional=true&ontology_types=VALUE_SET_COLLECTION&valueset_roots_only=true";
      }
      // Search for values in value sets
      else if ((scope.contains(BP_SEARCH_SCOPE_VALUES))) {
        url = BP_SEARCH_BASE_URL + "?q=" + q
          + "&also_search_provisional=true&ontology_types=VALUE_SET_COLLECTION&valueset_exclude_roots=true";
      }
    } else if (scope.size() == 2) {
      // TODO: This call is retrieving value sets only because the 'valueset_roots_only' parameter is restricting the
      // search to value sets and ignores ontology classes
      // Search for ontology classes and value sets
      if (scope.contains(BP_SEARCH_SCOPE_CLASSES) && scope.contains(BP_SEARCH_SCOPE_VALUE_SETS)) {
        url = BP_SEARCH_BASE_URL + "?q=" + q + "&also_search_provisional=true&valueset_roots_only=true";
      }
      // Search for ontology classes and values
      else if (scope.contains(BP_SEARCH_SCOPE_CLASSES) && scope.contains(BP_SEARCH_SCOPE_VALUES)) {
        url = BP_SEARCH_BASE_URL + "?q=" + q + "&also_search_provisional=true&valueset_exclude_roots=true";
      }
      // Search for value sets and values
      else if (scope.contains(BP_SEARCH_SCOPE_VALUE_SETS) && scope.contains(BP_SEARCH_SCOPE_VALUES)) {
        url = BP_SEARCH_BASE_URL + "?q=" + q + "&also_search_provisional=true&ontology_types=VALUE_SET_COLLECTION";
      }
    }

    /** Add sources **/
    if (sources.size() > 0) {
      url += "&ontologies=" + (sources.stream().map(number -> String.valueOf(number)).collect(Collectors.joining(",")));
    }

    /** Add page and pageSize **/
    url += "&page=" + page + "&pagesize=" + pageSize;

    /** Add displayContext and DisplayLinks **/
    url += "&display_context=" + displayContext + "&display_links=" + displayLinks;

    // Send request to the BioPortal API
    HttpResponse response = Request.Get(url).addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
      connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // The request has succeeded
    if (statusCode == 200) {
      String bpResponse = new String(EntityUtils.toByteArray(response.getEntity()));
      return(new ObjectMapper().readValue(bpResponse, SearchResults.class));
    } else {
      throw new HTTPException(statusCode);
    }
  }

  /** Class operations **/

  public OntologyClass createClass(OntologyClass c, String apiKey) throws IOException
  {
    OntologyClassDAO classDAO = new OntologyClassDAO(connectTimeout, socketTimeout);
    return classDAO.create(c, apiKey);
  }

  // TODO: extend it to work with regular classes
  public OntologyClass findClass(String id, String apiKey) throws IOException
  {
    OntologyClassDAO classDAO = new OntologyClassDAO(connectTimeout, socketTimeout);
    return classDAO.find(id, apiKey);
  }

  public List<OntologyClass> findAllProvisionalClasses(String ontology, String apiKey) throws IOException {
    OntologyClassDAO classDAO = new OntologyClassDAO(connectTimeout, socketTimeout);
    return classDAO.findAllProvisionalClasses(ontology, apiKey);
  }

  public OntologyClass updateClass(OntologyClass c, String apiKey) throws IOException {
    // TODO
    return null;
  }

  public void deleteClass(String classId, String apiKey) throws IOException {
    // TODO
  }

  /** Relation operations **/

  public Relation createRelation(Relation relation, String apiKey) throws IOException
  {
    RelationDAO relationDAO = new RelationDAO(connectTimeout, socketTimeout);
    return relationDAO.create(relation, apiKey);
  }

  public Relation findRelation(String id, String apiKey) throws IOException
  {
    RelationDAO relationDAO = new RelationDAO(connectTimeout, socketTimeout);
    return relationDAO.find(id, apiKey);
  }

  @Override public Relation deleteRelation(String id, String apiKey) throws IOException
  {
    // TODO
    return null;
  }

}

