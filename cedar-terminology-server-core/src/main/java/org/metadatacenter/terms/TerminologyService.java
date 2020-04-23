package org.metadatacenter.terms;

import org.metadatacenter.cedar.terminology.validation.integratedsearch.*;
import org.metadatacenter.terms.bioportal.BioPortalService;
import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.domainObjects.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;
import org.metadatacenter.terms.util.IntegratedSearchUtil;
import org.metadatacenter.terms.util.IntegratedSearchUtil.SourceType;
import org.metadatacenter.terms.util.ObjectConverter;
import org.metadatacenter.terms.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.*;

import static org.metadatacenter.cedar.terminology.util.Constants.*;

public class TerminologyService implements ITerminologyService {

  private static final Logger log = LoggerFactory.getLogger(TerminologyService.class);

  private BioPortalService bpService;

  public TerminologyService(String bpApiBasePath, int connectTimeout, int socketTimeout) {
    BP_API_BASE = bpApiBasePath;
    this.bpService = new BioPortalService(connectTimeout, socketTimeout);
  }

  /**
   * Search
   */

  public PagedResults<SearchResult> search(String q, List<String> scope, List<String> sources, boolean suggest,
                                           String source, String subtreeRootId, int maxDepth, int page, int pageSize,
                                           boolean displayContext, boolean displayLinks, String apiKey, List<String>
                                               valueSetsIds) throws IOException {
    BpPagedResults<BpClass> results = bpService.search(q, scope, sources, suggest, source, subtreeRootId, maxDepth,
        page, pageSize, displayContext, displayLinks, apiKey);

    return ObjectConverter.toPagedSearchResults(results, valueSetsIds);
  }

  public PagedResults<SearchResult> propertySearch(String q, List<String> sources, boolean exactMatch, boolean
      requireDefinitions, int page, int pageSize, boolean displayContext, boolean displayLinks, String apiKey) throws
      IOException {

    BpPagedResults<BpProperty> results =
        bpService.propertySearch(q, sources, exactMatch, requireDefinitions, page, pageSize, displayContext,
            displayLinks, apiKey);

    return ObjectConverter.toPagedSearchResults(results);
  }

  private PagedResults<SearchResult> searchValuesByValueSet(String q, String vsId, String vsCollection, int page,
                                                            int pageSize, String apiKey) throws IOException {

    // Check that vsCollection is a valid Value Set Collection
    if (Util.validVsCollection(vsCollection, false)) {
      PagedResults<SearchResult> results = null;
      // Find the value set and check if it is regular or provisional
      ValueSet vs = findValueSet(vsId, vsCollection, apiKey);

      if (!vs.isProvisional()) { // Regular value set
        // In this case, the value set is just an ontology branch. The value set corresponds to the root of the branch
        // and the values are all the branch subclasses. Therefore, we can use the branch search endpoint to find all
        // the values.
        results = search(q, Arrays.asList(BP_SEARCH_SCOPE_VALUES), new ArrayList<>(), true, vsCollection,
            vsId, 0, page, pageSize, false, true, apiKey, new ArrayList<>());
      } else { // Provisional value set
        List<SearchResult> values = new ArrayList<>();
        // Get all provisional classes in the vsCollection
        List<OntologyClass> classes = findAllProvisionalClasses(vsCollection, apiKey);
        // Keep those provisional classes that are subclass of the given vs and that match the search query
        for (OntologyClass c : classes) {
          if (c.getSubclassOf() != null) {
            String encSubclassOf = Util.encodeIfNeeded(c.getSubclassOf());
            String encVsId = Util.encodeIfNeeded(vsId);
            if (encSubclassOf.compareTo(encVsId) == 0) {
              if (c.getPrefLabel().toLowerCase().contains(q.toLowerCase())) {
                values.add(ObjectConverter.toSearchResult(c));
              }
            }
          }
        }
        // Sort values by length so that the closer matches are ranked higher
        values = Util.sortByPrefLabelLength(values);
        // Generate paginated results
        results = Util.generatePaginatedResults(values, page, pageSize);
      }
      return results;
    } else {
      // Bad request
      throw new HTTPException(Response.Status.BAD_REQUEST.getStatusCode());
    }
  }

  /**
   * CEDAR Integrated Search
   */
  public PagedResults<SearchResult> integratedSearch(Optional<String> q, ValueConstraints valueConstraints,
                                                     int page, int pageSize, String apiKey) throws IOException {

    // Check if we have to deal with multiple sources or not
    boolean multipleSources = IntegratedSearchUtil.hasMultipleSources(valueConstraints);

    PagedResults<SearchResult> results = null;

    if (!multipleSources) {
      results = integratedSearchSingleSource(q, valueConstraints, page, pageSize, apiKey);
    } else {
      results = integratedSearchMultipleSources(q, valueConstraints, page, pageSize, apiKey);
    }

    // Apply class arrangements.
    // Limitations:
    // ...
    if (valueConstraints.getActions() != null && valueConstraints.getActions().size() > 0) {
      results = applyActions(results, valueConstraints.getActions(), page, pageSize);
    }

    return results;
  }

