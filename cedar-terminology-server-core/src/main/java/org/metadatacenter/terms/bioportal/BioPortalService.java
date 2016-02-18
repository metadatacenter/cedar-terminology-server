package org.metadatacenter.terms.bioportal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.dao.BpClassDAO;
import org.metadatacenter.terms.bioportal.dao.BpProvisionalClassDAO;
import org.metadatacenter.terms.bioportal.dao.BpProvisionalRelationDAO;
import org.metadatacenter.terms.bioportal.domainObjects.BpClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalRelation;
import org.metadatacenter.terms.bioportal.customObjects.BpSearchResults;
import org.metadatacenter.terms.customObjects.SearchResults;
import org.metadatacenter.terms.util.Util;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.stream.Collectors;

import static org.metadatacenter.terms.util.Constants.BP_SEARCH_BASE_URL;
import static org.metadatacenter.terms.util.Constants.BP_SEARCH_SCOPE_ALL;
import static org.metadatacenter.terms.util.Constants.BP_SEARCH_SCOPE_CLASSES;
import static org.metadatacenter.terms.util.Constants.BP_SEARCH_SCOPE_VALUES;
import static org.metadatacenter.terms.util.Constants.BP_SEARCH_SCOPE_VALUE_SETS;

public class BioPortalService implements IBioPortalService
{
  private final int connectTimeout;
  private final int socketTimeout;

  private BpProvisionalClassDAO bpProvClassDAO;
  private BpProvisionalRelationDAO bpProvRelationDAO;
  private BpClassDAO bpClassDAO;

  /**
   * @param connectTimeout
   * @param socketTimeout
   */
  public BioPortalService(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
    this.bpProvClassDAO = new BpProvisionalClassDAO(connectTimeout, socketTimeout);
    this.bpProvRelationDAO = new BpProvisionalRelationDAO(connectTimeout, socketTimeout);
    this.bpClassDAO = new BpClassDAO(connectTimeout, socketTimeout);
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
  public BpSearchResults<BpClass> search(String q, List<String> scope, List<String> sources, int page, int pageSize,
    boolean displayContext, boolean displayLinks, String apiKey) throws IOException
  {
    // Encode query
    q = Util.encodeIfNeeded(q);
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
      ObjectMapper mapper = new ObjectMapper();
      JsonNode bpResult = mapper.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return mapper.readValue(mapper.treeAsTokens(bpResult), new TypeReference<BpSearchResults<BpClass>>() {});
    } else {
      throw new HTTPException(statusCode);
    }
  }

  /**
   * Provisional Classes
   **/

  public BpProvisionalClass createBpProvisionalClass(BpProvisionalClass c, String apiKey) throws IOException
  {
    return bpProvClassDAO.create(c, apiKey);
  }

  public BpProvisionalClass findBpProvisionalClassById(String id, String apiKey) throws IOException
  {
    return bpProvClassDAO.find(id, apiKey);
  }

  public List<BpProvisionalClass> findAllProvisionalClasses(String ontology, String apiKey) throws IOException
  {
    return bpProvClassDAO.findAll(ontology, apiKey);
  }

  public void updateProvisionalClass(BpProvisionalClass c, String apiKey) throws IOException {
    bpProvClassDAO.update(c, apiKey);
  }

  public void deleteProvisionalClass(String id, String apiKey) throws IOException {
    bpProvClassDAO.delete(id, apiKey);
  }

  public BpProvisionalRelation createBpProvisionalRelation(BpProvisionalRelation pr, String apiKey) throws IOException
  {
    return bpProvRelationDAO.create(pr, apiKey);
  }

  public BpProvisionalRelation findProvisionalRelationById(String id, String apiKey) throws IOException
  {
    return bpProvRelationDAO.find(id, apiKey);
  }

  public BpSearchResults<BpClass> findValueSetsByValueSetCollection(String vsCollection, String apiKey)
    throws IOException
  {
    return bpClassDAO.findValueSetsByValueSetCollection(vsCollection, apiKey);
  }

  public BpSearchResults<BpClass> findValuesByValueSet(String vsId, String vsCollection, String apiKey)
    throws IOException
  {
    return bpClassDAO.findValuesByValueSet(vsId, vsCollection, apiKey);
  }

}

