package org.metadatacenter.terms;

import org.metadatacenter.terms.bioportal.BioPortalService;
import org.metadatacenter.terms.bioportal.domainObjects.BpClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalRelation;
import org.metadatacenter.terms.bioportal.customObjects.BpSearchResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;
import org.metadatacenter.terms.customObjects.SearchResults;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.terms.domainObjects.ValueSet;
import org.metadatacenter.terms.util.ObjectConverter;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.BP_VS_COLLECTIONS;
import static org.metadatacenter.terms.util.Constants.BP_VS_CREATION_COLLECTIONS;

public class TerminologyService implements ITerminologyService
{

  private BioPortalService bpService;

  public TerminologyService(int connectTimeout, int socketTimeout)
  {
    this.bpService = new BioPortalService(connectTimeout, socketTimeout);
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
//  public SearchResults search(String q, List<String> scope, List<String> sources, int page, int pageSize,
//    boolean displayContext, boolean displayLinks, String apiKey) throws IOException
//  {
//    // Encode query
//    q = URLEncoder.encode(q, "UTF-8");
//    // TODO: Check that provisional classes are actually returned
//    // TODO: Add attribute with result type on the BioPortal side
//    /** Build url **/
//    String url = "";
//    // Search for ontology classes, value sets and values
//    if (scope.contains(BP_SEARCH_SCOPE_ALL) || (!scope.contains(BP_SEARCH_SCOPE_ALL) && scope.size() == 3)) {
//      url = BP_SEARCH_BASE_URL + "?q=" + q + "&also_search_provisional=true&valueset_exclude_roots=false";
//    } else if (scope.size() == 1) {
//      // Search for ontology classes
//      if (scope.contains(BP_SEARCH_SCOPE_CLASSES)) {
//        url = BP_SEARCH_BASE_URL + "?q=" + q + "&also_search_provisional=true&ontology_types=ONTOLOGY";
//      }
//      // Search for value sets
//      else if (scope.contains(BP_SEARCH_SCOPE_VALUE_SETS)) {
//        url = BP_SEARCH_BASE_URL + "?q=" + q
//          + "&also_search_provisional=true&ontology_types=VALUE_SET_COLLECTION&valueset_roots_only=true";
//      }
//      // Search for values in value sets
//      else if ((scope.contains(BP_SEARCH_SCOPE_VALUES))) {
//        url = BP_SEARCH_BASE_URL + "?q=" + q
//          + "&also_search_provisional=true&ontology_types=VALUE_SET_COLLECTION&valueset_exclude_roots=true";
//      }
//    } else if (scope.size() == 2) {
//      // TODO: This call is retrieving value sets only because the 'valueset_roots_only' parameter is restricting the
//      // search to value sets and ignores ontology classes
//      // Search for ontology classes and value sets
//      if (scope.contains(BP_SEARCH_SCOPE_CLASSES) && scope.contains(BP_SEARCH_SCOPE_VALUE_SETS)) {
//        url = BP_SEARCH_BASE_URL + "?q=" + q + "&also_search_provisional=true&valueset_roots_only=true";
//      }
//      // Search for ontology classes and values
//      else if (scope.contains(BP_SEARCH_SCOPE_CLASSES) && scope.contains(BP_SEARCH_SCOPE_VALUES)) {
//        url = BP_SEARCH_BASE_URL + "?q=" + q + "&also_search_provisional=true&valueset_exclude_roots=true";
//      }
//      // Search for value sets and values
//      else if (scope.contains(BP_SEARCH_SCOPE_VALUE_SETS) && scope.contains(BP_SEARCH_SCOPE_VALUES)) {
//        url = BP_SEARCH_BASE_URL + "?q=" + q + "&also_search_provisional=true&ontology_types=VALUE_SET_COLLECTION";
//      }
//    }
//
//    /** Add sources **/
//    if (sources.size() > 0) {
//      url += "&ontologies=" + (sources.stream().map(number -> String.valueOf(number)).collect(Collectors.joining(",")));
//    }
//
//    /** Add page and pageSize **/
//    url += "&page=" + page + "&pagesize=" + pageSize;
//
//    /** Add displayContext and DisplayLinks **/
//    url += "&display_context=" + displayContext + "&display_links=" + displayLinks;
//
//    // Send request to the BioPortal API
//    HttpResponse response = Request.Get(url).addHeader("Authorization", Util.getBioPortalAuthHeader(apiKey)).
//      connectTimeout(connectTimeout).socketTimeout(socketTimeout).execute().returnResponse();
//
//    int statusCode = response.getStatusLine().getStatusCode();
//    // The request has succeeded
//    if (statusCode == 200) {
//      String bpResponse = new String(EntityUtils.toByteArray(response.getEntity()));
//      return(new ObjectMapper().readValue(bpResponse, SearchResults.class));
//    } else {
//      throw new HTTPException(statusCode);
//    }
//  }
//
  /** Classes **/

  public OntologyClass createProvisionalClass(OntologyClass c, String apiKey) throws IOException
  {
    BpProvisionalClass pc = bpService.createBpProvisionalClass(ObjectConverter.toBpProvisionalClass(c), apiKey);
    return ObjectConverter.toOntologyClass(pc);
  }

  public OntologyClass findProvisionalClass(String id, String apiKey) throws IOException
  {
    BpProvisionalClass pc = bpService.findBpProvisionalClassById(id, apiKey);
    return ObjectConverter.toOntologyClass(pc);
  }

  public List<OntologyClass> findAllProvisionalClasses(String ontology, String apiKey) throws IOException
  {
    List<BpProvisionalClass> provClasses = bpService.findAllProvisionalClasses(ontology, apiKey);
    List<OntologyClass> classes = new ArrayList<>();
    for (BpProvisionalClass pc : provClasses) {
      classes.add(ObjectConverter.toOntologyClass(pc));
    }
    return classes;
  }

  /** Relations **/

  public Relation createProvisionalRelation(Relation r, String apiKey) throws IOException
  {
    BpProvisionalRelation pr = bpService
      .createBpProvisionalRelation(ObjectConverter.toBpProvisionalRelation(r), apiKey);
    return ObjectConverter.toRelation(pr);
  }

  public Relation findProvisionalRelation(String id, String apiKey) throws IOException
  {
    BpProvisionalRelation pr = bpService.findProvisionalRelationById(id, apiKey);
    return ObjectConverter.toRelation(pr);
  }

  /**
   * Value Sets
   **/

  public ValueSet createProvisionalValueSet(ValueSet vs, String apiKey) throws IOException
  {
    // Creation of value sets is restricted to the CEDARVS value set collection
    if ((vs.getVsCollection() != null) && (Arrays.asList(BP_VS_CREATION_COLLECTIONS).contains(vs.getVsCollection()))) {
      BpProvisionalClass pc = bpService.createBpProvisionalClass(ObjectConverter.toBpProvisionalClass(vs), apiKey);
      return ObjectConverter.toValueSet(pc);
    } else {
      // Bad request
      throw new HTTPException(400);
    }
  }

  public ValueSet findProvisionalValueSet(String id, String apiKey) throws IOException
  {
    BpProvisionalClass pc = bpService.findBpProvisionalClassById(id, apiKey);
    return ObjectConverter.toValueSet(pc);
  }

  public SearchResults<ValueSet> findValueSetsByVsCollection(String vsCollection, String apiKey) throws IOException
  {
    // Check that vsCollection is a valid Value Set Collection
    if ((vsCollection != null) && (Arrays.asList(BP_VS_COLLECTIONS).contains(vsCollection))) {
      BpSearchResults<BpClass> bpResults = bpService.findValueSetsByValueSetCollection(vsCollection, apiKey);
      return ObjectConverter.toValueSetResults(bpResults);
    } else {
      // Bad request
      throw new HTTPException(400);
    }
  }

  public SearchResults<Value> findValuesByValueSet(String vsId, String vsCollection, String apiKey) throws IOException
  {
    // Check that vsCollection is a valid Value Set Collection
    if ((vsCollection != null) && (Arrays.asList(BP_VS_COLLECTIONS).contains(vsCollection))) {
      BpSearchResults<BpClass> bpResults = bpService.findValuesByValueSet(vsId, vsCollection, apiKey);
      return ObjectConverter.toValueResults(bpResults);
    } else {
      // Bad request
      throw new HTTPException(400);
    }
  }

}