  private PagedResults<SearchResult> applyActions(PagedResults<SearchResult> results, List<Action> actions, int page, int pageSize) {

    // Check that the actions only apply to the right page!

    List<SearchResult> updatedResults = new ArrayList<>();
    // Sort actions to apply them in the right order. First, we will apply the 'delete' actions. Then, move actions
    // must be applied in order from highest to lowest rank
    List<Action> sortedActions = new ArrayList<>();
    List<Action> moveActions = new ArrayList<>();
    List<String> termUris = new ArrayList<>();
    for (Action action : actions) {
      if (action.getAction().equals(CEDAR_VALUE_ARRANGEMENTS_ACTION_DELETE)) {
        sortedActions.add(action);
      }
      else if (action.getAction().equals(CEDAR_VALUE_ARRANGEMENTS_ACTION_MOVE)) {
        moveActions.add(action);
      }
      else {
        throw new InternalError("Invalid action: " + action.getAction());
      }
      termUris.add(action.getTermUri());
    }
    // Sort 'move' actions
    moveActions.sort(Comparator.comparing(Action::getTo));

    // Ignore classes referenced in either 'delete' or 'move' actions
    for (SearchResult result : results.getCollection()) {
      if (!termUris.contains(result.getLdId())) {
        updatedResults.add(result);
      }
    }
    // Now, insert the classes reference by 'move' actions into the right position
//    for (Action action : moveActions) {
//      updatedResults.add(action.getTo())
//    }

    // Generate new paged results because the totalCount will be different
    //results.setCollection(updatedResults);

    return results;
  }

  private PagedResults<SearchResult> integratedSearchSingleSource(Optional<String> q, ValueConstraints valueConstraints,
                                                                  int page, int pageSize, String apiKey) throws IOException {
    PagedResults<SearchResult> results = null;
    /* Class constraints */
    if (valueConstraints.getClasses().size() > 0) {
      results = integratedSearchEnumeratedClasses(q, valueConstraints.getClasses(), page, pageSize);
    }
    /* Ontology constraints */
    if (valueConstraints.getOntologies().size() > 0) {
      if (q.isEmpty()) {
        results = integratedSearchOntologiesEmptyQuery(valueConstraints.getOntologies().get(0), page, pageSize, apiKey);
      } else {
        results = integratedSearchOntologies(q, valueConstraints.getOntologies(), page, pageSize, apiKey);
      }
    }
    /* Branch constraints */
    if (valueConstraints.getBranches().size() > 0) {
      results = integratedSearchBranches(q, valueConstraints.getBranches().get(0), page, pageSize, apiKey);
    }
    /* Value set constraints */
    if (valueConstraints.getValueSets().size() > 0) {
      results = integratedSearchValueSets(q, valueConstraints.getValueSets().get(0), page, pageSize, apiKey);
    }
    return results;
  }

  private PagedResults<SearchResult> integratedSearchMultipleSources(Optional<String> q,
                                                                     ValueConstraints valueConstraints,
                                                                     int page, int pageSize, String apiKey) throws IOException {
    if (q.isEmpty()) {
      return integratedSearchMultipleSourcesEmptyQuery(valueConstraints, page, pageSize, apiKey);
    } else {
      return integratedSearchMultipleSourcesNonEmptyQuery(q, valueConstraints, page, pageSize, apiKey);
    }
  }

