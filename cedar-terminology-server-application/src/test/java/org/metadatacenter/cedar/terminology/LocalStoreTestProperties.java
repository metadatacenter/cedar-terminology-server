package org.metadatacenter.cedar.terminology;

import java.util.List;

/**
 * The system properties through which a test points the application at a local store.
 *
 * <p>The application reads them once, at startup, and surefire runs every class in one JVM. A class
 * that sets them and leaves them set therefore decides how every later class boots: after the
 * equivalence harness, every suite ran in its strict local-only mode, and production's fallback
 * routing was never started. Each class that sets them clears them all when it finishes.
 */
public final class LocalStoreTestProperties {

  private static final List<String> NAMES = List.of(
      TerminologyServerApplication.PROP_CATALOG_PATH,
      TerminologyServerApplication.PROP_LOCAL_ONTOLOGIES,
      TerminologyServerApplication.PROP_LOCAL_ROOTS_ONTOLOGIES,
      TerminologyServerApplication.PROP_LOCAL_ONLY,
      TerminologyServerApplication.PROP_SEARCH_INDEX_PATH);

  private LocalStoreTestProperties() {
  }

  public static void clear() {
    NAMES.forEach(System::clearProperty);
  }
}
