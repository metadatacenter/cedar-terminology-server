package utils;

/**
 * Constants of general utility.
 *
 * All member of this class are immutable.
 */
public class Constants {

  public static final String BP_ENDPOINT = "bioportal/";
  public static final String BP_SEARCH = "search";
  public static final String BP_CLASSES = "classes";
  public static final String BP_ONTOLOGIES = "ontologies";
  public static final String BP_RELATIONS = "relations";
  public static final String BP_VALUE_SET_COLLECTIONS = "vs-collections";
  public static final String BP_VALUE_SETS = "value-sets";
  public static final String BP_VALUES = "values";
  public static final String BP_PROVISIONAL_CLASSES = BP_CLASSES + "/provisional";
//  public static final String BP_PROVISIONAL_RELATIONS = BP_RELATIONS + "/provisional";
//  public static final String BP_PROVISIONAL_VALUE_SETS = BP_VALUE_SETS + "/provisional";
//  public static final String BP_PROVISIONAL_VALUES = BP_VALUES + "/provisional";

  public static final String PLAY_MODULE_FOLDER_NAME = "cedar-terminology-server-play";
  public static final String PLAY_APP_FOLDER_NAME = "cedar-terminology-server";
  public static final String PLAY_TEST_FOLDER_NAME = "modules";
  public static final String CACHE_FOLDER_NAME = "cache-objects";
  public static final String ONTOLOGIES_CACHE_FILE = "ontologies.cache";

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