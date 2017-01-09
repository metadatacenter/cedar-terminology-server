package org.metadatacenter.cedar.cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
//import controllers.TerminologyController;
import org.metadatacenter.terms.domainObjects.Ontology;
import org.metadatacenter.terms.domainObjects.ValueSet;
//import play.Logger;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.metadatacenter.cedar.terminology.utils.Constants.VALUE_SETS_CACHE_FILE;
import static org.metadatacenter.cedar.terminology.utils.Constants.ONTOLOGIES_CACHE_FILE;
import static org.metadatacenter.cedar.terminology.utils.Constants.CACHE_FOLDER_NAME;

public class Cache {

  public static String apiKeyCache = "8c478417-cae7-473d-bcd7-a03cf229bb5d"; // cedar-public user api key
  private static ScheduledExecutorService executor;
  private static final int refreshInitialDelay = 0;
  private static final int refreshDelay = 13;
  private static final TimeUnit delayUnit = TimeUnit.HOURS;
  private static String valueSetsCachePath;
  private static String ontologiesCachePath;
  private static boolean firstLoadValueSets = true;
  private static boolean firstLoadOntologies = true;

  static {
    valueSetsCachePath = getCacheObjectsPath() + "/" + VALUE_SETS_CACHE_FILE;
    ontologiesCachePath = getCacheObjectsPath() + "/" + ONTOLOGIES_CACHE_FILE;
    executor = Executors.newSingleThreadScheduledExecutor();
  }

  public static void init() {
    //Logger.info("Initializing cache");
    executor.scheduleWithFixedDelay(
        new Runnable() {
          public void run() {
            valueSetsCache.refresh("value-sets");
            ontologiesCache.refresh("ontologies");
          }
        }, refreshInitialDelay, refreshDelay, delayUnit);
  }

  // Google Guava cache for all value sets. It has been implemented as a single-object cache that will contain a
  // LinkedHashMap with all value sets, so that it is possible both getting them as an ordered list and quickly
  // access to specific value sets by id.
  public static LoadingCache<String, LinkedHashMap<String, ValueSet>> valueSetsCache = CacheBuilder.newBuilder()
      .maximumSize(1)
      .build(new CacheLoader<String, LinkedHashMap<String, ValueSet>>() {

        public LinkedHashMap<String, ValueSet> load(String s) throws IOException {
          //Logger.info("Loading 'value sets' cache");
          return getAllValueSetsAsMap();
        }

//        public ListenableFuture<LinkedHashMap<String, ValueSet>> reload(final String key, LinkedHashMap<String,
//            ValueSet> prevValueSets) {
//          // asynchronous!
//          Logger.info("Reloading 'value sets' cache asynchronously");
//          ListenableFutureTask<LinkedHashMap<String, ValueSet>> task = ListenableFutureTask.create(new Callable<LinkedHashMap<String, ValueSet>>() {
//            public LinkedHashMap<String, ValueSet> call() throws IOException {
//              return getAllValueSetsAsMap();
//            }
//          });
//          executor.execute(task);
//          return task;
//        }
      });

  // Google Guava cache for all ontologies. It has been implemented as a single-object cache that will contain a
  // LinkedHashMap with all ontologies, so that it is possible both getting them as an ordered list and quickly
  // access to specific ontologies by id.
  public static LoadingCache<String, LinkedHashMap<String, Ontology>> ontologiesCache = CacheBuilder.newBuilder().maximumSize(1)
          //.expireAfterAccess(5, TimeUnit.SECONDS)
          //.recordStats()
      .build(new CacheLoader<String, LinkedHashMap<String, Ontology>>() {

        public LinkedHashMap<String, Ontology> load(String s) throws IOException {
          //Logger.info("Loading 'ontologies' cache");
          return getAllOntologiesAsMap();
        }

        public ListenableFuture<LinkedHashMap<String, Ontology>> reload(final String key, LinkedHashMap<String,
            Ontology> prevOntologies) {
          // asynchronous!
          //Logger.info("Reloading 'ontologies' cache asynchronously");
          ListenableFutureTask<LinkedHashMap<String, Ontology>> task = ListenableFutureTask.create(new Callable<LinkedHashMap<String, Ontology>>() {
            public LinkedHashMap<String, Ontology> call() throws IOException {
              return getAllOntologiesAsMap();
            }
          });
          executor.execute(task);
          return task;
        }
      });

