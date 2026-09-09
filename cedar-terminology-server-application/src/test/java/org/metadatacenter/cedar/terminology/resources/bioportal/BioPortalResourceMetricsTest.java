package org.metadatacenter.cedar.terminology.resources.bioportal;

import com.codahale.metrics.annotation.Timed;
import jakarta.ws.rs.HttpMethod;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BioPortalResourceMetricsTest {

  private static final List<Class<?>> RESOURCE_CLASSES = List.of(
      ClassResource.class,
      IntegratedRetrieveResource.class,
      IntegratedSearchResource.class,
      OntologyResource.class,
      PropertyResource.class,
      RelationResource.class,
      SearchResource.class,
      ValueResource.class,
      ValueSetCollectionResource.class,
      ValueSetResource.class
  );

  @Test
  void everyHttpEndpointRecordsLatency() {
    RESOURCE_CLASSES.stream()
        .flatMap(resourceClass -> Arrays.stream(resourceClass.getDeclaredMethods()))
        .filter(BioPortalResourceMetricsTest::isHttpEndpoint)
        .forEach(method -> assertTrue(method.isAnnotationPresent(Timed.class),
            () -> method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                + " must be annotated with @Timed"));
  }

  private static boolean isHttpEndpoint(Method method) {
    return Arrays.stream(method.getAnnotations())
        .map(Annotation::annotationType)
        .anyMatch(annotationType -> annotationType.isAnnotationPresent(HttpMethod.class));
  }
}
