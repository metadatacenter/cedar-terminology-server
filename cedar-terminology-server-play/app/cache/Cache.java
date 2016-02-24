package cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import controllers.TerminologyController;
import org.metadatacenter.terms.domainObjects.Ontology;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.*;

public class Cache {

  public static String apiKeyCache = "c4f0cadc-cec4-4ca6-9093-372d92804876"; // apikey for cedar-test user
  private static ScheduledExecutorService executor;

  // Google Guava cache for all ontologies. It has been implemented as a single-object cache that will contain a
  // LinkedHashMap with all ontologies, so that it is possible both getting them as an ordered list and quickly
  // access to specific ontologies by id.
  public static LoadingCache<String, LinkedHashMap<String, Ontology>> ontologiesCache = CacheBuilder.newBuilder()
      .maximumSize(1)
          //.expireAfterAccess(24, TimeUnit.HOURS)
      //.expireAfterAccess(5, TimeUnit.SECONDS)
      .recordStats()
      .build(new CacheLoader<String, LinkedHashMap<String, Ontology>>() {
        public LinkedHashMap<String, Ontology> load(String s) throws IOException {
          List<Ontology> ontologies = TerminologyController.termService.findAllOntologies(true, apiKeyCache);
          LinkedHashMap lhm = new LinkedHashMap();
          for (Ontology o : ontologies) {
            lhm.put(o.getId(), o);
          }
          return lhm;
        }

        public ListenableFuture<LinkedHashMap<String, Ontology>> reload(final String key, LinkedHashMap<String,
            Ontology> prevOntologies) {
          // asynchronous!
          ListenableFutureTask<LinkedHashMap<String, Ontology>> task = ListenableFutureTask.create(new Callable<LinkedHashMap<String, Ontology>>() {
            public LinkedHashMap<String, Ontology> call() {
              List<Ontology> ontologies = null;
              try {
                ontologies = TerminologyController.termService.findAllOntologies(true, apiKeyCache);
              } catch (IOException e) {
                e.printStackTrace();
              }
              LinkedHashMap lhm = new LinkedHashMap();
              for (Ontology o : ontologies) {
                lhm.put(o.getId(), o);
              }
              return lhm;
            }
          });
          executor.execute(task);
          return task;
        }
      });

  public static void loadAll() {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    executor.scheduleWithFixedDelay(
        new Runnable() {
          public void run() {
            ontologiesCache.refresh("ontologies");
          }
        }, 0, 24, TimeUnit.HOURS);

//    try {
//      ontologiesCache.get("ontologies");
//    } catch (ExecutionException e) {
//      e.printStackTrace();
//    }
  }
}
