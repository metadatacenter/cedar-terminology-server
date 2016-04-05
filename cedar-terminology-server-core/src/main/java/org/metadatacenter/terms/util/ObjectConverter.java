package org.metadatacenter.terms.util;

import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.domainObjects.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.metadatacenter.terms.util.Constants.BP_VS_COLLECTIONS_READ;
import static org.metadatacenter.terms.util.Constants.BP_TYPE_BASE;
import static org.metadatacenter.terms.util.Constants.BP_TYPE_CLASS;
import static org.metadatacenter.terms.util.Constants.BP_TYPE_VALUE;
import static org.metadatacenter.terms.util.Constants.BP_TYPE_VS;

public class ObjectConverter {
  /**
   * To BioPortal objects
   **/

  public static BpProvisionalClass toBpProvisionalClass(OntologyClass c) {
    List<BpProvisionalRelation> relations = new ArrayList<>();
    if (c.getRelations() != null) {
      for (Relation r : c.getRelations()) {
        if (r.getCreator() == null) {
          r.setCreator(c.getCreator());
        }
        relations.add(toBpProvisionalRelation(r));
      }
    }
    return new BpProvisionalClass(c.getId(), null, c.getPrefLabel(), c.getCreator(), c.getOntology(), c.getDefinitions(),
        c.getSynonyms(), c.getSubclassOf(), relations, true, c.getCreated());
  }

  public static BpProvisionalClass toBpProvisionalClass(ValueSet vs) {
    List<BpProvisionalRelation> relations = new ArrayList<>();
    if (vs.getRelations() != null) {
      for (Relation r : vs.getRelations()) {
        relations.add(toBpProvisionalRelation(r));
      }
    }
    return new BpProvisionalClass(vs.getId(), null, vs.getPrefLabel(), vs.getCreator(), vs.getVsCollection(),
        vs.getDefinitions(), vs.getSynonyms(), null, relations, true, vs.getCreated());
  }

  public static BpProvisionalClass toBpProvisionalClass(Value v) {
    List<BpProvisionalRelation> relations = new ArrayList<>();
    if (v.getRelations() != null) {
      for (Relation r : v.getRelations()) {
        relations.add(toBpProvisionalRelation(r));
      }
    }
    return new BpProvisionalClass(v.getId(), null, v.getPrefLabel(), v.getCreator(), v.getVsCollection(),
        v.getDefinitions(), v.getSynonyms(), v.getVsId(), relations, true, v.getCreated());
  }

  public static BpProvisionalRelation toBpProvisionalRelation(Relation r) {
    return new BpProvisionalRelation(r.getId(), r.getSourceClassId(), r.getRelationType(), r.getTargetClassId(),
        r.getTargetClassOntology(), r.getCreated(), r.getCreator());
  }

  /**
   * From BioPortal objects
   **/

  public static Ontology toOntology(BpOntology o) {
    return new Ontology(o.getAcronym(), o.getId(), o.getName(), null);
  }

  public static OntologyClass toOntologyClass(BpProvisionalClass pc) {
    boolean provisional = true;
    List<Relation> relations = new ArrayList<>();
    if (pc.getRelations() != null) {
      for (BpProvisionalRelation pr : pc.getRelations()) {
        relations.add(toRelation(pr));
      }
    }
    // We don't know if the provisional class has any children
    Boolean hasChildren = null;
    return new OntologyClass(Util.getShortIdentifier(pc.getId()), pc.getId(), pc.getLabel(), pc.getCreator(), pc
        .getOntology(), pc.getDefinition(),
        pc.getSynonym(), pc.getSubclassOf(), relations, provisional, pc.getCreated(), hasChildren);
  }

  public static OntologyClass toOntologyClass(BpClass pc) {
    return new OntologyClass(Util.getShortIdentifier(pc.getId()), pc.getId(), pc.getPrefLabel(), null, pc.getLinks()
        .getOntology(), pc.getDefinition(),
        pc.getSynonym(), null, null, pc.isProvisional(), null, pc.getHasChildren());
  }

  public static ValueSet toValueSet(BpProvisionalClass pc) {
    boolean provisional = true;
    List<Relation> relations = new ArrayList<>();
    if (pc.getRelations() != null) {
      for (BpProvisionalRelation pr : pc.getRelations()) {
        relations.add(toRelation(pr));
      }
    }
    return new ValueSet(Util.getShortIdentifier(pc.getId()), pc.getId(), pc.getLabel(), pc.getCreator(), pc
        .getOntology(), pc.getDefinition(),
        pc.getSynonym(), relations, provisional, pc.getCreated());
  }

  public static Relation toRelation(BpProvisionalRelation pr) {
    return new Relation(Util.getShortIdentifier(pr.getId()), pr.getId(), pr.getSource(), pr.getRelationType(), pr
        .getTargetClassId(),
        pr.getTargetClassOntology(), pr.getCreated(), pr.getCreator());
  }

  public static ValueSet toValueSet(BpClass c) {
    return new ValueSet(Util.getShortIdentifier(c.getId()), c.getId(), c.getPrefLabel(), null, c.getLinks()
        .getOntology(), c.getDefinition(),
        c.getSynonym(), null, c.isProvisional(), null);
  }

