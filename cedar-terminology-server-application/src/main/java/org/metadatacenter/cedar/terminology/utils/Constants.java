package org.metadatacenter.cedar.terminology.utils;

/**
 * Constants of general utility.
 *
 * All member of this class are immutable.
 */
public class Constants {

  public static final String BP_ENDPOINT = "bioportal";
  public static final String BP_SEARCH = "search";
  public static final String BP_CLASSES = "classes";
  public static final String BP_ONTOLOGIES = "ontologies";
  public static final String BP_RELATIONS = "relations";
  public static final String BP_VALUE_SET_COLLECTIONS = "vs-collections";
  public static final String BP_VALUE_SETS = "value-sets";
  public static final String BP_VALUES = "values";
  public static final String BP_PROVISIONAL_CLASSES = BP_CLASSES + "/provisional";
  public static final String CACHE_FOLDER_NAME = "cache/terminology";
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