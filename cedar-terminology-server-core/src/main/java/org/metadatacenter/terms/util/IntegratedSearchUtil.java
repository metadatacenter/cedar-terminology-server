package org.metadatacenter.terms.util;

import org.metadatacenter.cedar.terminology.validation.integratedsearch.OntologyValueConstraint;
import org.metadatacenter.cedar.terminology.validation.integratedsearch.ValueConstraints;
import org.metadatacenter.terms.customObjects.PagedResults;
import org.metadatacenter.terms.domainObjects.SearchResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IntegratedSearchUtil {

  public static List<String> extractOntologyAcronyms(List<OntologyValueConstraint> ontologyVCs) {
    List<String> ontologyAcronyms = new ArrayList<>();
    for (OntologyValueConstraint ontologyVC : ontologyVCs) {
      ontologyAcronyms.add(ontologyVC.getAcronym());
    }
    return ontologyAcronyms;
  }

  public static boolean hasMultipleSources(ValueConstraints valueConstraints) {
    int totalSources = valueConstraints.getOntologies().size() + valueConstraints.getBranches().size() + valueConstraints.getValueSets().size();
    if (valueConstraints.getClasses().size() > 0) {
      totalSources++;
    }
    if (totalSources > 1) {
      return true;
    }
    else {
      return false;
    }
  }

//  public static PagedResults<SearchResult> concatResultsAndRecalculatePagination(PagedResults<SearchResult> results, PagedResults<SearchResult> newResults) {
//    List<SearchResult> allResults = results.getCollection();
//    allResults.addAll(newResults.getCollection());
//
//    return new PagedResults()
//
//        results.getCollection().addAll(newResults.getCollection());
//  }

}
