package cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import controllers.TerminologyController;
import org.metadatacenter.terms.TerminologyService;
import org.metadatacenter.terms.domainObjects.Ontology;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.*;

import static utils.Constants.PATH_RESOURCES;

public class Cache {

  public static String apiKeyCache = "c4f0cadc-cec4-4ca6-9093-372d92804876"; // apikey for cedar-test user
  private static ScheduledExecutorService executor;
  private static final int refreshInitialDelay = 0;
  private static final int refreshDelay = 7;
  private static final TimeUnit delayUnit = TimeUnit.HOURS;
  private static String ontologiesFilePath = PATH_RESOURCES + "ontologies.dat";
  private static boolean firstExecution = true;

  static {
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
      .recordStats()
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
    if (firstExecution && new File(ontologiesFilePath).isFile()) {
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
      ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(ontologiesFilePath));
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
    if (!new File(ontologiesFilePath).isFile()) {
      return null;
    } else {
      ObjectInputStream in = null;
      try {
        in = new ObjectInputStream(new FileInputStream(ontologiesFilePath));
        ontologies = (List<Ontology>) in.readObject();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
      return ontologies;
    }
  }

}