  public static Value toValue(BpClass c) {
    return new Value(Util.getShortIdentifier(c.getId()), c.getId(), c.getPrefLabel(), null, null, c.getLinks()
        .getOntology(), c.getDefinition(),
        c.getSynonym(), null, c.isProvisional(), null);
  }

  public static Value toValue(BpProvisionalClass pc) {
    boolean provisional = true;
    List<Relation> relations = new ArrayList<>();
    if (pc.getRelations() != null) {
      for (BpProvisionalRelation pr : pc.getRelations()) {
        relations.add(toRelation(pr));
      }
    }
    return new Value(Util.getShortIdentifier(pc.getId()), pc.getId(), pc.getLabel(), pc.getCreator(), pc
        .getSubclassOf(), pc.getOntology(), pc.getDefinition(),
        pc.getSynonym(), relations, provisional, pc.getCreated());
  }

  public static PagedResults<ValueSet> toValueSetResults(BpPagedResults<BpClass> bpr) {
    List<ValueSet> valueSets = new ArrayList<>();
    for (BpClass c : bpr.getCollection()) {
      valueSets.add(toValueSet(c));
    }
    return new PagedResults<>(bpr.getPage(), bpr.getPageCount(), bpr.getCollection().size(), bpr.getPrevPage(),
        bpr.getNextPage(), valueSets);
  }

  public static PagedResults<Value> toValueResults(BpPagedResults<BpClass> bpr) {
    List<Value> values = new ArrayList<>();
    for (BpClass c : bpr.getCollection()) {
      values.add(toValue(c));
    }
    return new PagedResults<>(bpr.getPage(), bpr.getPageCount(), bpr.getCollection().size(), bpr.getPrevPage(),
        bpr.getNextPage(), values);
  }

  public static PagedResults<OntologyClass> toClassResults(BpPagedResults<BpClass> bpr) {
    List<OntologyClass> classes = new ArrayList<>();
    for (BpClass c : bpr.getCollection()) {
      classes.add(toOntologyClass(c));
    }
    return new PagedResults<>(bpr.getPage(), bpr.getPageCount(), bpr.getCollection().size(), bpr.getPrevPage(),
        bpr.getNextPage(), classes);
  }

  public static PagedResults<SearchResult> toSearchResults(BpPagedResults<BpClass> bpr, List<String> valueSetsIds) {
    List<SearchResult> results = new ArrayList<>();
    for (BpClass c : bpr.getCollection()) {
      // Assign information depending on the result type
      String type = null;
      String ontology = Util.getShortIdentifier(c.getLinks().getOntology());
      // If the ontology is a value set collection
      if (Arrays.asList(BP_VS_COLLECTIONS_READ).contains(ontology)) {
        // It is a Value Set
        String shortId = c.getId().substring(c.getId().lastIndexOf('/') + 1);
        if (valueSetsIds.contains(shortId)) {
          type = BP_TYPE_VS;
        }
        // It is a Value
        else {
          type = BP_TYPE_VALUE;
        }
      }
      // It is a class
      else {
        type = BP_TYPE_CLASS;
      }

      String source = c.getLinks().getOntology();
      SearchResult r = new SearchResult(c.getId(), c.getId(), BP_TYPE_BASE + type, type, c.getPrefLabel(), source);
      results.add(r);
    }
    return new PagedResults<>(bpr.getPage(), bpr.getPageCount(), bpr.getCollection().size(), bpr.getPrevPage(),
        bpr.getNextPage(), results);
  }

  public static TreeNode toTreeNode(BpTreeNode bpTreeNode) {
    List<TreeNode> children = new ArrayList<>();
    for (BpTreeNode child : bpTreeNode.getChildren()) {
      children.add(toTreeNode(child));
    }
    return new TreeNode(bpTreeNode.getId(), bpTreeNode.getId(), bpTreeNode.getType(), bpTreeNode.getPrefLabel(), bpTreeNode.getLinks().getOntology(), bpTreeNode.getHasChildren(), children, bpTreeNode.isObsolete());
  }

  /**
   * From API object to API object
   */

  public static Value toValue(OntologyClass c) {
    return new Value(c.getId(), c.getLdId(), c.getPrefLabel(), c.getCreator(), c.getSubclassOf(), c.getOntology(), c
        .getDefinitions(),
        c.getSynonyms(), c.getRelations(), c.isProvisional(), c.getCreated());
  }

  public static ValueSetCollection toVSCollection(Ontology o) {
    return new ValueSetCollection(o.getId(), o.getLdId(), o.getName(), toVSCollectionDetails(o.getDetails()));
  }

  public static ValueSetCollectionDetails toVSCollectionDetails(OntologyDetails d) {
    if (d == null) {
      return null;
    }
    return new ValueSetCollectionDetails(d.getDescription(), d.getNumberOfClasses(), d.getCategories(), d
        .getHasOntologyLanguage(), d.getReleased(), d.getCreationDate(), d.getHomepage(), d.getPublication(), d
        .getDocumentation(), d.getVersion());
  }

  public static ValueSet toValueSet(OntologyClass c) {
    return new ValueSet(c.getId(), c.getLdId(), c.getPrefLabel(), c.getCreator(), c.getOntology(), c.getDefinitions(), c
        .getSynonyms(), c.getRelations(), c.isProvisional(), c.getCreated());
  }

}
