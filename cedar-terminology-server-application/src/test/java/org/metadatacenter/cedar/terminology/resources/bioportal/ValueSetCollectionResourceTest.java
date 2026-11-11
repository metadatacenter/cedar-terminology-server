package org.metadatacenter.cedar.terminology.resources.bioportal;

import jakarta.ws.rs.core.GenericType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metadatacenter.terms.domainObjects.ValueSetCollection;

import java.util.List;

import static org.metadatacenter.cedar.terminology.resources.FakeBioPortal.*;

/**
 * The value-set collection listing.
 */
public class ValueSetCollectionResourceTest extends AbstractTerminologyServerResourceTest {

  @Test
  public void findAllVSCollectionsTest() {
    List<ValueSetCollection> collections = read(baseUrlBpVSCollections, new GenericType<>() {});
    // The registry lists ontologies too, and only the collections are kept.
    Assertions.assertEquals(List.of(CEDARVS, NLMVS, VS_COLLECTION),
        collections.stream().map(ValueSetCollection::getId).toList());
  }

}
