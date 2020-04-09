package org.metadatacenter.terms.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.terms.bioportal.customObjects.BpPagedResults;
import org.metadatacenter.terms.bioportal.domainObjects.*;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.metadatacenter.cedar.terminology.util.Constants.*;
import static org.metadatacenter.util.json.JsonMapper.MAPPER;

public class ObjectConverter {

  /**
   * From Terminology Server objects to BioPortal objects
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

  public static List<BpTreeNode> toBpTreeNodeList(JsonNode nodes) {
    List<BpTreeNode> treeNodes = new ArrayList<>();
    for (JsonNode node : nodes) {
      treeNodes.add(MAPPER.convertValue(node, BpTreeNode.class));
    }
    return treeNodes;
  }

  /**
   * From BioPortal objects to Terminology Server objects
   **/

  public static Ontology toOntology(BpOntology o) {
    // Note that some BioPortal ontologies have the "flat" parameter set to null. In those cases isFlat will be set to false
    boolean isFlat = o.getIsFlat()? true : false;
    return new Ontology(o.getAcronym(), o.getId(), o.getName(), isFlat, null);
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

  public static OntologyClass toOntologyClass(BpClass c) {
    return new OntologyClass(Util.getShortIdentifier(c.getId()), c.getId(), c.getPrefLabel(),
        null, c.getLinks().getOntology(), toListOfString(c.getDefinition()),
        c.getSynonym(), null, null, c.isProvisional(), null, c.getHasChildren());
  }

  public static ValueSet toValueSet(BpProvisionalClass pc) {
    boolean provisional = true;
    List<Relation> relations = new ArrayList<>();
    if (pc.getRelations() != null) {
      for (BpProvisionalRelation pr : pc.getRelations()) {
        relations.add(toRelation(pr));
      }
    }
    return new ValueSet(Util.getShortIdentifier(pc.getId()), pc.getId(), pc.getLabel(), pc.getCreator(),
        Util.getShortIdentifier(pc.getOntology()), pc.getDefinition(), pc.getSynonym(), relations, provisional, pc.getCreated());
  }

  public static Relation toRelation(BpProvisionalRelation pr) {
    return new Relation(Util.getShortIdentifier(pr.getId()), pr.getId(), pr.getSource(), pr.getRelationType(), pr
        .getTargetClassId(),
        pr.getTargetClassOntology(), pr.getCreated(), pr.getCreator());
  }

  public static ValueSet toValueSet(BpClass c) {
    return new ValueSet(Util.getShortIdentifier(c.getId()), c.getId(), c.getPrefLabel(), null, c.getLinks()
        .getOntology(), toListOfString(c.getDefinition()),
        c.getSynonym(), null, c.isProvisional(), null);
  }

  public static Value toValue(BpClass c) {
    String notation = null;
    String relatedMatch = null;
    if (Util.getShortIdentifier(c.getLinks().getOntology()).equals(CADSR_VALUE_SETS_ONTOLOGY)) {
      if (c.getProperties() != null) {
        if (c.getProperties().containsKey(SKOS_NOTATION_IRI) && c.getProperties().get(SKOS_NOTATION_IRI).size() > 0) {
          notation = c.getProperties().get(SKOS_NOTATION_IRI).get(0);
        }
        if (c.getProperties().containsKey(SKOS_RELATEDMATCH_IRI) && c.getProperties().get(SKOS_RELATEDMATCH_IRI).size() > 0) {
          relatedMatch = c.getProperties().get(SKOS_RELATEDMATCH_IRI).get(0);
        }
      }
    }
    return new Value(Util.getShortIdentifier(c.getId()), c.getId(), c.getPrefLabel(), notation, relatedMatch,
        null, null, c.getLinks().getOntology(), toListOfString(c.getDefinition()),
        c.getSynonym(), null, c.isProvisional(), null);
  }

  public static Value toValue(BpProvisionalClass pc) {
    boolean provisional = true;
    String notation = null;
    String relatedMatch = null;
    List<Relation> relations = new ArrayList<>();
    if (pc.getRelations() != null) {
      for (BpProvisionalRelation pr : pc.getRelations()) {
        relations.add(toRelation(pr));
      }
    }
    return new Value(Util.getShortIdentifier(pc.getId()), pc.getId(), pc.getLabel(), notation, relatedMatch,
        pc.getCreator(), pc.getSubclassOf(), pc.getOntology(), pc.getDefinition(),
        pc.getSynonym(), relations, provisional, pc.getCreated());
  }

  public static OntologyProperty toOntologyProperty(BpProperty p) {
    String ontology = p.getLinks().getOntology();
    // Note that for null lists, we return an empty list
    return new OntologyProperty(p.getId(), p.getId(), p.getType(), Util.getShortType(p.getType()),
        p.getPrefLabel(),
        p.getLabel() == null ? new ArrayList<>() : p.getLabel(),
        p.getDefinition() == null ? new ArrayList<>() : p.getDefinition(), ontology, p.getHasChildren());
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
      if (isValid(c)) {
        classes.add(toOntologyClass(c));
      }
    }
    return new PagedResults<>(bpr.getPage(), bpr.getPageCount(), bpr.getCollection().size(), bpr.getPrevPage(),
        bpr.getNextPage(), classes);
  }

