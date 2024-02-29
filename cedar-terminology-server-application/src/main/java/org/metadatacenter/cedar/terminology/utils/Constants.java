package org.metadatacenter.cedar.terminology.utils;

/**
 * Constants of general utility.
 *
 * All member of this class are immutable.
 */
public class Constants {

  // cedar-public user api key
  public static final String BP_PUBLIC_API_KEY = "8c478417-cae7-473d-bcd7-a03cf229bb5d";
  public static final String BP_ENDPOINT = "bioportal";
  public static final String BP_SEARCH = "search";
  public static final String BP_INTEGRATED_SEARCH = "integrated-search";
  public static final String BP_INTEGRATED_RETRIEVE = "integrated-retrieve";
  public static final String BP_PROPERTY_SEARCH = "property_search";
  public static final String BP_CLASSES = "classes";
  public static final String BP_PROPERTIES = "properties";
  public static final String BP_ONTOLOGIES = "ontologies";
  public static final String BP_TREE = "tree";
  public static final String BP_CHILDREN = "children";
  public static final String BP_DESCENDANTS = "descendants";
  public static final String BP_PARENTS = "parents";
  public static final String BP_ROOTS = "roots";
  public static final String BP_RELATIONS = "relations";
  public static final String BP_VALUE_SET_COLLECTIONS = "vs-collections";
  public static final String BP_VALUE_SETS = "value-sets";
  public static final String BP_VALUES = "values";
  public static final String BP_ALL_VALUES = "all-values";
  public static final String BP_PROVISIONAL_CLASSES = BP_CLASSES + "/provisional";
  public static final String CACHE_FOLDER_NAME = "cache/terminology";
  public static final String TEST_CACHE_FOLDER_NAME = "src/test/resources/test_cache";
  public static final String ONTOLOGIES_CACHE_FILE = "ontologies.cache";
  public static final String VALUE_SETS_CACHE_FILE = "value-sets.cache";

  // PRIVATE //

  /**
   * The caller references the constants using Constants.EMPTY_STRING,
   * and so on. Thus, the caller should be prevented from constructing objects of
   * this class, by declaring this private constructor.
   */
  private Constants() {
    // This restricts instantiation
    throw new AssertionError();
  }

}