  /**
   * Requested start/end indexes: the range of results requested.
   * Effective start/end indexes: the range covered by each source.
   * Notes:
   * - Determining totalCount, pageCount, and nextPage would require making one extra call per source type. In order to maximize performance,
   * we set them to 0.
   *
   * @param valueConstraints
   * @param page
   * @param pageSize
   * @param apiKey
   * @return
   * @throws IOException
   */
  private PagedResults<SearchResult> integratedSearchMultipleSourcesEmptyQuery(ValueConstraints valueConstraints,
                                                                               int page, int pageSize, String apiKey) throws IOException {


    List<SearchResult> allResults = new ArrayList<>();

    int requestedStartIndex = (page - 1) * pageSize; // Example: page = 2, pageSize = 50, startIndex = 100 (element 101)
    int effectiveStartIndex;
    int effectiveEndIndex = -1;

    // The order of the following array determines the order in which the sources will be queried
    SourceType[] sourceTypes = {SourceType.CLASSES, SourceType.ONTOLOGIES, SourceType.BRANCHES, SourceType.VALUE_SETS};

    outerloop:
    for (SourceType sourceType : sourceTypes) {
      int numberOfSources = IntegratedSearchUtil.getNumberOfSources(sourceType, valueConstraints);

      for (int i = 0; i < numberOfSources; i++) {
        int totalCount = 0;
        if (sourceType.equals(SourceType.CLASSES)) { /* Class constraints */
          totalCount = valueConstraints.getClasses().size();
        } else if (sourceType.equals(SourceType.ONTOLOGIES)) {
          totalCount =
              integratedSearchOntologiesEmptyQuery(valueConstraints.getOntologies().get(i), 1, 1, apiKey).getTotalCount();
        } else if (sourceType.equals(SourceType.BRANCHES)) {
          totalCount =
              integratedSearchBranches(Optional.empty(), valueConstraints.getBranches().get(i), 1, 1, apiKey).getTotalCount();
        } else if (sourceType.equals(SourceType.VALUE_SETS)) {
          totalCount =
              integratedSearchValueSets(Optional.empty(), valueConstraints.getValueSets().get(i), 1, 1, apiKey).getTotalCount();
        }
        effectiveStartIndex = effectiveEndIndex + 1;
        effectiveEndIndex = totalCount - 1;

        if (requestedStartIndex >= effectiveStartIndex && requestedStartIndex <= effectiveEndIndex) {
          int translatedPage = ((requestedStartIndex - effectiveStartIndex) / pageSize) + 1;
          List<SearchResult> partialResults;
          boolean finishedWithSource = false;
          while (!finishedWithSource) {
            PagedResults<SearchResult> pagedResults = null;
            if (sourceType.equals(SourceType.CLASSES)) { /* Class constraints */
              pagedResults = integratedSearchEnumeratedClasses(Optional.empty(), valueConstraints.getClasses(),
                  translatedPage, pageSize);
            } else if (sourceType.equals(SourceType.ONTOLOGIES)) {
              pagedResults =
                  integratedSearchOntologiesEmptyQuery(valueConstraints.getOntologies().get(i), translatedPage,
                      pageSize, apiKey);
            } else if (sourceType.equals(SourceType.BRANCHES)) {
              pagedResults =
                  integratedSearchBranches(Optional.empty(), valueConstraints.getBranches().get(i), translatedPage,
                      pageSize, apiKey);
            } else if (sourceType.equals(SourceType.VALUE_SETS)) {
              pagedResults =
                  integratedSearchValueSets(Optional.empty(), valueConstraints.getValueSets().get(i),
                      translatedPage, pageSize, apiKey);
            }
            partialResults = pagedResults.getCollection();
            int offset = (requestedStartIndex % pageSize) - effectiveStartIndex;
            int sublistStartIndex = offset > 0 ? offset : 0;
            // Calculate sublistEndIndex
            int numberOfRemainingResultsNeeded = pageSize - allResults.size();
            int endIndexTmp = Math.min(partialResults.size() - 1, effectiveEndIndex - effectiveStartIndex);
            // Note that when calling sublist, endIndex is exclusive so we add 1 to it
            int sublistEndIndex = (Math.min(numberOfRemainingResultsNeeded - 1, endIndexTmp)) + 1;
            partialResults = pagedResults.getCollection().subList(sublistStartIndex, sublistEndIndex);
            allResults.addAll(partialResults);

            requestedStartIndex += partialResults.size();

            if (allResults.size() == pageSize) {
              break outerloop; // finished
            } else if (allResults.size() > pageSize) {
              throw new InternalError("Pagination Error: the number of returned results is larger than the page size");
            }

            if (pagedResults.getNextPage() == null) {
              finishedWithSource = true;
            } else {
              translatedPage++;
            }
          }
        }
      }
    }

    // Notes: some parameters are set to null because calculating them would decrease performance
    Integer prevPage = page > 1 ? page - 1 : null;
    return new PagedResults(page, null, allResults.size(), null, prevPage, null, allResults);
  }

  private PagedResults<SearchResult> integratedSearchMultipleSourcesNonEmptyQuery(Optional<String> q,
                                                                                  ValueConstraints valueConstraints,
                                                                                  int page, int pageSize,
                                                                                  String apiKey) throws IOException {

    List<SearchResult> allResults = new ArrayList<>();

    /* Class constraints */
    if (valueConstraints.getClasses().size() > 0) {
      allResults.addAll(integratedSearchEnumeratedClasses(q, valueConstraints.getClasses(), page, pageSize).getCollection());
    }
    /* Ontology constraints */
    if ((valueConstraints.getOntologies().size() > 0)) { // Avoid making
      allResults.addAll(integratedSearchOntologies(q, valueConstraints.getOntologies(), page, pageSize, apiKey).getCollection());
    }
    /* Branch constraints */
    if ((valueConstraints.getBranches().size() > 0)) {
      for (BranchValueConstraint branchValueConstraint : valueConstraints.getBranches()) {
        allResults.addAll(integratedSearchBranches(q, branchValueConstraint, page, pageSize, apiKey).getCollection());
      }
    }
    /* Value set constraints */
    if ((valueConstraints.getValueSets().size() > 0)) {
      for (ValueSetValueConstraint valueSetConstraint : valueConstraints.getValueSets()) {
        allResults.addAll(integratedSearchValueSets(q, valueSetConstraint, page, pageSize, apiKey).getCollection());
      }
    }

    // Re-sort results by length so that the closer matches are ranked higher. We don't need to filter them out by
    // label at this point because we've done it already when retrieving the results.
    allResults = Util.sortByPrefLabelLength(allResults);

    // Generate paginated results
    PagedResults<SearchResult> results = Util.generatePaginatedResults(allResults, page, pageSize);

    return results;
  }

  // Find classes by name in a given list of ontologies
  private PagedResults<SearchResult> integratedSearchOntologies(Optional<String> q,
                                                                List<OntologyValueConstraint> ontologyValueConstraints,
                                                                int page, int pageSize, String apiKey) throws IOException {
    return search(q.get(), Arrays.asList(BP_SEARCH_SCOPE_CLASSES),
        IntegratedSearchUtil.extractOntologyAcronyms(ontologyValueConstraints), true, null,
        null, 1, page, pageSize,
        false, true, apiKey, new ArrayList<>());

  }

