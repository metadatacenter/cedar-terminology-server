package org.metadatacenter.terms.ingest;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.metadatacenter.terms.store.SnapshotProperties;
import org.openrdf.model.Literal;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.rio.*;
import org.openrdf.rio.helpers.RDFHandlerBase;
import org.openrdf.rio.helpers.XMLParserSettings;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.*;

/**
 * Extract property signatures and asserted superproperties from retained bytes, without fetching
 * imports.
 */
public final class PropertyExtractor {
  public List<SnapshotProperties.Property> extract(Path file) throws Exception {
    String head;
    try (InputStream in = Files.newInputStream(file)) {
      head =
          new String(in.readNBytes(8192), java.nio.charset.StandardCharsets.UTF_8).stripLeading();
    }
    // The legacy Sesame RDF/XML reader trims literal whitespace. Keep OWLAPI's native
    // RDF/XML path so existing property hashes and annotation text remain unchanged.
    if (head.contains("<rdf:RDF") || head.contains("<RDF")) return extractOwl(file);
    if (head.startsWith("@prefix")
        || head.startsWith("@base")
        || head.startsWith("PREFIX")
        || head.startsWith("BASE")
        || head.startsWith("<http")) return extractRdf(file, RDFFormat.TURTLE);
    // OBO, OWL/XML and functional syntax use OWLAPI's native parsers.
    return extractOwl(file);
  }

  private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
  private static final String OWL = "http://www.w3.org/2002/07/owl#";
  private static final String SUBPROPERTY = "http://www.w3.org/2000/01/rdf-schema#subPropertyOf";

  private static final Set<String> SYNTAX_IRIS = new HashSet<>();

  static {
    for (var value : org.semanticweb.owlapi.vocab.OWLRDFVocabulary.values())
      SYNTAX_IRIS.add(value.getIRI().toString());
    for (var value : org.semanticweb.owlapi.vocab.OWLFacet.values())
      SYNTAX_IRIS.add(value.getIRI().toString());
    for (var value : org.semanticweb.owlapi.vocab.SWRLVocabulary.values())
      SYNTAX_IRIS.add(value.getIRI().toString());
  }

  private static boolean vocabulary(String iri) {
    return SYNTAX_IRIS.contains(iri);
  }

  private static void stream(Path file, RDFFormat format, RDFHandler handler) throws Exception {
    RDFParser parser = Rio.createParser(format);
    // Anonymous restriction identifiers must be identical across the two passes.
    parser.setPreserveBNodeIDs(true);
    parser.setValueFactory(
        new org.openrdf.model.impl.ValueFactoryImpl() {
          private long next;

          @Override
          public org.openrdf.model.BNode createBNode() {
            return new org.openrdf.model.impl.BNodeImpl("generated-" + next++);
          }

          @Override
          public org.openrdf.model.BNode createBNode(String id) {
            return new org.openrdf.model.impl.BNodeImpl("named-" + id);
          }
        });
    parser.getParserConfig().set(XMLParserSettings.LOAD_EXTERNAL_DTD, false);
    parser.getParserConfig().set(XMLParserSettings.SECURE_PROCESSING, true);
    parser.setRDFHandler(handler);
    try (InputStream in = Files.newInputStream(file)) {
      parser.parse(in, file.toUri().toString());
    }
  }

