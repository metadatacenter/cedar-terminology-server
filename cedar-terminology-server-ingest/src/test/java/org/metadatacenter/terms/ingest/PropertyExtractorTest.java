package org.metadatacenter.terms.ingest;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;

class PropertyExtractorTest {
  @TempDir Path dir;

  @Test
  void extractsEachKindAndMultipleParentsWithoutMixingInClasses() throws Exception {
    var m = OWLManager.createOWLOntologyManager();
    var o = m.createOntology();
    var df = m.getOWLDataFactory();
    var p = df.getOWLObjectProperty(IRI.create("urn:p"));
    var a = df.getOWLObjectProperty(IRI.create("urn:a"));
    var b = df.getOWLObjectProperty(IRI.create("urn:b"));
    m.addAxiom(o, df.getOWLSubObjectPropertyOfAxiom(p, a));
    m.addAxiom(o, df.getOWLSubObjectPropertyOfAxiom(p, b));
    m.addAxiom(o, df.getOWLDeclarationAxiom(df.getOWLDataProperty(IRI.create("urn:data"))));
    m.addAxiom(
        o, df.getOWLDeclarationAxiom(df.getOWLAnnotationProperty(IRI.create("urn:annotation"))));
    m.addAxiom(o, df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create("urn:class"))));
    m.addAxiom(
        o,
        df.getOWLAnnotationAssertionAxiom(
            df.getRDFSLabel(), p.getIRI(), df.getOWLLiteral("Property", "en")));
    m.addAxiom(
        o,
        df.getOWLAnnotationAssertionAxiom(
            df.getRDFSLabel(), p.getIRI(), df.getOWLLiteral("Propriété", "fr")));
    var result = new PropertyExtractor().extract(o);
    assertEquals(
        Set.of("object", "datatype", "annotation"),
        new HashSet<>(result.stream().map(x -> x.kind()).toList()));
    assertFalse(result.stream().anyMatch(x -> x.iri().equals("urn:class")));
    var property = result.stream().filter(x -> x.iri().equals("urn:p")).findFirst().orElseThrow();
    assertEquals(List.of("urn:a", "urn:b"), property.parents());
    assertEquals("Property", property.label());
    assertEquals(2, property.literals().size());
  }

  @Test
  void importsAreNotFetched() throws Exception {
    Path f = dir.resolve("source.owl");
    Files.writeString(
        f,
        """
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:owl="http://www.w3.org/2002/07/owl#">
          <owl:Ontology rdf:about="urn:source"><owl:imports rdf:resource="http://127.0.0.1:1/must-not-fetch"/></owl:Ontology>
          <owl:ObjectProperty rdf:about="urn:property"/>
        </rdf:RDF>
        """);
    var result = new PropertyExtractor().extract(f);
    assertTrue(result.stream().anyMatch(x -> x.iri().equals("urn:property")));
  }

  @Test
  void streamingRetainsTheFullParsersPropertyModelAcrossRdfFormats() throws Exception {
    Path turtle = dir.resolve("properties.ttl");
    Files.writeString(
        turtle,
        """
      @prefix : <urn:test:> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      :child a owl:ObjectProperty; rdfs:subPropertyOf :left, :right;
        rdfs:label "Child"@en, "Enfant"@fr; :note "A property annotation" .
      :left a owl:ObjectProperty . :right a owl:ObjectProperty .
      :data a owl:DatatypeProperty; rdfs:label "Data" .
      :annotation a owl:AnnotationProperty; rdfs:subPropertyOf :parentAnnotation .
      :parentAnnotation a owl:AnnotationProperty .
      :class a owl:Class; :undeclared "A class annotation"; rdfs:resource "Nonstandard property in a reserved namespace";
        rdfs:subClassOf [ a owl:Restriction; owl:onProperty :implicit; owl:someValuesFrom :class ] .
      """);
    PropertyExtractor extractor = new PropertyExtractor();
    assertEquals(
        propertyHash(extractor.extractOwl(turtle)), propertyHash(extractor.extract(turtle)));
    var manager = OWLManager.createOWLOntologyManager();
    var ontology = manager.loadOntologyFromOntologyDocument(turtle.toFile());
    Path xml = dir.resolve("properties.rdf");
    manager.saveOntology(
        ontology,
        new org.semanticweb.owlapi.formats.RDFXMLDocumentFormat(),
        IRI.create(xml.toUri()));
    assertEquals(propertyHash(extractor.extractOwl(xml)), propertyHash(extractor.extract(xml)));
  }

  private String propertyHash(
      List<org.metadatacenter.terms.store.SnapshotProperties.Property> properties)
      throws Exception {
    try (var store = org.metadatacenter.terms.store.SnapshotStore.openInMemory()) {
      store.initSchema();
      store.properties().write(properties);
      return store.normalizedContentHash(true);
    }
  }
}
