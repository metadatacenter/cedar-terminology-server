package org.metadatacenter.terms;

import org.metadatacenter.terms.bioportal.BioPortalService;
import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.domainObjects.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;
import org.metadatacenter.terms.util.ObjectConverter;
import org.metadatacenter.terms.util.Util;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.BP_API_BASE;
import static org.metadatacenter.terms.util.Constants.BP_ONTOLOGY_TYPE_VS_COLLECTION;
import static org.metadatacenter.terms.util.Constants.BP_API_WAIT_TIME;
import static org.metadatacenter.terms.util.Constants.BP_VS_COLLECTIONS_READ;

public class TerminologyService implements ITerminologyService {

  private final int connectTimeout;
  private final int socketTimeout;
  private BioPortalService bpService;

  public TerminologyService(String bpApiBasePath, int connectTimeout, int socketTimeout) {
    BP_API_BASE = bpApiBasePath;
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
    this.bpService = new BioPortalService(connectTimeout, socketTimeout);
  }

  public PagedResults<OntologyClass> search(String q, List<String> scope, List<String> sources, int page, int pageSize,
                                            boolean displayContext, boolean displayLinks, String apiKey) throws
      IOException {
    BpPagedResults results = bpService.search(q, scope, sources, page, pageSize, displayContext, displayLinks, apiKey);
    return ObjectConverter.toClassResults(results);
  }