  // Retrieve all classes from a given list of ontologies
  private PagedResults<SearchResult> integratedSearchOntologiesEmptyQuery(OntologyValueConstraint ontologyValueConstraint,
                                                                          int page, int pageSize, String apiKey) throws IOException {


    String ontologyAcronym = ontologyValueConstraint.getAcronym();
    PagedResults<OntologyClass> pagedClassResults = findAllClassesInOntology(ontologyAcronym, page, pageSize,
        apiKey);
    return ObjectConverter.classResultsToSearchResults(pagedClassResults);

  }

  private PagedResults<SearchResult> integratedSearchBranches(Optional<String> q,
                                                              BranchValueConstraint branchValueConstraint,
                                                              int page, int pageSize, String apiKey) throws IOException {
    PagedResults<SearchResult> results = null;

    String rootClassUri = branchValueConstraint.getUri();
    String ontologyAcronym = branchValueConstraint.getAcronym();

    if (q.isPresent()) { // Find classes by name in a given list of ontology branches

      results = search(q.get(), Arrays.asList(BP_SEARCH_SCOPE_CLASSES),
          new ArrayList<>(), true, ontologyAcronym,
          rootClassUri, 0, page, pageSize,
          false, true, apiKey, new ArrayList<>());

    } else { // Retrieve all classes from a given list of ontology branches

      PagedResults<OntologyClass> pagedClassResults =
          getClassDescendants(rootClassUri, ontologyAcronym, page, pageSize, apiKey);
      results = ObjectConverter.classResultsToSearchResults(pagedClassResults);

    }
    return results;

  }

  private PagedResults<SearchResult> integratedSearchValueSets(Optional<String> q,
                                                               ValueSetValueConstraint valueSetValueConstraint,
                                                               int page, int pageSize, String apiKey) throws IOException {
    PagedResults<SearchResult> results = null;
    String vsId = valueSetValueConstraint.getUri();
    String vsCollection = valueSetValueConstraint.getVsCollection();

    if (q.isPresent()) { // Find values by name in a given list of value sets

      results = searchValuesByValueSet(q.get(), vsId, vsCollection, page, pageSize, apiKey);

    } else { // Retrieve all values from a given value set

      PagedResults<Value> pagedValueResults =
          findValuesByValueSet(vsId, vsCollection, page, pageSize, apiKey);
      results = ObjectConverter.valueResultsToSearchResults(pagedValueResults);

    }
    return results;
  }

  private PagedResults<SearchResult> integratedSearchEnumeratedClasses(Optional<String> q,
                                                                       List<ClassValueConstraint> classValueConstraints,
                                                                       int page, int pageSize) throws IOException {
    PagedResults<SearchResult> results = null;
    // Convert enumerated classes to search results
    List<SearchResult> searchResults = new ArrayList<>();

    for (ClassValueConstraint cvc : classValueConstraints) {
      searchResults.add(ObjectConverter.toSearchResult(cvc));
    }

    if (q.isPresent()) { // Find classes by name

      List<SearchResult> selectedResults = Util.filterByQuery(q.get(), searchResults);
      // Sort results by length so that the closer matches are ranked higher
      selectedResults = Util.sortByPrefLabelLength(selectedResults);
      results = Util.generatePaginatedResults(selectedResults, page, pageSize);

    } else { // Retrieve all classes

      results = Util.generatePaginatedResults(searchResults, page, pageSize);

    }
    return results;
  }

  /**
   * Ontologies
   */

  public List<Ontology> findAllOntologies(boolean includeDetails, String apiKey) throws IOException {
    List<BpOntology> bpOntologies = bpService.findAllOntologies(apiKey);
    List<Ontology> ontologies = new ArrayList<>();
    int i = 1;
    int total = bpOntologies.size();
    for (BpOntology o : bpOntologies) {
      // Only keep ontologies. Value set collections will be excluded
      if (o.getType() == null || o.getType().compareTo(BP_API_BASE + BP_ONTOLOGY_TYPE_VS_COLLECTION) != 0) {
        Ontology ont = ObjectConverter.toOntology(o);
        if (includeDetails) {
          try {
            ont.setDetails(getOntologyDetails(ont.getId(), apiKey));
          } catch (Exception e) {
            log.error("Error retrieving ontology details for: " + ont.getId(), e);
          }
        }
        ontologies.add(ont);
        String message = ont.getId() + " loaded (" + i + "/" + total + ")";
        log.info(message);
        i++;
      }
    }
    return ontologies;
  }

  public Ontology findOntology(String id, boolean includeDetails, String apiKey) throws IOException {
    BpOntology ontology = bpService.findBpOntologyById(id, apiKey);
    Ontology o = ObjectConverter.toOntology(ontology);
    // Get details
    if (includeDetails) {
      o.setDetails(getOntologyDetails(id, apiKey));
    }
    return o;
  }

