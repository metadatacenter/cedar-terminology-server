package org.metadatacenter.terms;

import org.metadatacenter.terms.bioportal.BioPortalService;
import org.metadatacenter.terms.bioportal.customObjects.BpSearchResults;
import org.metadatacenter.terms.bioportal.domainObjects.BpClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalRelation;
import org.metadatacenter.terms.customObjects.SearchResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;
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

  private final int connectTimeout;
  private final int socketTimeout;
  private BioPortalService bpService;

  public TerminologyService(int connectTimeout, int socketTimeout)
  {
    this.connectTimeout = connectTimeout;
    this.socketTimeout = socketTimeout;
    this.bpService = new BioPortalService(connectTimeout, socketTimeout);
  }

  public SearchResults<OntologyClass> search(String q, List<String> scope, List<String> sources, int page, int pageSize,
    boolean displayContext, boolean displayLinks, String apiKey) throws IOException
  {
    BpSearchResults results = bpService.search(q, scope, sources, page, pageSize, displayContext, displayLinks, apiKey);
    return ObjectConverter.toClassResults(results);
  }

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

  public Value createProvisionalValue(Value v, String apiKey) throws IOException
  {
    // Creation of value sets is restricted to the CEDARVS value set collection
    if ((v.getVsId() != null) && (v.getVsCollection() != null) && (Arrays.asList(BP_VS_CREATION_COLLECTIONS).contains(v.getVsCollection()))) {
      BpProvisionalClass pc = bpService.createBpProvisionalClass(ObjectConverter.toBpProvisionalClass(v), apiKey);
      return ObjectConverter.toValue(pc);
    } else {
      // Bad request
      throw new HTTPException(400);
    }
  }
}
