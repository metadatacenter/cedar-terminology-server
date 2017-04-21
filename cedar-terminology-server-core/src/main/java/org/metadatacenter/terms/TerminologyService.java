package org.metadatacenter.terms;

import org.metadatacenter.terms.bioportal.BioPortalService;
import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.domainObjects.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;
import org.metadatacenter.terms.util.ObjectConverter;
import org.metadatacenter.terms.util.Util;

import javax.ws.rs.core.Response;
import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.*;

public class TerminologyService implements ITerminologyService {

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
      requireDefinitions, int page, int pageSize, boolean displayContext, boolean displayLinks, String apiKey) throws IOException {

    BpPagedResults<BpProperty> results =
        bpService.propertySearch(q, sources, exactMatch, requireDefinitions, page, pageSize, displayContext, displayLinks, apiKey);

    return ObjectConverter.toPagedSearchResults(results);
  }

  /**
   * Ontologies
   */

  public List<Ontology> findAllOntologies(boolean includeDetails, String apiKey) throws IOException {
    List<BpOntology> bpOntologies = bpService.findAllOntologies(apiKey);
    List<Ontology> ontologies = new ArrayList<>();
    int i = 1;
    for (BpOntology o : bpOntologies) {
      // Only keep ontologies. Value set collections will be excluded
      if (o.getType() == null || o.getType().compareTo(BP_API_BASE + BP_ONTOLOGY_TYPE_VS_COLLECTION) != 0) {
        Ontology ont = ObjectConverter.toOntology(o);
        if (includeDetails) {
          ont.setDetails(getOntologyDetails(ont.getId(), apiKey));
        }
        //System.out.println(ont);
        ontologies.add(ont);
        System.out.println(ont.getId() + " loaded (" + i++ + ")");
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
    // Get number of classes
    try {
      // CEDARPC has only one level so the number of classes will be the number of root classes
      if (ontologyId.compareTo(CEDAR_PROVISIONAL_CLASSES_ONTOLOGY)==0) {
        int numClasses = getRootClasses(ontologyId, false, apiKey).size();
        details.setNumberOfClasses(numClasses);
      }
      else {
        BpOntologyMetrics metrics = bpService.findOntologyMetrics(ontologyId, apiKey);
        if (metrics.getClasses() != null) {
          details.setNumberOfClasses(Integer.parseInt(metrics.getClasses()));
        }
        else {
          System.out.println("Number of classes is 'null' for " + ontologyId + ". It has been set to 0");
          details.setNumberOfClasses(0);
        }
      }
    } catch (HTTPException e) {
      if (e.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
        // Do nothing
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
        // Do nothing
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
    } catch (HTTPException e) {
      if (e.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
        // Do nothing
      }
    }
    return details;
  }

  public List<OntologyClass> getRootClasses(String ontologyId, boolean isFlat, String apiKey) throws IOException {
    List<OntologyClass> roots = new ArrayList<>();
    // If it is a flat ontology BioPortal will return a timeout. An empty list will be returned to avoid calling BioPortal
    if (isFlat) {
      return roots;
    }
    // If CEDARPC ontology
    if (ontologyId.compareTo(CEDAR_PROVISIONAL_CLASSES_ONTOLOGY)==0) {
      // Iterate to retrieve all provisional classes
      boolean finished = false;
      int page = FIRST_PAGE;
      int pageSize = PAGE_SIZE;
      while (!finished) {
        BpPagedResults<BpProvisionalClass> provClasses = bpService.findAllProvisionalClasses(ontologyId, page, pageSize, apiKey);
        for (BpProvisionalClass c : provClasses.getCollection()) {
          OntologyClass oc = ObjectConverter.toOntologyClass(c);
          oc.setHasChildren(false);
          roots.add(oc);
        }
        if (provClasses.getPage() == provClasses.getPageCount()) {
          finished = true;
        }
        else {
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

  public PagedResults<OntologyClass> findAllClassesInOntology(String ontology, int page, int pageSize, String apiKey) throws IOException {
    BpPagedResults<BpClass> classes = bpService.findAllClassesInOntology(ontology, page, pageSize, apiKey);
    return ObjectConverter.toClassResults(classes);
  }

  public PagedResults<OntologyClass> findAllProvisionalClasses(String ontology, int page, int pageSize, String apiKey) throws IOException {
    BpPagedResults<BpProvisionalClass> provClasses = bpService.findAllProvisionalClasses(ontology, page, pageSize, apiKey);
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
      BpPagedResults<BpProvisionalClass> provClasses = bpService.findAllProvisionalClasses(ontology, page, pageSize, apiKey);
      PagedResults<OntologyClass> classes = ObjectConverter.toClassResultsFromProvClassResults(provClasses);
      result.addAll(classes.getCollection());
      if (provClasses.getPage() == provClasses.getPageCount()) {
        finished = true;
      }
      else {
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
    // If it is a flat ontology BioPortal will return a timeout. An empty list will be returned to avoid calling BioPortal
    if (isFlat) {
      return new ArrayList<>();
    }
    List<TreeNode> nodes = new ArrayList<>();
    // If it is a class in the CEDARPC ontology...
    if (ontology.compareTo(CEDAR_PROVISIONAL_CLASSES_ONTOLOGY)==0) {
      // Get all classes in the ontology and build tree nodes from them
      List<OntologyClass> classes = findAllProvisionalClasses(ontology, apiKey);
      for (OntologyClass c : classes) {
        nodes.add(ObjectConverter.toTreeNodeNoChildren(c));
      }
    }
    else {
      List<BpTreeNode> bpNodes = bpService.getClassTree(id, ontology, apiKey);
      for (BpTreeNode n : bpNodes) {
        nodes.add(ObjectConverter.toTreeNode(n));
      }
    }
    return nodes;
  }

  public PagedResults<OntologyClass> getClassChildren(String id, String ontology, int page, int pageSize, String apiKey) throws IOException {
    BpPagedResults<BpClass> bpChildren = bpService.getClassChildren(id, ontology, page, pageSize, apiKey);
    return ObjectConverter.toClassResults(bpChildren);
  }

  public PagedResults<OntologyClass> getClassDescendants(String id, String ontology, int page, int pageSize, String apiKey) throws IOException {
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
    BpProvisionalRelation pr = bpService.createBpProvisionalRelation(ObjectConverter.toBpProvisionalRelation(r), apiKey);
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
    }
    else {
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

  public PagedResults<Value> findValuesByValueSet(String vsId, String vsCollection, int page, int pageSize, String apiKey) throws IOException {
    // Check that vsCollection is a valid Value Set Collection
    if (Util.validVsCollection(vsCollection, false)) {
      PagedResults<Value> results = null;
      // Find the value set and check if it is regular or provisional
      ValueSet vs = findValueSet(vsId, vsCollection, apiKey);
      if (!vs.isProvisional()) {
        BpPagedResults<BpClass> bpResults = bpService.findValuesByValueSet(vsId, vsCollection, page, pageSize, apiKey);
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
        results = new PagedResults(1, 1, values.size(), 0, 0, values);
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
    int page = FIRST_PAGE;
    // We need to retrieve all value sets in one call so we set a higher limit for pageSize. Higher values are not
    // accepted by BioPortal. Bad request is returned
    int pageSize = LARGE_PAGE_SIZE;
    //int pageSize = Integer.MAX_VALUE;
    for (String vs : BP_VS_COLLECTIONS_READ) {
      List<ValueSet> vsTmp = findValueSetsByVsCollection(vs, page, pageSize, apiKey).getCollection();
      valueSets.addAll(vsTmp);
    }
    return valueSets;
  }

//  public List<ValueSet> findAllValueSets(String vsCollection, String apiKey) throws IOException {
//    List<String> vsCollectionIds = new ArrayList<>();
//    List<ValueSet> valueSets = new ArrayList<>();
//    // If the vsCollection has not been specified
//    if (vsCollection == null || vsCollection.isEmpty()) {
//      List<VSCollection> vsCollections = findAllVSCollections(false, apiKey);
//      for (VSCollection c : vsCollections) {
//        vsCollectionIds.add(c.getId());
//      }
//    }
//    else {
//      vsCollectionIds.add(vsCollection);
//    }
//    // Retrieve valueSets
//    for (String vscId : vsCollectionIds) {
//      List<OntologyClass> rootClasses = getRootClasses(vscId, apiKey);
//      for (OntologyClass c : rootClasses) {
//        valueSets.add(ObjectConverter.toValueSet(c));
//      }
//    }
//    return valueSets;
//  }

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
    try {
      v = findRegularValue(id, vsCollection, apiKey);
    } catch (HTTPException e) {
      try {
        v = findProvisionalValue(id, apiKey);
      } catch (HTTPException e2) {
        throw e2;
      }
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

  public PagedResults<Value> findAllValuesInValueSetByValue(String id, String vsCollection, int page, int pageSize, String apiKey) throws IOException {
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
