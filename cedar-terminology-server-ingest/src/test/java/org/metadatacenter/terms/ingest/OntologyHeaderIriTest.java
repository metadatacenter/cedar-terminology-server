package org.metadatacenter.terms.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OntologyHeaderIriTest {

  @TempDir
  Path dir;

  private Path write(String name, String content) throws Exception {
    Path p = dir.resolve(name);
    Files.writeString(p, content);
    return p;
  }

  @Test
  public void rdfXmlAboutAttribute() throws Exception {
    Path f = write("bao.owl", """
        <?xml version="1.0"?>
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                 xmlns:owl="http://www.w3.org/2002/07/owl#">
          <owl:Ontology rdf:about="http://www.bioassayontology.org/bao/bao_complete.owl">
            <owl:versionIRI rdf:resource="http://www.bioassayontology.org/bao/2.4/bao_complete.owl"/>
          </owl:Ontology>
          <owl:Class rdf:about="http://www.bioassayontology.org/bao#BAO_0000015"/>
        </rdf:RDF>
        """);
    assertEquals("http://www.bioassayontology.org/bao/bao_complete.owl",
        OntologyHeaderIri.fromFile(f).orElseThrow());
  }

  @Test
  public void rdfXmlEmptyAboutResolvesToXmlBase() throws Exception {
    // Protégé/OWL export style: <owl:Ontology rdf:about=""/> names itself by the document xml:base.
    Path f = write("bho.owl", """
        <?xml version="1.0"?>
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                 xmlns:owl="http://www.w3.org/2002/07/owl#"
                 xml:base="http://www.semanticweb.org/ontologies/2010/10/BPO.owl">
          <owl:Ontology rdf:about=""/>
          <owl:Class rdf:about="#subject"/>
        </rdf:RDF>
        """);
    assertEquals("http://www.semanticweb.org/ontologies/2010/10/BPO.owl",
        OntologyHeaderIri.fromFile(f).orElseThrow());
  }

  @Test
  public void owlXmlOntologyIriAttribute() throws Exception {
    Path f = write("ont.owx", """
        <?xml version="1.0"?>
        <Ontology xmlns="http://www.w3.org/2002/07/owl#"
                  ontologyIRI="http://example.org/myont"
                  versionIRI="http://example.org/myont/1.0">
        </Ontology>
        """);
    assertEquals("http://example.org/myont", OntologyHeaderIri.fromFile(f).orElseThrow());
  }

  @Test
  public void turtleSubjectTypedOntology() throws Exception {
    Path f = write("ont.ttl", """
        @prefix owl: <http://www.w3.org/2002/07/owl#> .
        @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
        <http://purl.org/example/thing> a owl:Ontology ;
            owl:versionInfo "1.2" .
        """);
    assertEquals("http://purl.org/example/thing", OntologyHeaderIri.fromFile(f).orElseThrow());
  }

  @Test
  public void turtleEmptySubjectWithBase() throws Exception {
    Path f = write("base.ttl", """
        @base <http://example.org/base-ont> .
        @prefix owl: <http://www.w3.org/2002/07/owl#> .
        <> a owl:Ontology .
        """);
    assertEquals("http://example.org/base-ont", OntologyHeaderIri.fromFile(f).orElseThrow());
  }

  @Test
  public void ntriplesFullIris() throws Exception {
    Path f = write("ont.nt", """
        <http://example.org/nt-ont> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/2002/07/owl#Ontology> .
        """);
    assertEquals("http://example.org/nt-ont", OntologyHeaderIri.fromFile(f).orElseThrow());
  }

  @Test
  public void doctypeEntityInAboutIsExpanded() throws Exception {
    Path f = write("idobru.owl", """
        <?xml version="1.0"?>
        <!DOCTYPE rdf:RDF [ <!ENTITY obo "http://purl.obolibrary.org/obo/" > ]>
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                 xmlns:owl="http://www.w3.org/2002/07/owl#">
          <owl:Ontology rdf:about="&obo;ido/brucellosis.owl"/>
        </rdf:RDF>
        """);
    assertEquals("http://purl.obolibrary.org/obo/ido/brucellosis.owl",
        OntologyHeaderIri.fromFile(f).orElseThrow());
  }

  @Test
  public void undefinedEntityIsRejected() throws Exception {
    Path f = write("bad.owl", """
        <?xml version="1.0"?>
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                 xmlns:owl="http://www.w3.org/2002/07/owl#">
          <owl:Ontology rdf:about="&undefined;Thing.owl"/>
        </rdf:RDF>
        """);
    assertTrue(OntologyHeaderIri.fromFile(f).isEmpty());
  }

  @Test
  public void placeholderHeaderYieldsEmpty() throws Exception {
    Path f = write("unnamed.owl", """
        <owl:Ontology xmlns:owl="http://www.w3.org/2002/07/owl#"
                      xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                      rdf:about="http://www.owl-ontologies.com/unnamed.owl"/>
        """);
    assertTrue(OntologyHeaderIri.fromFile(f).isEmpty());
  }

  @Test
  public void anonymousOrMissingHeaderYieldsEmpty() throws Exception {
    Path noHeader = write("plain.owl", """
        <?xml version="1.0"?>
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                 xmlns:owl="http://www.w3.org/2002/07/owl#">
          <owl:Class rdf:about="http://example.org/C1"/>
        </rdf:RDF>
        """);
    assertTrue(OntologyHeaderIri.fromFile(noHeader).isEmpty());

    Path blankAbout = write("blank.owl", """
        <owl:Ontology xmlns:owl="http://www.w3.org/2002/07/owl#"
                      xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" rdf:about=""/>
        """);
    assertTrue(OntologyHeaderIri.fromFile(blankAbout).isEmpty());
  }
}