  public List<Ontology> findAllOntologies(boolean includeDetails, String apiKey) throws IOException {
    List<BpOntology> bpOntologies = bpService.findAllOntologies(apiKey);
    List<Ontology> ontologies = new ArrayList<>();
    for (BpOntology o : bpOntologies) {
      // Only keep ontologies. Value set collections will be excluded
      if (o.getType() == null || o.getType().compareTo(BP_API_BASE + BP_ONTOLOGY_TYPE_VS_COLLECTION) != 0) {
        ontologies.add(ObjectConverter.toOntology(o));
      }
    }
    // Get details
    if (includeDetails) {
      int i = 1;
      for (Ontology o : ontologies) {
        o.setDetails(getOntologyDetails(o.getId(), apiKey));
        // Delay between calls (BioPortal requirement)
        try {
          Thread.sleep(BP_API_WAIT_TIME);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        System.out.println("Ontologies loaded in cache: " + i++);
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
      BpOntologyMetrics metrics = bpService.findOntologyMetrics(ontologyId, apiKey);
      details.setNumberOfClasses(metrics.getClasses());
    } catch (HTTPException e) {
      if (e.getStatusCode() == 404) {
        // Do nothing
      }
    }
    // Delay between calls (BioPortal requirement)
    try {
      Thread.sleep(BP_API_WAIT_TIME);
    } catch (InterruptedException e) {
      e.printStackTrace();
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
      if (e.getStatusCode() == 404) {
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
      if (e.getStatusCode() == 404) {
        // Do nothing
      }
    }
    return details;
  }

  public List<OntologyClass> getRootClasses(String ontologyId, String apiKey) throws IOException {
    List<BpClass> bpRoots = bpService.getRootClasses(ontologyId, apiKey);
    List<OntologyClass> roots = new ArrayList<>();
    for (BpClass c : bpRoots) {
      roots.add(ObjectConverter.toOntologyClass(c));
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

  public List<OntologyClass> findAllProvisionalClasses(String ontology, String apiKey) throws IOException {
    List<BpProvisionalClass> provClasses = bpService.findAllProvisionalClasses(ontology, apiKey);
    List<OntologyClass> classes = new ArrayList<>();
    for (BpProvisionalClass pc : provClasses) {
      classes.add(ObjectConverter.toOntologyClass(pc));
    }
    return classes;
  }

  public void updateProvisionalClass(OntologyClass c, String apiKey) throws IOException {
    bpService.updateProvisionalClass(ObjectConverter.toBpProvisionalClass(c), apiKey);
  }

  public void deleteProvisionalClass(String id, String apiKey) throws IOException {
    bpService.deleteProvisionalClass(id, apiKey);
  }

  public List<TreeNode> getClassTree(String id, String ontology, String apiKey) throws IOException {
    List<BpTreeNode> bpNodes = bpService.getClassTree(id, ontology, apiKey);
    List<TreeNode> nodes = new ArrayList<>();
    for (BpTreeNode n : bpNodes) {
      nodes.add(ObjectConverter.toTreeNode(n));
    }
    return nodes;
  }

  public PagedResults<OntologyClass> getClassChildren(String id, String ontology, String apiKey) throws IOException {
    BpPagedResults<BpClass> bpChildren = bpService.getClassChildren(id, ontology, apiKey);
    return ObjectConverter.toClassResults(bpChildren);
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
    BpProvisionalRelation pr = bpService
        .createBpProvisionalRelation(ObjectConverter.toBpProvisionalRelation(r), apiKey);
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
      throw new HTTPException(400);
    }
  }

  public ValueSet findProvisionalValueSet(String id, String apiKey) throws IOException {
    BpProvisionalClass pc = bpService.findBpProvisionalClassById(id, apiKey);
    return ObjectConverter.toValueSet(pc);
  }

  public ValueSet findRegularValueSet(String id, String vsCollection, String apiKey) throws IOException {
    BpClass c = bpService.findBpClassById(id, vsCollection, apiKey);
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
      throw new HTTPException(400);
    }
  }

  public PagedResults<Value> findValuesByValueSet(String vsId, String vsCollection, String apiKey) throws IOException {
    // Check that vsCollection is a valid Value Set Collection
    if (Util.validVsCollection(vsCollection, true)) {
      PagedResults<Value> results = null;
      // Find the value set and check if it is regular or provisional
      ValueSet vs = findValueSet(vsId, vsCollection, apiKey);
      if (!vs.isProvisional()) {
        BpPagedResults<BpClass> bpResults = bpService.findValuesByValueSet(vsId, vsCollection, apiKey);
        results = ObjectConverter.toValueResults(bpResults);
      } else {
        List<Value> values = new ArrayList<>();
        // Get all provisional classes in the vsCollection
        // TODO: Pass vsCollection instead of null. I did not do it because BioPortal returns error 500
        List<OntologyClass> classes = findAllProvisionalClasses(null, apiKey);
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
      throw new HTTPException(400);
    }
  }

  public List<VSCollection> findAllVSCollections(boolean includeDetails, String apiKey) throws IOException {
    List<BpOntology> bpOntologies = bpService.findAllOntologies(apiKey);
    List<VSCollection> vsCollections = new ArrayList<>();
    for (BpOntology o : bpOntologies) {
      if (o.getType() != null && o.getType().compareTo(BP_API_BASE + BP_ONTOLOGY_TYPE_VS_COLLECTION) == 0) {
        vsCollections.add(ObjectConverter.toVSCollection(ObjectConverter.toOntology(o)));
      }
    }
    // Get details
    if (includeDetails) {
      for (VSCollection c : vsCollections) {
        c.setDetails(ObjectConverter.toVSCollectionDetails(getOntologyDetails(c.getId(), apiKey)));
        // Delay between calls (BioPortal requirement)
        try {
          Thread.sleep(BP_API_WAIT_TIME);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
    return vsCollections;
  }

  public List<ValueSet> findAllValueSets(String apiKey) throws IOException {
    List<ValueSet> valueSets = new ArrayList<>();
    int page = 1;
    // We need to retrieve all value sets in one call so we set a higher limit for pageSize. Higher values are not
    // accepted by BioPortal. Bad request is returned
    int pageSize = 5000;
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

  public Value createProvisionalValue(Value v, String apiKey) throws IOException {
    // Creation of value sets is restricted to the CEDARVS value set collection
    if ((v.getVsId() != null) && (Util.validVsCollection(v.getVsCollection(), true))) {
      BpProvisionalClass pc = bpService.createBpProvisionalClass(ObjectConverter.toBpProvisionalClass(v), apiKey);
      return ObjectConverter.toValue(pc);
    } else {
      // Bad request
      throw new HTTPException(400);
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

  public void updateProvisionalValue(Value v, String apiKey) throws IOException {
    bpService.updateProvisionalClass(ObjectConverter.toBpProvisionalClass(v), apiKey);
  }

  public void deleteProvisionalValue(String id, String apiKey) throws IOException {
    bpService.deleteProvisionalClass(id, apiKey);
  }
}