  /**
   * Two Turtle/NTriples streaming passes retain the property subgraph and a bounded set of usage
   * witnesses. OWLAPI still interprets property kinds and axioms, but never loads the millions of
   * class annotations in files such as MEDGEN. Witness subjects retain their types so undeclared
   * annotation properties are interpreted in the same context as in the full ontology.
   */
  private List<SnapshotProperties.Property> extractRdf(Path file, RDFFormat format)
      throws Exception {
    Set<String> properties = new HashSet<>();
    Map<String, Statement> witnesses = new HashMap<>();
    Set<org.openrdf.model.Resource> witnessSubjects = new HashSet<>();
    Set<org.openrdf.model.Resource> restrictions = new HashSet<>();
    stream(
        file,
        format,
        new RDFHandlerBase() {
          @Override
          public void handleStatement(Statement st) {
            String predicate = st.getPredicate().stringValue();
            String object = st.getObject().stringValue();
            if (!vocabulary(predicate)) {
              properties.add(predicate);
              String key =
                  predicate + (st.getObject() instanceof Literal ? " literal" : " resource");
              if (!witnesses.containsKey(key)) {
                witnesses.put(key, st);
                witnessSubjects.add(st.getSubject());
              }
            }
            if (predicate.equals(RDF_TYPE)
                && st.getSubject() instanceof URI
                && (object.equals(OWL + "ObjectProperty")
                    || object.equals(OWL + "DatatypeProperty")
                    || object.equals(OWL + "AnnotationProperty")
                    || object.equals(OWL + "TransitiveProperty")
                    || object.equals(OWL + "FunctionalProperty")
                    || object.equals(OWL + "InverseFunctionalProperty")
                    || object.equals(OWL + "SymmetricProperty")
                    || object.equals(OWL + "AsymmetricProperty")
                    || object.equals(OWL + "ReflexiveProperty")
                    || object.equals(OWL + "IrreflexiveProperty")
                    || object.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#Property")))
              properties.add(st.getSubject().stringValue());
            if (predicate.equals(SUBPROPERTY)
                || predicate.equals(OWL + "inverseOf")
                || predicate.equals(OWL + "equivalentProperty")) {
              if (st.getSubject() instanceof URI) properties.add(st.getSubject().stringValue());
              if (st.getObject() instanceof URI) properties.add(object);
            }
            if (predicate.equals(OWL + "onProperty") && st.getObject() instanceof URI) {
              properties.add(object);
              // One restriction witness per property; its filler distinguishes data and object use.
              String key = predicate + object;
              if (!witnesses.containsKey(key)) {
                witnesses.put(key, st);
                restrictions.add(st.getSubject());
              }
            }
          }
        });
    Path reduced =
        Files.createTempFile(
            "cedar-property-subgraph-", format.equals(RDFFormat.RDFXML) ? ".rdf" : ".nt");
    try {
      try (OutputStream out = Files.newOutputStream(reduced)) {
        RDFWriter writer =
            Rio.createWriter(
                format.equals(RDFFormat.RDFXML) ? RDFFormat.RDFXML : RDFFormat.NTRIPLES, out);
        writer.startRDF();
        var values = new org.openrdf.model.impl.ValueFactoryImpl();
        var context = values.createURI("urn:cedar:property-extraction-context");
        writer.handleStatement(
            values.createStatement(
                context, values.createURI(RDF_TYPE), values.createURI(OWL + "Class")));
        for (Statement st : witnesses.values()) {
          // Blank-node annotation axioms need their entire reification to be reparsed. Their
          // predicates can instead be witnessed as annotations on a synthetic class.
          writer.handleStatement(
              st.getSubject() instanceof URI || vocabulary(st.getPredicate().stringValue())
                  ? st
                  : values.createStatement(context, st.getPredicate(), st.getObject()));
        }
        for (var restriction : restrictions)
          writer.handleStatement(
              values.createStatement(
                  context,
                  values.createURI("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
                  restriction));
        stream(
            file,
            format,
            new RDFHandlerBase() {
              @Override
              public void handleStatement(Statement st) throws RDFHandlerException {
                String predicate = st.getPredicate().stringValue();
                boolean property =
                    st.getSubject() instanceof URI
                        && properties.contains(st.getSubject().stringValue())
                        && (st.getObject() instanceof Literal
                            || (st.getObject() instanceof URI
                                && (predicate.equals(RDF_TYPE)
                                    || predicate.equals(SUBPROPERTY)
                                    || predicate.equals(OWL + "inverseOf")
                                    || predicate.equals(OWL + "equivalentProperty"))));
                boolean witnessType =
                    st.getSubject() instanceof URI
                        && witnessSubjects.contains(st.getSubject())
                        && predicate.equals(RDF_TYPE)
                        && !st.getObject().stringValue().equals(OWL + "Axiom");
                boolean restriction =
                    restrictions.contains(st.getSubject())
                        && (st.getObject() instanceof URI || st.getObject() instanceof Literal);
                if (property || witnessType || restriction) writer.handleStatement(st);
              }
            });
        writer.endRDF();
      }
      return extractOwl(reduced);
    } finally {
      Files.deleteIfExists(reduced);
    }
  }

  List<SnapshotProperties.Property> extractOwl(Path file) throws Exception {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    // Mapping every import to an empty local document prevents even attempted network retrieval.
    Path empty = Files.createTempFile("cedar-property-import-", ".owl");
    try {
      Files.writeString(
          empty,
          "<?xml version=\"1.0\"?><rdf:RDF"
              + " xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"/>");
      manager.getIRIMappers().add(iri -> IRI.create(empty.toUri()));
      OWLOntology ontology =
          manager.loadOntologyFromOntologyDocument(
              new FileDocumentSource(file.toFile()),
              new OWLOntologyLoaderConfiguration()
                  // Preserve the asserted property kinds; repairing punning can sort enormous
                  // class signatures even though this extractor only needs properties.
                  .setRepairIllegalPunnings(false)
                  .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT));
      return extract(ontology);
    } finally {
      Files.deleteIfExists(empty);
    }
  }

  public List<SnapshotProperties.Property> extract(OWLOntology ontology) {
    List<SnapshotProperties.Property> out = new ArrayList<>();
    for (OWLEntity entity : ontology.getSignature()) {
      String kind =
          entity.isOWLObjectProperty()
              ? "object"
              : entity.isOWLDataProperty()
                  ? "datatype"
                  : entity.isOWLAnnotationProperty() ? "annotation" : null;
      if (kind == null || entity.isBuiltIn()) continue;
      List<SnapshotProperties.Literal> literals = new ArrayList<>();
      boolean obsolete = false;
      for (OWLAnnotationAssertionAxiom ax :
          ontology.getAnnotationAssertionAxioms(entity.getIRI())) {
        if (ax.getValue() instanceof OWLLiteral literal) {
          String predicate = ax.getProperty().getIRI().toString();
          literals.add(
              new SnapshotProperties.Literal(predicate, literal.getLang(), literal.getLiteral()));
          if (predicate.equals("http://www.w3.org/2002/07/owl#deprecated")
              && (literal.getLiteral().equals("true") || literal.getLiteral().equals("1")))
            obsolete = true;
        }
      }
      Comparator<SnapshotProperties.Literal> labelOrder =
          Comparator.comparingInt(
                  (SnapshotProperties.Literal l) ->
                      l.lang().equalsIgnoreCase("en") ? 0 : l.lang().isEmpty() ? 1 : 2)
              .thenComparingInt(l -> l.predicate().endsWith("#prefLabel") ? 0 : 1)
              .thenComparing(SnapshotProperties.Literal::value);
      String iri = entity.getIRI().toString();
      String label =
          literals.stream()
              .filter(
                  l ->
                      l.predicate().equals("http://www.w3.org/2000/01/rdf-schema#label")
                          || l.predicate().equals("http://www.w3.org/2004/02/skos/core#prefLabel"))
              .filter(l -> !l.value().isBlank())
              .sorted(labelOrder)
              .map(SnapshotProperties.Literal::value)
              .findFirst()
              .orElseGet(
                  () -> iri.substring(Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/')) + 1));
      Set<String> parents = new TreeSet<>();
      if (entity.isOWLObjectProperty())
        for (OWLSubObjectPropertyOfAxiom ax :
            ontology.getObjectSubPropertyAxiomsForSubProperty(entity.asOWLObjectProperty())) {
          if (!ax.getSuperProperty().isAnonymous())
            parents.add(ax.getSuperProperty().asOWLObjectProperty().getIRI().toString());
        }
      if (entity.isOWLDataProperty())
        for (OWLSubDataPropertyOfAxiom ax :
            ontology.getDataSubPropertyAxiomsForSubProperty(entity.asOWLDataProperty())) {
          parents.add(ax.getSuperProperty().asOWLDataProperty().getIRI().toString());
        }
      if (entity.isOWLAnnotationProperty())
        for (OWLSubAnnotationPropertyOfAxiom ax :
            ontology.getSubAnnotationPropertyOfAxioms(entity.asOWLAnnotationProperty())) {
          parents.add(ax.getSuperProperty().getIRI().toString());
        }
      out.add(
          new SnapshotProperties.Property(
              iri,
              kind,
              label,
              obsolete,
              literals.stream().distinct().toList(),
              List.copyOf(parents)));
    }
    out.sort(
        Comparator.comparing(SnapshotProperties.Property::iri)
            .thenComparing(SnapshotProperties.Property::kind));
    return out;
  }
}