  private OntologyDetails getOntologyDetails(String ontologyId, String apiKey) throws IOException {
    OntologyDetails details = new OntologyDetails();
    boolean hasSubmissions = true;
    boolean metricsAvailable = true;
    // Get number of classes
    try {
      // CEDARPC has only one level so the number of classes will be the number of root classes
      if (ontologyId.compareTo(CEDAR_PROVISIONAL_CLASSES_ONTOLOGY) == 0) {
        int numClasses = getRootClasses(ontologyId, false, apiKey).size();
        details.setNumberOfClasses(numClasses);
      } else {
        BpOntologyMetrics metrics = bpService.findOntologyMetrics(ontologyId, apiKey);
        if (metrics.getId() != null && metrics.getClasses() != null) { // Metrics not available for that ontology
          details.setNumberOfClasses(Integer.parseInt(metrics.getClasses()));
        } else {
          metricsAvailable = false;
        }
      }
    } catch (HTTPException e) {
      if (e.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
        // When the metrics are not found, we also assume that it is because either there are no submissions for the
        // ontology or the latest submission failed to process, so submissions are not available either.
        hasSubmissions = false;
        metricsAvailable = false;
      } else {
        throw (e);
      }
    }
    // Get categories
    try {
      List<String> categories = new ArrayList<>();
      List<BpOntologyCategory> bpCategories = bpService.findOntologyCategories(ontologyId, apiKey);
      for (BpOntologyCategory c : bpCategories) {
        categories.add(c.getName());
      }
      details.setCategories(categories);
    } catch (HTTPException e) {
      if (e.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
        log.error("Categories not found for " + ontologyId + " ontology");
      } else {
        throw (e);
      }
    }
    // Get latest submission details
    try {
      BpOntologySubmission bpSubmission = bpService.getOntologyLatestSubmission(ontologyId, apiKey);
      details.setDescription(bpSubmission.getDescription());
      details.setHasOntologyLanguage(bpSubmission.getHasOntologyLanguage());
      details.setReleased(bpSubmission.getReleased());
      details.setCreationDate(bpSubmission.getCreationDate());
      details.setHomepage(bpSubmission.getHomepage());
      details.setPublication(bpSubmission.getPublication());
      details.setDocumentation(bpSubmission.getDocumentation());
      details.setVersion(bpSubmission.getVersion());
      details.setHasSubmissions(hasSubmissions);
      details.setMetricsAvailable(metricsAvailable);
    } catch (HTTPException e) {
      if (e.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
        log.error("Latest submission not found for " + ontologyId + " ontology");
      } else {
        throw (e);
      }
    }
    if (!hasSubmissions) {
      log.warn("No submissions available for " + ontologyId);
    }
    if (!metricsAvailable) {
      log.warn("No metrics available for " + ontologyId);
    }
    return details;
  }

  public List<OntologyClass> getRootClasses(String ontologyId, boolean isFlat, String apiKey) throws IOException {
    List<OntologyClass> roots = new ArrayList<>();
    // If it is a flat ontology BioPortal will return a timeout. An empty list will be returned to avoid calling
    // BioPortal
    if (isFlat) {
      return roots;
    }
    // If CEDARPC ontology
    if (ontologyId.compareTo(CEDAR_PROVISIONAL_CLASSES_ONTOLOGY) == 0) {
      // Iterate to retrieve all provisional classes
      boolean finished = false;
      int page = FIRST_PAGE;
      int pageSize = PAGE_SIZE;
      while (!finished) {
        BpPagedResults<BpProvisionalClass> provClasses = bpService.findAllProvisionalClasses(ontologyId, page,
            pageSize, apiKey);
        for (BpProvisionalClass c : provClasses.getCollection()) {
          OntologyClass oc = ObjectConverter.toOntologyClass(c);
          oc.setHasChildren(false);
          roots.add(oc);
        }
        if (provClasses.getPage() == provClasses.getPageCount()) {
          finished = true;
        } else {
          page++;
        }
      }
    }
    // If any other ontology
    else {
      List<BpClass> bpRoots = bpService.getRootClasses(ontologyId, apiKey);
      for (BpClass c : bpRoots) {
        roots.add(ObjectConverter.toOntologyClass(c));
      }
    }
    return roots;
  }

  public List<OntologyProperty> getRootProperties(String ontologyId, String apiKey) throws IOException {
    List<OntologyProperty> roots = new ArrayList<>();
    List<BpProperty> bpRoots = bpService.getRootProperties(ontologyId, apiKey);
    for (BpProperty property : bpRoots) {
      roots.add(ObjectConverter.toOntologyProperty(property));
    }
    return roots;
  }

  /**
   * Classes
   **/

  public OntologyClass createProvisionalClass(OntologyClass c, String apiKey) throws IOException {
    BpProvisionalClass pc = bpService.createBpProvisionalClass(ObjectConverter.toBpProvisionalClass(c), apiKey);
    return ObjectConverter.toOntologyClass(pc);
  }

  public OntologyClass findProvisionalClass(String id, String apiKey) throws IOException {
    BpProvisionalClass pc = bpService.findBpProvisionalClassById(id, apiKey);
    return ObjectConverter.toOntologyClass(pc);
  }

  public OntologyClass findRegularClass(String id, String ontology, String apiKey) throws IOException {
    BpClass c = bpService.findBpClassById(id, ontology, apiKey);
    return ObjectConverter.toOntologyClass(c);
  }

  public OntologyClass findClass(String id, String ontology, String apiKey) throws IOException {
    OntologyClass c;
    try {
      c = findRegularClass(id, ontology, apiKey);
    } catch (HTTPException e) {
      try {
        c = findProvisionalClass(id, apiKey);
      } catch (HTTPException e2) {
        throw e2;
      }
    }
    return c;
  }

  public PagedResults<OntologyClass> findAllClassesInOntology(String ontology, int page, int pageSize, String apiKey)
      throws IOException {
    BpPagedResults<BpClass> classes = bpService.findAllClassesInOntology(ontology, page, pageSize, apiKey);
    return ObjectConverter.toClassResults(classes);
  }

