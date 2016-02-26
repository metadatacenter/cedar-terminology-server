package org.metadatacenter.terms.util;

/**
 * Constants of general utility.
 *
 * All member of this class are immutable.
 */
public class Constants
{
  // max calls/second allowed = 15 -> 1 call each 66 ms
  public static int BP_API_WAIT_TIME = 80;

  /** BioPortal API endpoints */
  public static String BP_API_BASE;
  public static final String BP_SEARCH = "search";
  public static final String BP_ONTOLOGIES = "ontologies/";
  public static final String BP_CLASSES = "classes/";
  public static final String BP_PROVISIONAL_CLASSES = "provisional_classes/";
  public static final String BP_PROVISIONAL_RELATIONS = "provisional_relations/";

  /** BioPortal Search endpoint - Search scopes */
  public static final String BP_SEARCH_SCOPE_ALL = "all";
  public static final String BP_SEARCH_SCOPE_CLASSES = "classes";
  public static final String BP_SEARCH_SCOPE_VALUE_SETS = "value_sets";
  public static final String BP_SEARCH_SCOPE_VALUES = "values";

  /** Other constants **/
  public static final String BP_VS_COLLECTIONS = BP_ONTOLOGIES;
  // BioPortal VS collections
  public static final String[] BP_VS_COLLECTIONS_READ = {"CEDARVS", "NLMVS"};
  // Creation of value sets is restricted to this VS collections
  public static final String[] BP_VS_COLLECTIONS_WRITE = {"CEDARVS"};

  //public static final String BP_ONTOLOGY_TYPE_ONTOLOGY = "ontology_types/ONTOLOGY";
  public static final String BP_ONTOLOGY_TYPE_VS_COLLECTION = "ontology_types/VALUE_SET_COLLECTION";

  // PRIVATE //

  /**
   The caller references the constants using Constants.EMPTY_STRING,
   and so on. Thus, the caller should be prevented from constructing objects of
   this class, by declaring this private constructor.
   */
  private Constants()
  {
    // This restricts instantiation
    throw new AssertionError();
  }
}