  public static PagedResults<OntologyClass> toClassResultsFromProvClassResults(BpPagedResults<BpProvisionalClass> bpr) {
    List<OntologyClass> classes = new ArrayList<>();
    for (BpProvisionalClass c : bpr.getCollection()) {
      classes.add(toOntologyClass(c));
    }
    return new PagedResults<>(bpr.getPage(), bpr.getPageCount(), bpr.getCollection().size(), bpr.getPrevPage(),
        bpr.getNextPage(), classes);
  }

  public static PagedResults<SearchResult> toPagedSearchResults(BpPagedResults<BpClass> bpr, List<String> valueSetsIds) {
    List<SearchResult> results = new ArrayList<>();
    for (BpClass c : bpr.getCollection()) {
      // Assign information depending on the result type
      String type = null;
      String ontology = Util.getShortIdentifier(c.getLinks().getOntology());
      // If the ontology is a value set collection
      if (Arrays.asList(BP_VS_COLLECTIONS_READ).contains(ontology)) {
        String shortId = Util.getShortIdentifier(c.getId());
        // It is a Value Set
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

      String definition = null;
      if (c.getDefinition() != null && c.getDefinition().size() > 0) {
        definition = toListOfString(c.getDefinition()).get(0);
      }

      String source = c.getLinks().getOntology();
      SearchResult r = new SearchResult(c.getId(), c.getId(), BP_TYPE_BASE + type, type, c.getPrefLabel(), definition, source);
      results.add(r);
    }
    return new PagedResults<>(bpr.getPage(), bpr.getPageCount(), bpr.getCollection().size(), bpr.getPrevPage(),
        bpr.getNextPage(), results);
  }

  public static PagedResults<SearchResult> toPagedSearchResults(BpPagedResults<BpProperty> bpProperties) {
    List<SearchResult> results = new ArrayList<>();
    for (BpProperty bpProperty : bpProperties.getCollection()) {
      String definition = null;
      if (bpProperty.getDefinition() != null && bpProperty.getDefinition().size() > 0) {
        definition = bpProperty.getDefinition().get(0);
      }
      String source = bpProperty.getLinks().getOntology();
      SearchResult searchResult = new SearchResult(bpProperty.getId(), bpProperty.getId(), bpProperty.getType(),
          Util.getShortType(bpProperty.getType()), bpProperty.getPrefLabel(), definition, source);
      results.add(searchResult);
    }
    return new PagedResults<>(bpProperties.getPage(), bpProperties.getPageCount(), bpProperties.getCollection().size(),
        bpProperties.getPrevPage(), bpProperties.getNextPage(), results);
  }

  public static List<OntologyProperty> toPropertyListResults(List<BpProperty> bpr) {
    List<OntologyProperty> results = new ArrayList<>();
    for (BpProperty p : bpr) {
      OntologyProperty r = toOntologyProperty(p);
      results.add(r);
    }
    return results;
  }

  public static TreeNode toTreeNode(BpTreeNode bpTreeNode) {
    List<TreeNode> children = new ArrayList<>();
    if (bpTreeNode.getHasChildren()) {
      for (BpTreeNode child : bpTreeNode.getChildren()) {
        children.add(toTreeNode(child));
      }
    }
    String ldType = bpTreeNode.getType();
    String type = Util.getShortType(ldType);
    if (type.equals("Class")) {
      type = BP_TYPE_CLASS;
    }
    return new TreeNode(bpTreeNode.getId(), bpTreeNode.getId(), ldType, type, bpTreeNode.getPrefLabel(), bpTreeNode.getLinks().getOntology(), bpTreeNode.getHasChildren(), children, bpTreeNode.isObsolete());
  }

  // Transforms a value set and its values into a tree node
  public static TreeNode toTreeNode(ValueSet vs, List<Value> values) {
    List<TreeNode> children = new ArrayList<>();
    boolean hasChildren = false;
    if (values.size() > 0) {
      hasChildren = true;
      for (Value v : values) {
        children.add(toTreeNode(v));
      }
    }
    return new TreeNode(vs.getId(), vs.getLdId(), vs.getLdType(), vs.getType(), vs.getPrefLabel(), vs.getVsCollection(), hasChildren, children, false);
  }

  public static TreeNode toTreeNode(Value value) {
    // Values do not have any children -> Emtpy list
    List<TreeNode> children = new ArrayList<>();
    return new TreeNode(value.getId(), value.getLdId(), value.getLdType(), value.getType(), value.getPrefLabel(), value.getVsCollection(), false, children, false);
  }

  public static TreeNode toTreeNodeNoChildren(OntologyClass c) {
    List<TreeNode> children = new ArrayList<>();
    return new TreeNode(c.getId(), c.getLdId(), c.getLdType(), c.getType(), c.getPrefLabel(), c.getOntology(),  false, children, false);
  }

  /**
   * From Terminology Server object to a different Terminology Server object
   */

  public static Value toValue(OntologyClass c) {
    String notation = null;
    String relatedMatch = null;
    return new Value(c.getId(), c.getLdId(), c.getPrefLabel(), notation, relatedMatch, c.getCreator(),
        c.getSubclassOf(), c.getOntology(), c.getDefinitions(),
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

  public static SearchResult toSearchResult(OntologyClass c) {

    String definition = null;
    if (c.getDefinitions() != null && c.getDefinitions().size() > 0) {
      definition = c.getDefinitions().get(0);
    }

    return new SearchResult(c.getId(), c.getLdId(), c.getLdType(), c.getType(), c.getPrefLabel(),
        definition, c.getOntology());
  }

  public static SearchResult toSearchResult(Value v) {

    String definition = null;
    if (v.getDefinitions() != null && v.getDefinitions().size() > 0) {
      definition = v.getDefinitions().get(0);
    }

    return new SearchResult(v.getId(), v.getLdId(), v.getLdType(), v.getType(), v.getPrefLabel(),
        definition, v.getVsCollection());
  }

  public static PagedResults<SearchResult> classResultsToSearchResults(PagedResults<OntologyClass> classPagedResults) {

    List<SearchResult>  results = new ArrayList<>();
    for (OntologyClass c : classPagedResults.getCollection()) {
      results.add(toSearchResult(c));
    }

    return new PagedResults<>(classPagedResults.getPage(), classPagedResults.getPageCount(),
        classPagedResults.getCollection().size(), classPagedResults.getPrevPage(),
        classPagedResults.getNextPage(), results);
  }

  public static PagedResults<SearchResult> valueResultsToSearchResults(PagedResults<Value> valuePagedResults) {

    List<SearchResult>  results = new ArrayList<>();
    for (Value v : valuePagedResults.getCollection()) {
      results.add(toSearchResult(v));
    }

    return new PagedResults<>(valuePagedResults.getPage(), valuePagedResults.getPageCount(),
        valuePagedResults.getCollection().size(), valuePagedResults.getPrevPage(),
        valuePagedResults.getNextPage(), results);
  }

  /**
   * Utils
   */

  public static List<String> toListOfString(List<Object> objects) {
    List<String> strings = new ArrayList<>();
    if (objects != null) {
      for (Object object : objects) {
        strings.add(Objects.toString(object, null));
      }
    }
    return strings;
  }

  // Checks if the class is valid to be accepted by CEDAR. For instance, classes with null values are considered invalid
  // (BiPortal issue: https://github.com/ncbo/ontologies_linked_data/issues/81)
  public static boolean isValid(BpClass c) {
    if (c.getPrefLabel() == null) {
      return false;
    }
    else {
      return true;
    }
  }

}
