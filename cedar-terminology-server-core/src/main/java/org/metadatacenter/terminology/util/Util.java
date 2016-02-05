package org.metadatacenter.terminology.util;

public class Util
{
  public static String getBioPortalAuthHeader(String apikey) {
    return "apikey token=" + apikey;
  }

}
