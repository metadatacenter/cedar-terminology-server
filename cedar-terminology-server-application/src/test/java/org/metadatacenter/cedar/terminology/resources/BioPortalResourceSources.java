package org.metadatacenter.cedar.terminology.resources;

import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The source files of the BioPortal resource classes, which two tests scan for a pattern the resources must not use.
 * <p>
 * The directory is found from Maven's {@code basedir}, then from the module directory, then from the repository root.
 * A run from anywhere else is reported as skipped. Either test used to return early there, which a runner reported as
 * a pass without a file having been read.
 */
final class BioPortalResourceSources {

  private static final String RELATIVE = "src/main/java/org/metadatacenter/cedar/terminology/resources/bioportal";
  private static final String MODULE = "cedar-terminology-server-application";

  private BioPortalResourceSources() {
  }

  /** Every Java source file of the BioPortal resources, never empty. */
  static List<Path> files() throws IOException {
    List<Path> candidates = new ArrayList<>();
    String basedir = System.getProperty("basedir");
    if (basedir != null) {
      candidates.add(Path.of(basedir, RELATIVE));
    }
    candidates.add(Path.of(RELATIVE));
    candidates.add(Path.of(MODULE, RELATIVE));
    for (Path directory : candidates) {
      if (Files.isDirectory(directory)) {
        try (Stream<Path> walk = Files.walk(directory)) {
          List<Path> files = walk.filter(p -> p.toString().endsWith(".java")).toList();
          if (files.isEmpty()) {
            throw new AssertionError("no BioPortal resource sources under " + directory);
          }
          return files;
        }
      }
    }
    throw new TestAbortedException("the BioPortal resource sources are under none of " + candidates);
  }
}
