package org.metadatacenter.terms.util;

import org.apache.commons.lang.StringUtils;
import org.metadatacenter.terms.domainObjects.SearchResult;

import java.util.Comparator;

public class SearchResultComparator implements Comparator<SearchResult> {

  private final String query;

  public SearchResultComparator(String query) {
    this.query = query.toLowerCase();
  }

  @Override
  public int compare(SearchResult r1, SearchResult r2) {
    String prefLabel1 = r1.getPrefLabel().toLowerCase();
    String prefLabel2 = r2.getPrefLabel().toLowerCase();

    if (prefLabel1.contains(query) && !prefLabel2.contains(query)) {
      return -1;
    }
    else if (!prefLabel1.contains(query) && prefLabel2.contains(query)) {
      return 1;
    }
    else { // both contain the query. Use Levenshtein
      Integer distance1 = StringUtils.getLevenshteinDistance(prefLabel1, query);
      Integer distance2 = StringUtils.getLevenshteinDistance(prefLabel2, query);
      return distance1.compareTo(distance2);
    }
  }

}