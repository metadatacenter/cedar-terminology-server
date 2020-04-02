package org.metadatacenter.terms.util;

import org.metadatacenter.cedar.terminology.validation.integratedsearch.OntologyValueConstraint;

import java.util.ArrayList;
import java.util.List;

public class IntegratedSearchUtil {

  public static List<String> extractOntologyAcronyms(List<OntologyValueConstraint> ontologyVCs) {
    List<String> ontologyAcronyms = new ArrayList<>();
    for (OntologyValueConstraint ontologyVC : ontologyVCs) {
      ontologyAcronyms.add(ontologyVC.getAcronym());
    }
    return ontologyAcronyms;
  }

}