  public PagedResults<OntologyClass> findAllProvisionalClasses(String ontology, int page, int pageSize, String
      apiKey) throws IOException {
    BpPagedResults<BpProvisionalClass> provClasses = bpService.findAllProvisionalClasses(ontology, page, pageSize,
        apiKey);
    List<OntologyClass> classes = new ArrayList<>();
    for (BpProvisionalClass pc : provClasses.getCollection()) {
      classes.add(ObjectConverter.toOntologyClass(pc));
    }
    PagedResults<OntologyClass> results = ObjectConverter.toClassResultsFromProvClassResults(provClasses);
    return results;
  }

  public List<OntologyClass> findAllProvisionalClasses(String ontology, String apiKey) throws IOException {
    List<OntologyClass> result = new ArrayList<>();
    boolean finished = false;
    int page = FIRST_PAGE;
    int pageSize = LARGE_PAGE_SIZE;
    while (!finished) {
      BpPagedResults<BpProvisionalClass> provClasses = bpService.findAllProvisionalClasses(ontology, page, pageSize,
          apiKey);
      PagedResults<OntologyClass> classes = ObjectConverter.toClassResultsFromProvClassResults(provClasses);
      result.addAll(classes.getCollection());
      if (provClasses.getPage() == provClasses.getPageCount()) {
        finished = true;
      } else {
        page++;
      }
    }
    return result;
  }


  public void updateProvisionalClass(OntologyClass c, String apiKey) throws IOException {
    bpService.updateProvisionalClass(ObjectConverter.toBpProvisionalClass(c), apiKey);
  }

  public void deleteProvisionalClass(String id, String apiKey) throws IOException {
    bpService.deleteProvisionalClass(id, apiKey);
  }

  public List<TreeNode> getClassTree(String id, String ontology, boolean isFlat, String apiKey) throws IOException {
    // If it is a flat ontology BioPortal will return a timeout. An empty list will be returned to avoid calling
    // BioPortal
    if (isFlat) {
      return new ArrayList<>();
    }
    List<TreeNode> nodes = new ArrayList<>();
    // If it is a class in the CEDARPC ontology...
    if (ontology.compareTo(CEDAR_PROVISIONAL_CLASSES_ONTOLOGY) == 0) {
      // Get all classes in the ontology and build tree nodes from them
      List<OntologyClass> classes = findAllProvisionalClasses(ontology, apiKey);
      for (OntologyClass c : classes) {
        nodes.add(ObjectConverter.toTreeNodeNoChildren(c));
      }
    } else {
      List<BpTreeNode> bpNodes = bpService.getClassTree(id, ontology, apiKey);
      for (BpTreeNode n : bpNodes) {
        nodes.add(ObjectConverter.toTreeNode(n));
      }
    }
    return nodes;
  }

  public PagedResults<OntologyClass> getClassChildren(String id, String ontology, int page, int pageSize, String
      apiKey) throws IOException {
    BpPagedResults<BpClass> bpChildren = bpService.getClassChildren(id, ontology, page, pageSize, apiKey);
    return ObjectConverter.toClassResults(bpChildren);
  }

  public PagedResults<OntologyClass> getClassDescendants(String id, String ontology, int page, int pageSize, String
      apiKey) throws IOException {
    BpPagedResults<BpClass> bpDescendants = bpService.getClassDescendants(id, ontology, page, pageSize, apiKey);
    return ObjectConverter.toClassResults(bpDescendants);
  }

  public List<OntologyClass> getClassParents(String id, String ontology, String apiKey) throws IOException {
    List<BpClass> bpParents = bpService.getClassParents(id, ontology, apiKey);
    List<OntologyClass> parents = new ArrayList<>();
    for (BpClass p : bpParents) {
      parents.add(ObjectConverter.toOntologyClass(p));
    }
    return parents;
  }

  /**
   * Relations
   **/

  public Relation createProvisionalRelation(Relation r, String apiKey) throws IOException {
    BpProvisionalRelation pr = bpService.createBpProvisionalRelation(ObjectConverter.toBpProvisionalRelation(r),
        apiKey);
    return ObjectConverter.toRelation(pr);
  }

  public Relation findProvisionalRelation(String id, String apiKey) throws IOException {
    BpProvisionalRelation pr = bpService.findProvisionalRelationById(id, apiKey);
    return ObjectConverter.toRelation(pr);
  }

//  public void updateProvisionalRelation(Relation r, String apiKey) throws IOException {
//    bpService.updateProvisionalRelation(ObjectConverter.toBpProvisionalRelation(r), apiKey);
//  }

  public void deleteProvisionalRelation(String classId, String apiKey) throws IOException {
    bpService.deleteProvisionalRelation(classId, apiKey);
  }

  /**
   * Value Sets
   **/

  public ValueSet createProvisionalValueSet(ValueSet vs, String apiKey) throws IOException {
    // Creation of value sets is restricted to the CEDARVS value set collection
    if (Util.validVsCollection(vs.getVsCollection(), true)) {
      // Create value set
      BpProvisionalClass pc = bpService.createBpProvisionalClass(ObjectConverter.toBpProvisionalClass(vs), apiKey);
      ValueSet createdVs = ObjectConverter.toValueSet(pc);
      return createdVs;
    } else {
      // Bad request
      throw new HTTPException(Response.Status.BAD_REQUEST.getStatusCode());
    }
  }

