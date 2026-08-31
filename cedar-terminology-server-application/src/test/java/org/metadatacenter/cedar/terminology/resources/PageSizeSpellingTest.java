package org.metadatacenter.cedar.terminology.resources;

import jakarta.ws.rs.QueryParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.resources.bioportal.ClassResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.SearchResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.ValueResource;
import org.metadatacenter.cedar.terminology.resources.bioportal.ValueSetResource;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every route that takes a page size takes it in both spellings.
 *
 * <p>This server declared the same parameter as {@code page_size} on two routes and {@code pageSize} on
 * nine. JAX-RS binds an unmatched name to the default silently, so a client that learned one spelling
 * received the default page size from every route that used the other, with nothing in the response to
 * say the parameter had been ignored.
 *
 * <p>Asserted by reflection over the declared surface rather than against a list written here, so a new
 * route that accepts only one spelling fails.
 */
class PageSizeSpellingTest {

  private static final Set<String> BOTH_SPELLINGS = Set.of("page_size", "pageSize");

  private static List<Method> methodsTakingAPageSize() {
    List<Method> found = new ArrayList<>();
    for (Class<?> resource : List.of(ClassResource.class, SearchResource.class,
        ValueResource.class, ValueSetResource.class)) {
      for (Method method : resource.getDeclaredMethods()) {
        boolean takesOne = Arrays.stream(method.getParameters())
            .map(p -> p.getAnnotation(QueryParam.class))
            .anyMatch(q -> q != null && BOTH_SPELLINGS.contains(q.value()));
        if (takesOne) {
          found.add(method);
        }
      }
    }
    return found;
  }

  @Test
  @DisplayName("The reflection finds the routes it is meant to check")
  void theSurfaceIsNotEmpty() {
    assertFalse(methodsTakingAPageSize().isEmpty(),
        "No page-size routes were found by reflection, so this test asserts nothing");
  }

  @Test
  @DisplayName("Every page-size route accepts page_size and pageSize")
  void everyRouteAcceptsBothSpellings() {
    for (Method method : methodsTakingAPageSize()) {
      Set<String> spellings = Arrays.stream(method.getParameters())
          .map(p -> p.getAnnotation(QueryParam.class))
          .filter(q -> q != null && BOTH_SPELLINGS.contains(q.value()))
          .map(QueryParam::value)
          .collect(java.util.stream.Collectors.toSet());
      assertEquals(BOTH_SPELLINGS, spellings,
          method.getDeclaringClass().getSimpleName() + "." + method.getName()
              + " accepts only " + spellings + "; a client using the other spelling gets the default silently");
    }
  }

  @Test
  @DisplayName("Both spellings arrive as separate int parameters, so neither shadows the other")
  void theTwoSpellingsAreDistinctParameters() {
    for (Method method : methodsTakingAPageSize()) {
      List<Parameter> pageSizeParams = Arrays.stream(method.getParameters())
          .filter(p -> {
            QueryParam q = p.getAnnotation(QueryParam.class);
            return q != null && BOTH_SPELLINGS.contains(q.value());
          })
          .toList();
      assertEquals(2, pageSizeParams.size(),
          method.getName() + " should bind each spelling to its own parameter");
      assertTrue(pageSizeParams.stream().allMatch(p -> p.getType() == int.class),
          method.getName() + " should take both as int, so an absent one arrives as the 0 sentinel");
    }
  }
}
