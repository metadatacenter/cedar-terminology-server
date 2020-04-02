package org.metadatacenter.terms.bioportal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.dao.*;
import org.metadatacenter.terms.bioportal.domainObjects.*;
import org.metadatacenter.terms.util.Util;

import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.metadatacenter.terms.util.Constants.*;
import static org.metadatacenter.util.json.JsonMapper.MAPPER;


public class BioPortalService implements IBioPortalService {
  private final int connectTimeout;
  private final int socketTimeout;
  private BpProvisionalClassDAO bpProvClassDAO;
  private BpProvisionalRelationDAO bpProvRelationDAO;
  private BpClassDAO bpClassDAO;
  private BpOntologyDAO bpOntologyDAO;
  private BpPropertyDAO bpPropertyDAO;

  /**
   * @param connectTimeout
   * @param socketTimeout
   */
  public BioPortalService(int connectTimeout, int socketTimeout) {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
    this.bpProvClassDAO = new BpProvisionalClassDAO(connectTimeout, socketTimeout);
    this.bpProvRelationDAO = new BpProvisionalRelationDAO(connectTimeout, socketTimeout);
    this.bpClassDAO = new BpClassDAO(connectTimeout, socketTimeout);
    this.bpOntologyDAO = new BpOntologyDAO(connectTimeout, socketTimeout);
    this.bpPropertyDAO = new BpPropertyDAO(connectTimeout, socketTimeout);
  }

