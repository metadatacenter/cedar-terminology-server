package org.metadatacenter.terms.util;

import org.metadatacenter.terms.bioportal.domainObjects.BpClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalClass;
import org.metadatacenter.terms.bioportal.domainObjects.BpProvisionalRelation;
import org.metadatacenter.terms.bioportal.customObjects.BpSearchResults;
import org.metadatacenter.terms.domainObjects.OntologyClass;
import org.metadatacenter.terms.domainObjects.Relation;
import org.metadatacenter.terms.customObjects.SearchResults;
import org.metadatacenter.terms.domainObjects.Value;
import org.metadatacenter.terms.domainObjects.ValueSet;

import java.util.ArrayList;
import java.util.List;

public class ObjectConverter
{
  /**
   * To BioPortal objects
   **/

  public static BpProvisionalClass toBpProvisionalClass(OntologyClass c)
  {
    List<BpProvisionalRelation> relations = new ArrayList<>();
    if (c.getRelations() != null) {
      for (Relation r : c.getRelations()) {
        relations.add(toBpProvisionalRelation(r));
      }
    }
    return new BpProvisionalClass(c.getId(), null, c.getLabel(), c.getCreator(), c.getOntology(), c.getDefinitions(),
      c.getSynonyms(), c.getSubclassOf(), relations, true, c.getCreated());
  }

  public static BpProvisionalClass toBpProvisionalClass(ValueSet vs)
  {
    List<BpProvisionalRelation> relations = new ArrayList<>();
    if (vs.getRelations() != null) {
      for (Relation r : vs.getRelations()) {
        relations.add(toBpProvisionalRelation(r));
      }
    }
    return new BpProvisionalClass(vs.getId(), null, vs.getLabel(), vs.getCreator(), vs.getVsCollection(),
      vs.getDefinitions(), vs.getSynonyms(), null, relations, true, vs.getCreated());
  }

  public static BpProvisionalRelation toBpProvisionalRelation(Relation r)
  {
    return new BpProvisionalRelation(r.getId(), r.getSourceClassId(), r.getRelationType(), r.getTargetClassId(),
      r.getTargetClassOntology(), r.getCreated());
  }

  /**
   * From BioPortal objects
   **/

  public static OntologyClass toOntologyClass(BpProvisionalClass pc)
  {
    List<Relation> relations = new ArrayList<>();
    if (pc.getRelations() != null) {
      for (BpProvisionalRelation pr : pc.getRelations()) {
        relations.add(toRelation(pr));
      }
    }
    return new OntologyClass(pc.getId(), pc.getLabel(), pc.getCreator(), pc.getOntology(), pc.getDefinition(),
      pc.getSynonym(), pc.getSubclassOf(), relations, true, pc.getCreated());
  }

  public static ValueSet toValueSet(BpProvisionalClass pc)
  {
    List<Relation> relations = new ArrayList<>();
    if (pc.getRelations() != null) {
      for (BpProvisionalRelation pr : pc.getRelations()) {
        relations.add(toRelation(pr));
      }
    }
    return new ValueSet(pc.getId(), pc.getLabel(), pc.getCreator(), pc.getOntology(), pc.getDefinition(),
      pc.getSynonym(), relations, true, pc.getCreated());
  }

  public static Relation toRelation(BpProvisionalRelation pr)
  {
    return new Relation(pr.getId(), pr.getSource(), pr.getRelationType(), pr.getTargetClassId(),
      pr.getTargetClassOntology(), pr.getCreated());
  }

  public static ValueSet toValueSet(BpClass c)
  {
    return new ValueSet(c.getId(), c.getPrefLabel(), null, c.getLinks().getOntology(), c.getDefinition(),
      c.getSynonym(), null, false, null);
  }

  public static Value toValue(BpClass c)
  {
    return new Value(c.getId(), c.getPrefLabel(), null, null, c.getLinks().getOntology(), c.getDefinition(),
      c.getSynonym(), null, false, null);
  }

  public static SearchResults<ValueSet> toValueSetResults(BpSearchResults<BpClass> bpr)
  {
    List<ValueSet> valueSets = new ArrayList<>();
    for (BpClass c : bpr.getCollection()) {
      valueSets.add(toValueSet(c));
    }
    return new SearchResults<>(bpr.getPage(), bpr.getPageCount(), bpr.getCollection().size(), bpr.getPrevPage(),
      bpr.getNextPage(), valueSets);
  }

  public static SearchResults<Value> toValueResults(BpSearchResults<BpClass> bpr)
  {
    List<Value> values = new ArrayList<>();
    for (BpClass c : bpr.getCollection()) {
      values.add(toValue(c));
    }
    return new SearchResults<>(bpr.getPage(), bpr.getPageCount(), bpr.getCollection().size(), bpr.getPrevPage(),
      bpr.getNextPage(), values);
  }

}