  private static LinkedHashMap<String, ValueSet> getAllValueSetsAsMap() throws IOException {
    List<ValueSet> valueSets = null;
    if (firstLoadValueSets && new File(valueSetsCachePath).isFile()) {
      //Logger.info("Loading value sets from file");
      valueSets = readValueSetsFromFile();
    } else {
      //Logger.info("Loading value sets from BioPortal");
      // TODO: update this line to make it work with the Dropwizard project
      //valueSets = TerminologyController.termService.findAllValueSets(apiKeyCache);
      saveValueSetsToFile(valueSets);
    }
    firstLoadValueSets = false;
    LinkedHashMap lhm = new LinkedHashMap();
    for (ValueSet vs : valueSets) {
      lhm.put(vs.getId(), vs);
    }
    //Logger.info("Value sets loaded");
    return lhm;
  }

  private static LinkedHashMap<String, Ontology> getAllOntologiesAsMap() throws IOException {
    List<Ontology> ontologies = null;
    if (firstLoadOntologies && new File(ontologiesCachePath).isFile()) {
      //Logger.info("Loading ontologies from file");
      ontologies = readOntologiesFromFile();
    } else {
      //Logger.info("Loading ontologies from BioPortal");
      // TODO: update this line to make it work with the Dropwizard project
      //ontologies = TerminologyController.termService.findAllOntologies(true, apiKeyCache);
      saveOntologiesToFile(ontologies);
    }
    firstLoadOntologies = false;
    LinkedHashMap lhm = new LinkedHashMap();
    for (Ontology o : ontologies) {
      lhm.put(o.getId(), o);
    }
    //Logger.info("Ontologies loaded");
    return lhm;
  }

  private static void saveValueSetsToFile(List<ValueSet> ontologies) {
    //Logger.info("Saving value sets to file");
    try {
      // Create dirs if they don't exist
      File targetFile = new File(ontologiesCachePath);
      File parent = targetFile.getParentFile();
      if(!parent.exists() && !parent.mkdirs()){
        throw new IllegalStateException("Couldn't create dir: " + parent);
      }
      ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(valueSetsCachePath));
      out.writeObject(ontologies);
      out.flush();
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    //Logger.info("Value sets saved to file");
  }

  private static void saveOntologiesToFile(List<Ontology> ontologies) {
    //Logger.info("Saving ontologies to file");
    try {
      // Create dirs if they don't exist
      File targetFile = new File(ontologiesCachePath);
      File parent = targetFile.getParentFile();
      if(!parent.exists() && !parent.mkdirs()){
        throw new IllegalStateException("Couldn't create dir: " + parent);
      }
      ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(ontologiesCachePath));
      out.writeObject(ontologies);
      out.flush();
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    //Logger.info("Ontologies saved to file");
  }

  private static List<ValueSet> readValueSetsFromFile() {
    List<ValueSet> valueSets = null;
    // Check if the file exists
    if (!new File(valueSetsCachePath).isFile()) {
      return null;
    } else {
      ObjectInputStream in = null;
      try {
        in = new ObjectInputStream(new FileInputStream(valueSetsCachePath));
        valueSets = (List<ValueSet>) in.readObject();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
      return valueSets;
    }
  }

  private static List<Ontology> readOntologiesFromFile() {
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
    String path = System.getenv("CEDAR_HOME") + CACHE_FOLDER_NAME;
    return path;
  }

}