  /**
   * Search for ontology classes, value sets, and value set items. Provisional classes are included.
   */
  public BpPagedResults<BpClass> search(String q, List<String> scope, List<String> sources, boolean suggest,
                                        String source, String subtreeRootId, int maxDepth, int page, int pageSize,
                                        boolean displayContext, boolean displayLinks, String apiKey) throws IOException {
    // Encode query
    q = Util.encodeIfNeeded(q);
    /** Build url **/
    String url = "";
    // Search for ontology classes, value sets and values
    if (scope.contains(BP_SEARCH_SCOPE_ALL) || (!scope.contains(BP_SEARCH_SCOPE_ALL) && scope.size() == 3)) {
      url = BP_API_BASE + BP_SEARCH + "?q=" + q + "&also_search_provisional=true&valueset_exclude_roots=false";
    } else if (scope.size() == 1) {
      // Search for ontology classes
      if (scope.contains(BP_SEARCH_SCOPE_CLASSES)) {
        url = BP_API_BASE + BP_SEARCH + "?q=" + q + "&also_search_provisional=true&ontology_types=ONTOLOGY";
      }
      // Search for value sets
      else if (scope.contains(BP_SEARCH_SCOPE_VALUE_SETS)) {
        url = BP_API_BASE + BP_SEARCH + "?q=" + q
            + "&also_search_provisional=true&ontology_types=VALUE_SET_COLLECTION&valueset_roots_only=true";
      }
      // Search for values in value sets
      else if ((scope.contains(BP_SEARCH_SCOPE_VALUES))) {
        url = BP_API_BASE + BP_SEARCH + "?q=" + q
            + "&also_search_provisional=true&ontology_types=VALUE_SET_COLLECTION&valueset_exclude_roots=true";
      }
    } else if (scope.size() == 2) {
      // TODO: This call is retrieving value sets only because the 'valueset_roots_only' parameter is restricting the
      // search to value sets and ignores ontology classes
      // Search for ontology classes and value sets
      if (scope.contains(BP_SEARCH_SCOPE_CLASSES) && scope.contains(BP_SEARCH_SCOPE_VALUE_SETS)) {
        url = BP_API_BASE + BP_SEARCH + "?q=" + q + "&also_search_provisional=true&valueset_roots_only=true";
      }
      // Search for ontology classes and values
      else if (scope.contains(BP_SEARCH_SCOPE_CLASSES) && scope.contains(BP_SEARCH_SCOPE_VALUES)) {
        url = BP_API_BASE + BP_SEARCH + "?q=" + q + "&also_search_provisional=true&valueset_exclude_roots=true";
      }
      // Search for value sets and values
      else if (scope.contains(BP_SEARCH_SCOPE_VALUE_SETS) && scope.contains(BP_SEARCH_SCOPE_VALUES)) {
        url = BP_API_BASE + BP_SEARCH + "?q=" + q + "&also_search_provisional=true&ontology_types=VALUE_SET_COLLECTION";
      }
    }

    /** Add sources **/
    if (sources.size() > 0) {
      url += "&ontologies=" + (sources.stream().map(number -> String.valueOf(number)).collect(Collectors.joining(",")));
    }

    /** Add suggest **/
    if (suggest == true) {
      url += "&suggest=true";
    }

    /** Add source **/
    if (source != null) {
      url += "&ontology=" + source;
      /** Add subtreeRootId **/
      if (subtreeRootId != null) {
        url += "&subtree_root_id=" + Util.encodeIfNeeded(subtreeRootId);
        /** Add maxDepth **/
        url += "&max_depth=" + maxDepth;
      }
    }

    /** Enable 'also_search_properties' for a more flexible search. Enabling this feature makes it possible to find,
     * for example, the 'Audio Disc' class from BIBLIOTEK-O (http://bibliotek-o.org/1.1/ontology/AudioDisc) when
     * searching for 'audiodisc' **/
    url += "&also_search_properties=true";

    /** Include additional information **/
    url += "&include=prefLabel,definition";

    /** Add page and pageSize **/
    url += "&page=" + page + "&pagesize=" + pageSize;

    /** Add displayContext and DisplayLinks **/
    url += "&display_context=" + displayContext + "&display_links=" + displayLinks;

    System.out.println("Search url: " + url);

    // Send request to the BioPortal API
    HttpResponse response = Request.Get(url).addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // The request has succeeded
    if (statusCode == Response.Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<BpPagedResults<BpClass>>() {
      });
    } else {
      throw new HTTPException(statusCode);
    }
  }

  /**
   * Search for ontology properties.
   */
  public BpPagedResults<BpProperty> propertySearch(String q, List<String> sources, boolean exactMatch, boolean
      requireDefinitions, int page, int pageSize, boolean displayContext, boolean displayLinks, String apiKey) throws IOException {
    // Encode query
    q = Util.encodeIfNeeded(q);
    /** Build url **/
    String url = BP_API_BASE + BP_PROPERTY_SEARCH + "?q=" + q;

    /** Add sources **/
    if (sources.size() > 0) {
      url += "&ontologies=" + (sources.stream().map(number -> String.valueOf(number)).collect(Collectors.joining(",")));
    }

    /** Add exactMatch **/
    if (exactMatch == true) {
      url += "&require_exact_match=true";
    }

    /** Add requireDefinitions **/
    if (requireDefinitions == true) {
      url += "&require_definitions=true";
    }

    /** Add page and pageSize **/
    url += "&page=" + page + "&pagesize=" + pageSize;

    /** Add displayContext and DisplayLinks **/
    url += "&display_context=" + displayContext + "&display_links=" + displayLinks;

    System.out.println("Search url: " + url);

    // Send request to the BioPortal API
    HttpResponse response = Request.Get(url).addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
        connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();

    int statusCode = response.getStatusLine().getStatusCode();
    // The request has succeeded
    if (statusCode == Response.Status.OK.getStatusCode()) {
      JsonNode bpResult = MAPPER.readTree(new String(EntityUtils.toByteArray(response.getEntity())));
      return MAPPER.readValue(MAPPER.treeAsTokens(bpResult), new TypeReference<BpPagedResults<BpProperty>>() {
      });
    } else {
      throw new HTTPException(statusCode);
    }
  }

  /**
   * Ontologies
   */

  public BpOntology findBpOntologyById(String id, String apiKey) throws IOException {
    return bpOntologyDAO.find(id, apiKey);
  }

  public BpOntologySubmission getOntologyLatestSubmission(String id, String apiKey) throws IOException {
    return bpOntologyDAO.getLatestSubmission(id, apiKey);
  }

  public List<BpOntology> findAllOntologies(String apiKey) throws IOException {
    return bpOntologyDAO.findAll(apiKey);
  }

  public BpOntologyMetrics findOntologyMetrics(String id, String apiKey) throws IOException {
    return bpOntologyDAO.getOntologyMetrics(id, apiKey);
  }

  public List<BpOntologyCategory> findOntologyCategories(String id, String apiKey) throws IOException {
    return bpOntologyDAO.getOntologyCategories(id, apiKey);
  }

  public List<BpClass> getRootClasses(String ontologyId, String apiKey) throws IOException {
    return bpOntologyDAO.getRootClasses(ontologyId, apiKey);
  }

  public List<BpProperty> getRootProperties(String ontologyId, String apiKey) throws IOException {
    return bpOntologyDAO.getRootProperties(ontologyId, apiKey);
  }

  /**
   * Classes
   */

  public BpClass findBpClassById(String id, String ontology, String apiKey) throws IOException {
    return bpClassDAO.find(id, ontology, apiKey);
  }

  public BpPagedResults<BpClass> findAllClassesInOntology(String ontology, int page, int pageSize, String apiKey) throws IOException {
    return bpClassDAO.findAllClassesInOntology(ontology, page, pageSize, apiKey);
  }

  public List<BpTreeNode> getClassTree(String id, String ontology, String apiKey) throws IOException {
    return bpClassDAO.getTree(id, ontology, apiKey);
  }

  public BpPagedResults<BpClass> getClassChildren(String id, String ontology, int page, int pageSize, String apiKey) throws IOException {
    return bpClassDAO.getChildren(id, ontology, page, pageSize, apiKey);
  }

  public BpPagedResults<BpClass> getClassDescendants(String id, String ontology, int page, int pageSize,
                                                     String apiKey) throws IOException {
    return bpClassDAO.getDescendants(id, ontology, page, pageSize, apiKey);
  }

  public List<BpClass> getClassParents(String id, String ontology, String apiKey) throws IOException {
    return bpClassDAO.getParents(id, ontology, apiKey);
  }

  public BpProvisionalClass createBpProvisionalClass(BpProvisionalClass c, String apiKey) throws IOException {
    return bpProvClassDAO.create(c, apiKey);
  }

  public BpProvisionalClass findBpProvisionalClassById(String id, String apiKey) throws IOException {
    return bpProvClassDAO.find(id, apiKey);
  }

  public BpPagedResults<BpProvisionalClass> findAllProvisionalClasses(String ontology, int page, int pageSize,
                                                                      String apiKey) throws IOException {
    return bpProvClassDAO.findAll(ontology, page, pageSize, apiKey);
  }

  public void updateProvisionalClass(BpProvisionalClass c, String apiKey) throws IOException {
    bpProvClassDAO.update(c, apiKey);
  }

  public void deleteProvisionalClass(String id, String apiKey) throws IOException {
    bpProvClassDAO.delete(id, apiKey);
  }

  /**
   * Relations
   */

  public BpProvisionalRelation createBpProvisionalRelation(BpProvisionalRelation pr, String apiKey) throws IOException {
    return bpProvRelationDAO.create(pr, apiKey);
  }

  public BpProvisionalRelation findProvisionalRelationById(String id, String apiKey) throws IOException {
    return bpProvRelationDAO.find(id, apiKey);
  }

