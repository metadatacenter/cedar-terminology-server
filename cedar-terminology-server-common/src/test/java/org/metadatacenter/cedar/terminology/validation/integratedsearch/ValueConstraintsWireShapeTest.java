package org.metadatacenter.cedar.terminology.validation.integratedsearch;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.metadatacenter.artifacts.model.core.fields.constraints.ControlledTermValueConstraints;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValueConstraintsWireShapeTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void terminologyDtosMatchArtifactLibraryWireShape() {
    assertEquals(abstractProperties(ControlledTermValueConstraints.class), localFields(ValueConstraints.class));
    assertEquals(recordComponents(
            org.metadatacenter.artifacts.model.core.fields.constraints.OntologyValueConstraint.class),
        localFields(OntologyValueConstraint.class));
    assertEquals(recordComponents(
            org.metadatacenter.artifacts.model.core.fields.constraints.BranchValueConstraint.class),
        localFields(BranchValueConstraint.class));
    assertEquals(recordComponents(
            org.metadatacenter.artifacts.model.core.fields.constraints.ValueSetValueConstraint.class),
        localFields(ValueSetValueConstraint.class));
    assertEquals(recordComponents(
            org.metadatacenter.artifacts.model.core.fields.constraints.ClassValueConstraint.class),
        localFields(ClassValueConstraint.class));
    assertEquals(recordComponents(
            org.metadatacenter.artifacts.model.core.fields.constraints.ControlledTermValueConstraintsAction.class),
        localFields(Action.class));
    assertEquals(recordComponents(
            org.metadatacenter.artifacts.model.core.fields.constraints.VersionSpec.class),
        ConstraintVersionDeserializer.VERSION_FIELDS);
  }

  @Test
  void structuredVersionRetainsTheArtifactLibraryShape() throws Exception {
    String json = "{\"acronym\":\"DOID\",\"version\":{" +
        "\"id\":\"sha256:123\",\"effectiveDate\":\"2026-07-01\"," +
        "\"declaredVersion\":\"2026-07\"}}";

    OntologyValueConstraint constraint = mapper.readValue(json, OntologyValueConstraint.class);

    assertEquals("sha256:123", constraint.getVersion());
    assertEquals("2026-07-01", constraint.versionSpec().effectiveDate().orElseThrow());
    assertEquals("2026-07", constraint.versionSpec().declaredVersion().orElseThrow());
  }

  @Test
  void unknownConstraintFieldsFailLoudly() {
    String json = "{\"ontologies\":[],\"branches\":[],\"valueSets\":[],\"classes\":[]," +
        "\"futureRoutingField\":\"do-not-drop-me\"}";
    ObjectMapper dropwizardStyleMapper = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    assertThrows(JsonMappingException.class,
        () -> dropwizardStyleMapper.readValue(json, ValueConstraints.class));
  }

  @Test
  void unknownNestedConstraintFieldsFailLoudly() {
    String json = "{\"acronym\":\"DOID\",\"futureRoutingField\":\"do-not-drop-me\"}";
    ObjectMapper dropwizardStyleMapper = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    assertThrows(JsonMappingException.class,
        () -> dropwizardStyleMapper.readValue(json, OntologyValueConstraint.class));
  }

  private static Set<String> abstractProperties(Class<?> sourceType) {
    return Arrays.stream(sourceType.getMethods())
        .filter(method -> Modifier.isAbstract(method.getModifiers()))
        .filter(method -> method.getParameterCount() == 0)
        .map(Method::getName)
        .collect(Collectors.toSet());
  }

  private static Set<String> recordComponents(Class<?> sourceType) {
    return Arrays.stream(sourceType.getRecordComponents())
        .map(RecordComponent::getName)
        .collect(Collectors.toSet());
  }

  private static Set<String> localFields(Class<?> localType) {
    return Arrays.stream(localType.getDeclaredFields())
        .filter(field -> !Modifier.isStatic(field.getModifiers()))
        .map(field -> field.getName())
        .collect(Collectors.toSet());
  }
}