  public ValueSet findProvisionalValueSet(String id, String apiKey) throws IOException {
    BpProvisionalClass pc = bpService.findBpProvisionalClassById(Util.encodeIfNeeded(id), apiKey);
    return ObjectConverter.toValueSet(pc);
  }

  public ValueSet findRegularValueSet(String id, String vsCollection, String apiKey) throws IOException {
    BpClass c = bpService.findBpClassById(Util.encodeIfNeeded(id), vsCollection, apiKey);
    return ObjectConverter.toValueSet(c);
  }

  public ValueSet findValueSet(String id, String vsCollection, String apiKey) throws IOException {
    ValueSet vs;
    try {
      vs = findRegularValueSet(id, vsCollection, apiKey);
    } catch (HTTPException e) {
      try {
        vs = findProvisionalValueSet(id, apiKey);
      } catch (HTTPException e2) {
        throw e2;
      }
    }
    return vs;
  }

  public ValueSet findValueSetByValue(String id, String vsCollection, String apiKey) throws IOException {
    ValueSet vs = null;
    if (vsCollection.compareTo(CEDAR_VALUE_SETS_ONTOLOGY) == 0) {
      Value v = findProvisionalValue(id, apiKey);
      vs = findValueSet(v.getVsId(), vsCollection, apiKey);
    } else {
      List<BpClass> bpParents = bpService.getClassParents(id, vsCollection, apiKey);
      // Keep only the first parent
      for (int i = 0; i < 1; i++) {
        vs = ObjectConverter.toValueSet(bpParents.get(i));
      }
    }
    return vs;
  }

  public void updateProvisionalValueSet(ValueSet vs, String apiKey) throws IOException {
    bpService.updateProvisionalClass(ObjectConverter.toBpProvisionalClass(vs), apiKey);
  }

  public void deleteProvisionalValueSet(String id, String apiKey) throws IOException {
    bpService.deleteProvisionalClass(id, apiKey);
  }

  public PagedResults<ValueSet> findValueSetsByVsCollection(String vsCollection, int page, int pageSize, String
      apiKey) throws IOException {
    // Check that vsCollection is a valid Value Set Collection
    if (Util.validVsCollection(vsCollection, false)) {
      BpPagedResults<BpClass> bpResults = bpService.findValueSetsByValueSetCollection(vsCollection, page, pageSize,
          apiKey);
      return ObjectConverter.toValueSetResults(bpResults);
    } else {
      // Bad request
      throw new HTTPException(Response.Status.BAD_REQUEST.getStatusCode());
    }
  }

  public PagedResults<Value> findValuesByValueSet(String vsId, String vsCollection, int page, int pageSize, String
      apiKey) throws IOException {
    // Check that vsCollection is a valid Value Set Collection
    if (Util.validVsCollection(vsCollection, false)) {
      PagedResults<Value> results = null;
      // Find the value set and check if it is regular or provisional

      // Old code. We commented it out to avoid making this extra call
      // ValueSet vs = findValueSet(vsId, vsCollection, apiKey);
      // boolean isProvisional = vs.isProvisional();

      boolean isProvisional = Util.isProvisionalClass(vsId);

      if (!isProvisional) {
        BpPagedResults<BpClass> bpResults = bpService.findValuesByValueSet(vsId, vsCollection, page, pageSize,
            apiKey);
        results = ObjectConverter.toValueResults(bpResults);
      } else {
        List<Value> values = new ArrayList<>();
        // Get all provisional classes in the vsCollection
        List<OntologyClass> classes = findAllProvisionalClasses(vsCollection, apiKey);
        // Keep those provisional classes that are subclass of the given vs
        for (OntologyClass c : classes) {
          if (c.getSubclassOf() != null) {
            String encSubclassOf = Util.encodeIfNeeded(c.getSubclassOf());
            String encVsId = Util.encodeIfNeeded(vsId);
            if (encSubclassOf.compareTo(encVsId) == 0) {
              values.add(ObjectConverter.toValue(c));
            }
          }
        }
        // Generate paginated results
        results = Util.generatePaginatedResults(values, page, pageSize);
      }
      return results;
    } else {
      // Bad request
      throw new HTTPException(Response.Status.BAD_REQUEST.getStatusCode());
    }
  }

  public List<ValueSetCollection> findAllVSCollections(boolean includeDetails, String apiKey) throws IOException {
    List<BpOntology> bpOntologies = bpService.findAllOntologies(apiKey);
    List<ValueSetCollection> vsCollections = new ArrayList<>();
    for (BpOntology o : bpOntologies) {
      if (o.getType() != null && o.getType().compareTo(BP_API_BASE + BP_ONTOLOGY_TYPE_VS_COLLECTION) == 0) {
        vsCollections.add(ObjectConverter.toVSCollection(ObjectConverter.toOntology(o)));
      }
    }
    // Get details
    if (includeDetails) {
      for (ValueSetCollection c : vsCollections) {
        c.setDetails(ObjectConverter.toVSCollectionDetails(getOntologyDetails(c.getId(), apiKey)));
      }
    }
    return vsCollections;
  }

