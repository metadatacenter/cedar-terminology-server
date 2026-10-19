package org.metadatacenter.terms.bioportal.domainObjects.jackson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.metadatacenter.model.policy.JsonSourcePolicy;
import org.metadatacenter.terms.bioportal.domainObjects.BpTreeNode;
import org.metadatacenter.terms.bioportal.domainObjects.BpProperty;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NestedSourcePolicyTest {
  private final ObjectMapper mapper = JsonSourcePolicy.apply(new ObjectMapper());
  @Test void nestedTreesConsumeTheirOwnValueAndKeepRootTrailingTokenChecks() throws Exception {
    String source = "[{\"@id\":\"urn:root\",\"children\":[{\"@id\":\"urn:child\"}]},{\"@id\":\"urn:other\"}]";
    var nodes = mapper.readValue(source, new TypeReference<List<BpTreeNode>>() {});
    assertEquals(2, nodes.size());
    assertEquals("urn:child", nodes.get(0).getChildren().get(0).getId());
    assertThrows(Exception.class, () -> mapper.readValue(source + "{}", new TypeReference<List<BpTreeNode>>() {}));
  }
  @Test void nestedPropertiesAndDuplicateDetectionShareTheRequestParser() throws Exception {
    var properties = mapper.readValue("[{\"@id\":\"urn:a\"},{\"@id\":\"urn:b\"}]", new TypeReference<List<BpProperty>>() {});
    assertEquals(2, properties.size());
    assertThrows(Exception.class, () -> mapper.readValue("[{\"@id\":\"urn:a\",\"@id\":\"urn:b\"}]", new TypeReference<List<BpProperty>>() {}));
  }
}
