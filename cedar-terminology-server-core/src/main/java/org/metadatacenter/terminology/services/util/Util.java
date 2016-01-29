package org.metadatacenter.terminology.services.util;

public class Util
{
  public static String getBioPortalAuthHeader(String apikey) {
    return "apikey token=" + apikey;
  }

}