  public List<ValueSet> findAllValueSets(String apiKey) throws IOException {
    List<ValueSet> valueSets = new ArrayList<>();
    for (String vs : BP_VS_COLLECTIONS_READ) {
      int page = FIRST_PAGE;
      int pageSize = LARGE_PAGE_SIZE;
      boolean finished = false;
      while (!finished) {
        List<ValueSet> vsTmp = findValueSetsByVsCollection(vs, page, pageSize, apiKey).getCollection();
        if (vsTmp.size() > 0) {
          valueSets.addAll(vsTmp);
          page++;
        } else {
          finished = true;
        }
      }
    }
    return valueSets;
  }

  /**
   * Values
   */

  public Value createProvisionalValue(Value v, String apiKey) throws IOException {
    // Creation of value sets is restricted to the CEDARVS value set collection
    if ((v.getVsId() != null) && (Util.validVsCollection(v.getVsCollection(), true))) {
      BpProvisionalClass pc = bpService.createBpProvisionalClass(ObjectConverter.toBpProvisionalClass(v), apiKey);
      return ObjectConverter.toValue(pc);
    } else {
      // Bad request
      throw new HTTPException(Response.Status.BAD_REQUEST.getStatusCode());
    }
  }

  public Value findProvisionalValue(String id, String apiKey) throws IOException {
    BpProvisionalClass pc = bpService.findBpProvisionalClassById(id, apiKey);
    return ObjectConverter.toValue(pc);
  }

  public Value findRegularValue(String id, String vsCollection, String apiKey) throws IOException {
    BpClass c = bpService.findBpClassById(id, vsCollection, apiKey);
    return ObjectConverter.toValue(c);
  }

  public Value findValue(String id, String vsCollection, String apiKey) throws IOException {
    Value v;
    if (!Util.isProvisionalClass(id)) { // Non-provisional value
      v = findRegularValue(id, vsCollection, apiKey);
    } else {
      v = findProvisionalValue(id, apiKey);
    }
    return v;
  }

  public TreeNode getValueTree(String id, String vsCollection, String apiKey) throws IOException {
    int page = FIRST_PAGE;
    int pageSize = LARGE_PAGE_SIZE;
    ValueSet vs = findValueSetByValue(id, vsCollection, apiKey);
    List<Value> values =
        findValuesByValueSet(Util.encodeIfNeeded(vs.getLdId()), vsCollection, page, pageSize, apiKey).getCollection();
    return ObjectConverter.toTreeNode(vs, values);
  }

  public TreeNode getValueSetTree(String id, String vsCollection, String apiKey) throws IOException {
    int page = FIRST_PAGE;
    int pageSize = LARGE_PAGE_SIZE;
    ValueSet vs = findValueSet(id, vsCollection, apiKey);
    List<Value> values =
        findValuesByValueSet(Util.encodeIfNeeded(vs.getLdId()), vsCollection, page, pageSize, apiKey).getCollection();
    return ObjectConverter.toTreeNode(vs, values);
  }

  public PagedResults<Value> findAllValuesInValueSetByValue(String id, String vsCollection, int page, int pageSize,
                                                            String apiKey) throws IOException {
    ValueSet vs = findValueSetByValue(id, vsCollection, apiKey);
    return findValuesByValueSet(Util.encodeIfNeeded(vs.getLdId()), vsCollection, page, pageSize, apiKey);
  }

  public void updateProvisionalValue(Value v, String apiKey) throws IOException {
    bpService.updateProvisionalClass(ObjectConverter.toBpProvisionalClass(v), apiKey);
  }

  public void deleteProvisionalValue(String id, String apiKey) throws IOException {
    bpService.deleteProvisionalClass(id, apiKey);
  }

  /**
   * Properties
   */

  public OntologyProperty findProperty(String id, String ontology, String apiKey) throws IOException {
    BpProperty bp = bpService.findBpPropertyById(id, ontology, apiKey);
    return ObjectConverter.toOntologyProperty(bp);
  }

  public List<OntologyProperty> findAllPropertiesInOntology(String ontology, String apiKey) throws IOException {
    List<BpProperty> properties = bpService.findAllPropertiesInOntology(ontology, apiKey);
    return ObjectConverter.toPropertyListResults(properties);
  }

  public List<TreeNode> getPropertyTree(String id, String ontology, String apiKey) throws IOException {
    List<TreeNode> nodes = new ArrayList<>();
    List<BpTreeNode> bpNodes = bpService.getPropertyTree(id, ontology, apiKey);
    for (BpTreeNode n : bpNodes) {
      nodes.add(ObjectConverter.toTreeNode(n));
    }
    return nodes;
  }

  public List<OntologyProperty> getPropertyChildren(String id, String ontology, String apiKey) throws IOException {
    List<BpProperty> bpChildren = bpService.getPropertyChildren(id, ontology, apiKey);
    return ObjectConverter.toPropertyListResults(bpChildren);
  }

  public List<OntologyProperty> getPropertyDescendants(String id, String ontology, String apiKey) throws IOException {
    List<BpProperty> bpDescendants = bpService.getPropertyDescendants(id, ontology, apiKey);
    return ObjectConverter.toPropertyListResults(bpDescendants);
  }

  public List<OntologyProperty> getPropertyParents(String id, String ontology, String apiKey) throws IOException {
    List<BpProperty> bpParents = bpService.getPropertyParents(id, ontology, apiKey);
    List<OntologyProperty> parents = new ArrayList<>();
    for (BpProperty property : bpParents) {
      parents.add(ObjectConverter.toOntologyProperty(property));
    }
    return parents;
  }

}
