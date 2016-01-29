package org.metadatacenter.terminology.services;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.List;

public interface IBioPortalService
{
  JsonNode search(String q, List<String> scope, List<String> sources, int page, int pageSize, boolean displayContext,
    boolean displayLinks, String apiKey) throws IOException;

  /** TODO:
   * - Get all ontologies
   * - Get ontology details
   * - Get ontology size
   * - Get ontology categories
   * - Get class details / and value set details
   * - Get class tree
   * - Get class children
   * - Get class parents
   * - Get ontology tree root
   * - Get all value sets in a value set collection
   * - Get ontology classes
   *
   * ==== NEW ====
   *
   * Provisional classes and relations
   * - Create a provisional class
   * - Retrieve a provisional class by id
   * - Retrieve all provisional classes
   * - Update a provisional class [TO BE RELEASED]
   * - Delete a provisional class [TO BE RELEASED]
   * - Add a provisional relation to an existing provisional class
   * - Retrieve a provisional relation by id
   * - Delete a provisional relation [TO BE RELEASED]
   *
   * Value Sets
   * - Create a Value Set
   * - Return all value sets in a specific value set collection
   * - Return all values in a value set (including provisional classes)
   * - Retrieve value set by id
   * - Update value set
   * - Delete value set
   * Value Set Items
   * - Create a Value Set Item
   * - Retrieve value set item by id
   * - Update value set item
   * - Delete value set item
   *
   */
}
