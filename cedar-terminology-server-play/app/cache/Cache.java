package cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import controllers.TerminologyController;
import org.metadatacenter.terms.domainObjects.Ontology;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static utils.Constants.*;

public class Cache {

  public static String apiKeyCache = "8c478417-cae7-473d-bcd7-a03cf229bb5d"; // cedar-public user api key
  private static ScheduledExecutorService executor;
  private static final int refreshInitialDelay = 0;
  private static final int refreshDelay = 7;
  private static final TimeUnit delayUnit = TimeUnit.HOURS;
  private static String ontologiesCachePath;
  private static boolean firstExecution = true;

  static {
    ontologiesCachePath = getCacheObjectsPath() + "/" + ONTOLOGIES_CACHE_FILE;
    executor = Executors.newSingleThreadScheduledExecutor();
  }

  public static void init() {
    executor.scheduleWithFixedDelay(
        new Runnable() {
          public void run() {
            ontologiesCache.refresh("ontologies");
          }
        }, refreshInitialDelay, refreshDelay, delayUnit);
  }

  // Google Guava cache for all ontologies. It has been implemented as a single-object cache that will contain a
  // LinkedHashMap with all ontologies, so that it is possible both getting them as an ordered list and quickly
  // access to specific ontologies by id.
  public static LoadingCache<String, LinkedHashMap<String, Ontology>> ontologiesCache = CacheBuilder.newBuilder()
      .maximumSize(1)
          //.expireAfterAccess(5, TimeUnit.SECONDS)
          //.recordStats()
      .build(new CacheLoader<String, LinkedHashMap<String, Ontology>>() {
        @Override
        public LinkedHashMap<String, Ontology> load(String s) throws IOException {
          return getAllOntologiesAsMap();
        }

        @Override
        public ListenableFuture<LinkedHashMap<String, Ontology>> reload(final String key, LinkedHashMap<String,
            Ontology> prevOntologies) {
          // asynchronous!
          ListenableFutureTask<LinkedHashMap<String, Ontology>> task = ListenableFutureTask.create(new Callable<LinkedHashMap<String, Ontology>>() {
            public LinkedHashMap<String, Ontology> call() throws IOException {
              return getAllOntologiesAsMap();
            }
          });
          executor.execute(task);
          return task;
        }
      });

  public static LinkedHashMap<String, Ontology> getAllOntologiesAsMap() throws IOException {
    List<Ontology> ontologies = null;
    if (firstExecution && new File(ontologiesCachePath).isFile()) {
      ontologies = readOntologiesFromFile();
    } else {
      ontologies = TerminologyController.termService.findAllOntologies(true, apiKeyCache);
      saveOntologiesToFile(ontologies);
    }
    firstExecution = false;
    LinkedHashMap lhm = new LinkedHashMap();
    for (Ontology o : ontologies) {
      lhm.put(o.getId(), o);
    }
    return lhm;
  }

  public static void saveOntologiesToFile(List<Ontology> ontologies) {
    try {
      ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(ontologiesCachePath));
      out.writeObject(ontologies);
      out.flush();
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static List<Ontology> readOntologiesFromFile() {
    List<Ontology> ontologies = null;
    // Check if the file exists
    if (!new File(ontologiesCachePath).isFile()) {
      return null;
    } else {
      ObjectInputStream in = null;
      try {
        in = new ObjectInputStream(new FileInputStream(ontologiesCachePath));
        ontologies = (List<Ontology>) in.readObject();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
      return ontologies;
    }
  }

  private static String getCacheObjectsPath() {
    String path;
    String workingDirectory = System.getProperty("user.dir");
    // Get last fragment of working directory
    String folder = workingDirectory.substring(workingDirectory.lastIndexOf("/") + 1, workingDirectory.length());
    // Working directory for Maven execution (mvn play2:run)
    if (folder.compareTo(PLAY_MODULE_FOLDER_NAME) == 0) {
      path = CACHE_FOLDER_NAME;
    }
    // Working directory for execution from IntelliJ
    else if (folder.compareTo(PLAY_APP_FOLDER_NAME)==0) {
      path = PLAY_MODULE_FOLDER_NAME + "/" + CACHE_FOLDER_NAME;
    }
    // Working directory for test execution from IntelliJ (working directory: ...cedar-terminology-server/.idea/modules)
    else {
      path = "../../" + PLAY_MODULE_FOLDER_NAME + "/" + CACHE_FOLDER_NAME;
    }
    return path;
  }

}
