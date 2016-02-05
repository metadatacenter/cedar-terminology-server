package org.metadatacenter.terminology.util;

import org.metadatacenter.terminology.bioportal.domainObjects2.bioportal.BpProvisionalClass;
import org.metadatacenter.terminology.bioportal.domainObjects2.bioportal.BpProvisionalRelation;
import org.metadatacenter.terminology.bioportal.domainObjects2.custom.OntologyClass;
import org.metadatacenter.terminology.bioportal.domainObjects2.custom.Relation;

import java.util.ArrayList;
import java.util.List;

public class ObjectConverter
{
  /** To BioPortal objects **/
  
  public static BpProvisionalClass toBpProvisionalClass(OntologyClass c) {
    List<BpProvisionalRelation> relations = new ArrayList<>();
    if (c.getRelations() != null) {
      for (Relation r : c.getRelations()) {
        relations.add(toBpProvisionalRelation(r));
      }
    }
    return new BpProvisionalClass(c.getId(), null, c.getLabel(), c.getCreator(), c.getOntology(), c.getDefinitions(),
      c.getSynonyms(), c.getSubclassOf(), relations, true, c.getCreated());
  }

  public static BpProvisionalRelation toBpProvisionalRelation(Relation r) {
    return new BpProvisionalRelation(r.getId(), r.getSourceClassId(), r.getRelationType(), r.getTargetClassId(),
      r.getTargetClassOntology(), r.getCreated());
  }
  
  /** From BioPortal objects **/
  
  public static OntologyClass toOntologyClass(BpProvisionalClass pc) {
    List<Relation> relations = new ArrayList<>();
    if (pc.getRelations() != null) {
      for (BpProvisionalRelation pr : pc.getRelations()) {
        relations.add(toRelation(pr));
      }
    }
    return new OntologyClass(pc.getId(), pc.getLabel(), pc.getCreator(), pc.getOntology(), pc.getDefinition(),
      pc.getSynonym(), pc.getSubclassOf(), relations, true, pc.getCreated());
  }

  public static Relation toRelation(BpProvisionalRelation pr) {
    return new Relation(pr.getId(), pr.getSource(), pr.getRelationType(), pr.getTargetClassId(),
      pr.getTargetClassOntology(), pr.getCreated());
  }
  
}