//  public void updateProvisionalRelation(BpProvisionalRelation r, String apiKey) throws IOException {
//    bpProvRelationDAO.update(r, apiKey);
//  }

  public void deleteProvisionalRelation(String id, String apiKey) throws IOException {
    bpProvRelationDAO.delete(id, apiKey);
  }

  /**
   * Value Sets
   */

  public BpPagedResults<BpClass> findValueSetsByValueSetCollection(String vsCollection, int page, int pageSize,
                                                                   String apiKey)
      throws IOException {
    return bpClassDAO.findValueSetsByValueSetCollection(vsCollection, page, pageSize, apiKey);
  }

  public BpPagedResults<BpClass> findValuesByValueSet(String vsId, String vsCollection, int page, int pageSize,
                                                      String apiKey)
      throws IOException {
    return bpClassDAO.findValuesByValueSet(vsId, vsCollection, page, pageSize, apiKey);
  }

  /**
   * Properties
   */

  public BpProperty findBpPropertyById(String id, String ontology, String apiKey) throws IOException {
    return bpPropertyDAO.find(id, ontology, apiKey);
  }

  public List<BpProperty> findAllPropertiesInOntology(String ontology, String apiKey) throws IOException {
    return bpPropertyDAO.findAllPropertiesInOntology(ontology, apiKey);
  }

  public List<BpTreeNode> getPropertyTree(String id, String ontology, String apiKey) throws IOException {
    return bpPropertyDAO.getTree(id, ontology, apiKey);
  }

  public List<BpProperty> getPropertyChildren(String id, String ontology, String apiKey) throws IOException {
    return bpPropertyDAO.getChildren(id, ontology, apiKey);
  }

  public List<BpProperty> getPropertyDescendants(String id, String ontology, String apiKey) throws IOException {
    return bpPropertyDAO.getDescendants(id, ontology, apiKey);
  }

  public List<BpProperty> getPropertyParents(String id, String ontology, String apiKey) throws IOException {
    return bpPropertyDAO.getParents(id, ontology, apiKey);
  }

}

