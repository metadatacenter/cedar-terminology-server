package org.metadatacenter.cedar.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.terminology.resources.AbstractTerminologyServerResource;
import org.metadatacenter.terms.ITerminologyService;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.ValueSet;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two answers {@link Cache} holds for an hour: whether an ontology is flat, and the identifiers of every value set.
 * Each is held only once fetched cleanly, and fetched again once the hour has passed.
 */
class CacheTest {

  private static final long HOUR = 60 * 60 * 1000L;

  private final AtomicLong now = new AtomicLong(1_000_000L);
  /** What the terminology service is asked, in order: the method and its first argument. */
  private final List<String> asked = new ArrayList<>();
  private ITerminologyService saved;
  private RuntimeException ontologyFailure;
  private boolean flat;
  private List<String> valueSetIds = List.of("vs1");

  @BeforeEach
  void useAFakeServiceAndClock() {
    saved = AbstractTerminologyServerResource.terminologyService;
    AbstractTerminologyServerResource.injectTerminologyService((ITerminologyService) Proxy.newProxyInstance(
        ITerminologyService.class.getClassLoader(), new Class<?>[]{ITerminologyService.class}, (proxy, method, args) -> {
          switch (method.getName()) {
            case "findOntology" -> {
              asked.add("findOntology " + args[0]);
              if (ontologyFailure != null) {
                throw ontologyFailure;
              }
              Ontology ontology = new Ontology();
              ontology.setIsFlat(flat);
              return ontology;
            }
            case "findAllValueSets" -> {
              asked.add("findAllValueSets");
              List<ValueSet> valueSets = new ArrayList<>();
              for (String id : valueSetIds) {
                ValueSet valueSet = new ValueSet();
                valueSet.setId(id);
                valueSets.add(valueSet);
              }
              return valueSets;
            }
            default -> throw new UnsupportedOperationException(method.getName());
          }
        }));
    Cache.clear();
    Cache.useClock(now::get);
  }

  @AfterEach
  void restore() {
    Cache.useClock(System::currentTimeMillis);
    Cache.clear();
    AbstractTerminologyServerResource.injectTerminologyService(saved);
  }

  @Test
  void aFailedFlatnessLookupIsAskedAgainOnTheNextCall() throws Exception {
    ontologyFailure = new IllegalStateException("BioPortal did not answer");
    assertFalse(Cache.isFlat("EX"), "a failed lookup falls back to hierarchical");
    ontologyFailure = null;
    flat = true;
    assertTrue(Cache.isFlat("EX"), "the fallback was held instead of asking again");
    assertEquals(List.of("findOntology EX", "findOntology EX"), asked);
  }

  @Test
  void flatnessIsHeldForAnHourAndThenFetchedAgain() throws Exception {
    flat = true;
    assertTrue(Cache.isFlat("EX"));
    flat = false;
    now.addAndGet(HOUR - 1);
    assertTrue(Cache.isFlat("EX"), "within the hour the held answer is served");
    assertEquals(1, asked.size());
    now.addAndGet(1);
    assertFalse(Cache.isFlat("EX"), "after the hour the answer is fetched again");
    assertEquals(2, asked.size());
  }

  @Test
  void valueSetIdentifiersAreHeldForAnHourAndThenFetchedAgain() throws Exception {
    assertEquals(Set.of("vs1"), Cache.getValueSetIds());
    valueSetIds = List.of("vs1", "vs2");
    now.addAndGet(HOUR - 1);
    assertEquals(Set.of("vs1"), Cache.getValueSetIds(), "within the hour the held identifiers are served");
    now.addAndGet(1);
    assertEquals(Set.of("vs1", "vs2"), Cache.getValueSetIds(), "after the hour they are fetched again");
    assertEquals(List.of("findAllValueSets", "findAllValueSets"), asked);
  }

  @Test
  void clearingDropsBothHolds() throws Exception {
    flat = true;
    Cache.isFlat("EX");
    Cache.getValueSetIds();
    Cache.clear();
    Cache.isFlat("EX");
    Cache.getValueSetIds();
    assertEquals(List.of("findOntology EX", "findAllValueSets", "findOntology EX", "findAllValueSets"), asked);
  }
}
