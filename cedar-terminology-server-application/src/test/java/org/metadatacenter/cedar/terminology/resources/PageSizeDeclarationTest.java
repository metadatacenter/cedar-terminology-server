package org.metadatacenter.cedar.terminology.resources;

import jakarta.ws.rs.QueryParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.resources.bioportal.ClassResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.SearchResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.ValueResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.ValueSetResource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every paged GET route declares {@code limit} and {@code offset}, and none declares the withdrawn
 * page-number parameters.
 *
 * <p>Asserted by reflection over the declared surface rather than against a list written here, so a
 * new route that declares a page number fails. A declaration is not a reading: {@code PageSizeResourceTest}
 * asks one route of each family for a page and asserts what it answers.
 */
class PageSizeDeclarationTest {

  private static final List<Class<?>> RESOURCES = List.of(ClassResource.class, SearchResource.class,
      ValueResource.class, ValueSetResource.class, VersionAwareSearchResource.class,
      VersionedPropertyResource.class);
  private static final Set<String> WITHDRAWN = Set.of("page", "pageSize", "page_size");

  private static Set<String> queryParameters(Method method) {
    return Arrays.stream(method.getParameters())
        .map(p -> p.getAnnotation(QueryParam.class))
        .filter(q -> q != null)
        .map(QueryParam::value)
        .collect(Collectors.toSet());
  }

  private static List<Method> pagedMethods() {
    List<Method> found = new ArrayList<>();
    for (Class<?> resource : RESOURCES) {
      for (Method method : resource.getDeclaredMethods()) {
        if (queryParameters(method).contains("limit")) {
          found.add(method);
        }
      }
    }
    return found;
  }

  @Test
  @DisplayName("The reflection finds the routes it is meant to check")
  void theSurfaceIsNotEmpty() {
    assertFalse(pagedMethods().isEmpty(), "No paged routes were found by reflection, so this test asserts nothing");
  }

  @Test
  @DisplayName("Every paged route declares limit and offset")
  void everyPagedRouteDeclaresLimitAndOffset() {
    for (Method method : pagedMethods()) {
      assertTrue(queryParameters(method).contains("offset"),
          method.getDeclaringClass().getSimpleName() + "." + method.getName() + " declares limit without offset");
    }
  }

  @Test
  @DisplayName("No route declares page, pageSize or page_size")
  void noRouteDeclaresAPageNumber() {
    for (Class<?> resource : RESOURCES) {
      for (Method method : resource.getDeclaredMethods()) {
        Set<String> declared = queryParameters(method);
        declared.retainAll(WITHDRAWN);
        assertEquals(Set.of(), declared,
            resource.getSimpleName() + "." + method.getName() + " declares a withdrawn page-number parameter");
      }
    }
  }
}
