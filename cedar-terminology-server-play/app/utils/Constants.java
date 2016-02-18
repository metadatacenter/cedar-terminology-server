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
  public static final String BP_PROVISIONAL_CLASSES = BP_CLASSES + "/provisional";

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