package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OntologyHeaderTitleTest {

  @TempDir
  Path dir;

  private Optional<String> title(String name, String content) throws IOException {
    Path f = dir.resolve(name);
    Files.writeString(f, content);
    return OntologyHeaderTitle.fromFile(f);
  }

  @Test
  void rdfXmlDcTitleOnTheOntologyNode() throws IOException {
    String x = """
        <rdf:RDF xmlns:owl="http://www.w3.org/2002/07/owl#" xmlns:dc="http://purl.org/dc/elements/1.1/">
          <owl:Ontology rdf:about="http://ex.org/o">
            <dc:title>Gene Ontology</dc:title>
          </owl:Ontology>
        </rdf:RDF>""";
    assertEquals(Optional.of("Gene Ontology"), title("go.owl", x));
  }

  @Test
  void turtleDctermsTitleOnTheOntologyNode() throws IOException {
    String ttl = """
        @prefix owl: <http://www.w3.org/2002/07/owl#> .
        @prefix dcterms: <http://purl.org/dc/terms/> .
        <https://w3id.org/emi> rdf:type owl:Ontology ;
            dcterms:title "The Earth Metabolome Initiative ontology" .""";
    assertEquals(Optional.of("The Earth Metabolome Initiative ontology"), title("emi.ttl", ttl));
  }

  @Test
  void rdfsLabelIsTheFallbackWhenNoTitle() throws IOException {
    String x = """
        <rdf:RDF xmlns:owl="http://www.w3.org/2002/07/owl#" xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#">
          <owl:Ontology rdf:about="http://ex.org/o">
            <rdfs:label>REPRODUCE-ME Ontology</rdfs:label>
          </owl:Ontology>
        </rdf:RDF>""";
    assertEquals(Optional.of("REPRODUCE-ME Ontology"), title("r.owl", x));
  }

  @Test
  void selfClosingOntologyHasNoTitle() throws IOException {
    String x = """
        <rdf:RDF xmlns:owl="http://www.w3.org/2002/07/owl#">
          <owl:Ontology rdf:about="http://ex.org/o"/>
          <owl:Class rdf:about="http://ex.org/o#Structure">
            <rdfs:label>Structure</rdfs:label>
          </owl:Class>
        </rdf:RDF>""";
    assertTrue(title("test1.owl", x).isEmpty(), "a class label must not be taken as the ontology title");
  }

  @Test
  void turtleTitleOnANonOntologySubjectIsIgnored() throws IOException {
    // dct:title sits on a ConceptScheme, not the ontology — must not be picked up.
    String ttl = """
        @prefix owl: <http://www.w3.org/2002/07/owl#> .
        @prefix dct: <http://purl.org/dc/terms/> .
        @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
        <http://ex.org/scheme> a skos:ConceptScheme ;
            dct:title "A description that is not the ontology title" .""";
    assertTrue(title("v.ttl", ttl).isEmpty());
  }

  @Test
  void prefersEnglishAmongMultilingualTitles() throws IOException {
    String x = """
        <rdf:RDF xmlns:owl="http://www.w3.org/2002/07/owl#" xmlns:dct="http://purl.org/dc/terms/">
          <owl:Ontology rdf:about="http://ex.org/o">
            <dct:title xml:lang="cs">Slovnik pro datove katalogy</dct:title>
            <dct:title xml:lang="en">The Data Catalog Vocabulary</dct:title>
          </owl:Ontology>
        </rdf:RDF>""";
    assertEquals(Optional.of("The Data Catalog Vocabulary"), title("dcat.rdf", x));
  }
}
