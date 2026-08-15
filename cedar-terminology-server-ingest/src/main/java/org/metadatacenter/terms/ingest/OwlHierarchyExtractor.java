package org.metadatacenter.terms.ingest;

import org.metadatacenter.terms.store.SnapshotStore;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extracts a subsumption hierarchy from an OWL (or OBO-in-OWL) ontology into a {@link SnapshotStore}.
 *
 * A hierarchy edge is drawn to each <em>named</em> superclass, matching how BioPortal renders an
 * OWL tree. Two asserted forms contribute:
 * <ul>
 *   <li>a plain {@code rdfs:subClassOf} to a named class;</li>
 *   <li>the named <em>genus</em> of a definition — a named class appearing as a conjunct of an
 *       {@code owl:intersectionOf}, whether that intersection is the superclass of a
 *       {@code subClassOf} axiom or the definiens of an {@code owl:equivalentClass} axiom. This is
 *       the ubiquitous OBO genus-differentia pattern (e.g. {@code assay measuring X ≡ assay and
 *       (has_specified_input some X)}); the genus {@code assay} is the parent.</li>
 * </ul>
 * Restriction fillers (the {@code X} in {@code part_of some X}) are not edges, so compositional
 * relations do not pollute the {@code is_a} tree. {@code owl:Thing} and {@code owl:Nothing} are not
 * materialized, so classes with no named superclass become roots.
 *
 * Only asserted axioms are read; no reasoner is invoked (the genus is taken structurally from the
 * intersection, not inferred). Concept labels come from {@code rdfs:label}. See the
 * hierarchy-extractor design note.
 */
public class OwlHierarchyExtractor implements HierarchyExtractor {

  private static final Logger log = LoggerFactory.getLogger(OwlHierarchyExtractor.class);

  /** OBO "term replaced by": links an obsolete term to its successor. */
  private static final IRI TERM_REPLACED_BY = IRI.create("http://purl.obolibrary.org/obo/IAO_0100001");

  /** obo2owl renders an OBO {@code relationship: is_a X} clause (a non-standard way some ontologies write
   *  subsumption, e.g. BSAO) as {@code is_a some X} on its TEMP# placeholder namespace rather than as
   *  {@code rdfs:subClassOf}. It is still subsumption, so this property is always a hierarchy edge. */
  private static final IRI OBO_RELATIONSHIP_IS_A = IRI.create("http://purl.obolibrary.org/obo/TEMP#is_a");

  /** SKOS preferred label: the label BioPortal serves for UMLS/TTL ontologies (e.g. ICD10CM, MESH,
   *  LOINC), which carry an OWL {@code rdfs:subClassOf} hierarchy but label concepts with
   *  {@code skos:prefLabel} rather than {@code rdfs:label}. */
  private static final IRI SKOS_PREF_LABEL = IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel");

  /**
   * Object properties whose {@code some Filler} restriction superclasses count as hierarchy edges,
   * in addition to {@code rdfs:subClassOf}. Empty by default (pure subsumption). A partonomy
   * ontology whose BioPortal browse tree is built from object properties — e.g. BTO, where the tree
   * is {@code part_of} + {@code develops_from}, not the near-empty {@code is_a} — supplies them here.
   */
  private final Set<IRI> hierarchyProperties;

  /** Default extractor: only {@code rdfs:subClassOf} and named genera form the hierarchy. */
  public OwlHierarchyExtractor() {
    this(Set.of());
  }

  /**
   * Extractor that also treats {@code SubClassOf(C, p some D)} as a hierarchy edge {@code C -> D}
   * when {@code p} is in {@code hierarchyProperties} (the named filler {@code D} becomes a parent).
   */
  public OwlHierarchyExtractor(Set<IRI> hierarchyProperties) {
    this.hierarchyProperties = hierarchyProperties;
  }

  /**
   * Extracts the hierarchy from an already-loaded ontology into the store and materializes the
   * closure. The store should be freshly initialized ({@link SnapshotStore#initSchema()}).
   */
  @Override
  public Result extract(OWLOntology ont, SnapshotStore store) throws SQLException {
    OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
    OWLAnnotationProperty deprecated = df.getOWLDeprecated();
    OWLAnnotationProperty replacedBy = df.getOWLAnnotationProperty(TERM_REPLACED_BY);

    int classCount = 0;
    List<SnapshotStore.LabelRow> labelRows = new ArrayList<>();
    for (OWLClass cls : ont.getClassesInSignature(Imports.INCLUDED)) {
      if (cls.isOWLThing() || cls.isOWLNothing()) {
        continue;
      }
      store.addConcept(cls.getIRI().toString(), label(cls, ont),
          isDeprecated(cls, ont, deprecated), replacedBy(cls, ont, replacedBy));
      captureLabels(cls, ont, labelRows);
      classCount++;
    }
    // Preserve every language variant of every name (labels + synonyms) alongside the single served
    // pref_label. Additive: pref_label (and thus content identity) is unchanged; this only records what
    // the single-label pick discards.
    store.addLabels(labelRows);

    int edgeCount = 0;
    // Asserted rdfs:subClassOf. A named superclass is a parent; a named genus inside an
    // intersection superclass is too. Read across the import closure: BioPortal resolves an
    // ontology's owl:imports and serves the merged graph, so a field constrained to an imported
    // branch (e.g. BAO's CHEBI/UO slims) browses the imported terms. Imports that don't resolve are
    // silently skipped at load (MissingImportHandlingStrategy.SILENT), so this stays best-effort.
    for (OWLSubClassOfAxiom ax : ont.getAxioms(AxiomType.SUBCLASS_OF, Imports.INCLUDED)) {
      OWLClassExpression sub = ax.getSubClass();
      if (sub.isAnonymous()) {
        continue;
      }
      OWLClass child = sub.asOWLClass();
      if (child.isOWLThing() || child.isOWLNothing()) {
        continue;
      }
      OWLClassExpression sup = ax.getSuperClass();
      if (!sup.isAnonymous() && sup.asOWLClass().isOWLThing()) {
        // subClassOf owl:Thing is not a hierarchy edge — owl:Thing is never materialized. The class
        // is left parentless, which is exactly what makes it a root (see SnapshotStore.materialize).
        continue;
      }
      edgeCount += addParents(store, child, namedParents(sup), "rdfs:subClassOf");
    }
    // Defined classes: the named genus of an equivalentClass intersection (genus-differentia).
    for (OWLEquivalentClassesAxiom ax : ont.getAxioms(AxiomType.EQUIVALENT_CLASSES, Imports.INCLUDED)) {
      List<OWLClassExpression> exprs = new ArrayList<>(ax.getClassExpressions());
      for (OWLClassExpression e : exprs) {
        if (e.isAnonymous()) {
          continue;
        }
        OWLClass child = e.asOWLClass();
        if (child.isOWLThing() || child.isOWLNothing()) {
          continue;
        }
        for (OWLClassExpression definiens : exprs) {
          // Only take the genus from an anonymous definition (an intersection); a named class on
          // the other side is an equivalence, not a subsumption, and is not an edge.
          if (definiens != e && definiens.isAnonymous()) {
            edgeCount += addParents(store, child, namedParents(definiens), "equivalentClass:genus");
          }
        }
      }
    }

    store.materialize();
    log.info("Extracted {} classes and {} hierarchy edges", classCount, edgeCount);
    return new Result(classCount, edgeCount);
  }

  /** Loads an ontology from a file and extracts it into the store. */
  @Override
  public Result extractFromFile(File file, SnapshotStore store)
      throws OWLOntologyCreationException, SQLException {
    OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    // Ignore owl:imports we can't resolve: we extract each ontology's OWN asserted
    // hierarchy, and many BioPortal ontologies import external IRIs that are
    // unreachable offline — otherwise OWLAPI throws UnloadableImportException.
    OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
        .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
    OWLOntology ont = manager.loadOntologyFromOntologyDocument(new FileDocumentSource(file), config);
    return extract(ont, store);
  }

  /**
   * The named superclasses contributed by an expression: the class itself if it is named, or the
   * named conjuncts (the genus) if it is an {@code owl:intersectionOf}. Anything else (a bare
   * restriction, a union) contributes nothing. {@code owl:Thing}/{@code owl:Nothing} are excluded.
   */
  private List<OWLClass> namedParents(OWLClassExpression expr) {
    List<OWLClass> parents = new ArrayList<>();
    collectParents(expr, parents);
    return parents;
  }

  private void collectParents(OWLClassExpression expr, List<OWLClass> parents) {
    if (!expr.isAnonymous()) {
      addNamed(parents, expr);
    } else if (expr instanceof OWLObjectIntersectionOf intersection) {
      // A named genus contributes; a configured relation restriction inside the intersection does too.
      for (OWLClassExpression operand : intersection.getOperands()) {
        collectParents(operand, parents);
      }
    } else if (expr instanceof OWLObjectSomeValuesFrom svf
        && !svf.getProperty().isAnonymous() && !svf.getFiller().isAnonymous()) {
      IRI prop = svf.getProperty().asOWLObjectProperty().getIRI();
      // An OBO `relationship: is_a X` clause (as some ontologies write their subsumption — BSAO) is
      // rendered by obo2owl as `is_a some X` on the TEMP# namespace, not rdfs:subClassOf. It is still
      // subsumption, so treat it as a hierarchy edge everywhere. Plus, for a configured partonomy, the
      // configured relations (e.g. BTO/EHDAA part_of some system) also make the filler a parent.
      if (OBO_RELATIONSHIP_IS_A.equals(prop)
          || (!hierarchyProperties.isEmpty() && hierarchyProperties.contains(prop))) {
        addNamed(parents, svf.getFiller());
      }
    }
  }

  private static void addNamed(List<OWLClass> parents, OWLClassExpression named) {
    OWLClass c = named.asOWLClass();
    if (!c.isOWLThing() && !c.isOWLNothing()) {
      parents.add(c);
    }
  }

  private static int addParents(SnapshotStore store, OWLClass child, List<OWLClass> parents, String sourcePred)
      throws SQLException {
    int added = 0;
    for (OWLClass parent : parents) {
      if (!parent.equals(child)) {
        store.addEdge(child.getIRI().toString(), parent.getIRI().toString(), sourcePred);
        added++;
      }
    }
    return added;
  }

  /**
   * The class's preferred label, taken from its own {@code rdfs:label} assertion, falling back to
   * {@code skos:prefLabel} (which is where UMLS/TTL ontologies such as ICD10CM, MESH, and LOINC put
   * the label BioPortal serves — they carry an OWL hierarchy but SKOS labels).
   *
   * Read from the class's direct annotation-assertion axioms rather than via
   * {@code EntitySearcher.getAnnotations}, which also surfaces labels attached as <em>axiom
   * annotations</em> on other assertions — notably an OBO {@code xref} description (obo2owl exposes
   * {@code xref: X "desc"} as a second {@code rdfs:label}), which must not be mistaken for the term's
   * name and would otherwise diverge from BioPortal.
   */
  private static String label(OWLClass cls, OWLOntology ont) {
    // A class can carry the label in several languages; BioPortal serves the English one. Pick the
    // best-ranked literal (English, then untagged, then any other) rather than the first encountered,
    // so a multilingual ontology (NANDO's Japanese, ONTOAD's French, ...) does not diverge from
    // BioPortal on which language it names the term in. rdfs:label wins over skos:prefLabel overall.
    //
    // A blank literal is not a label. ABD asserts rdfs:label "" alongside the real one on 61 of its
    // classes, and taking the blank left the class unlabeled — which then drew the IRI-fragment
    // fallback, so "White pine blister rust" was served as "?id=118".
    String rdfsLabel = null;
    int rdfsRank = -1;
    String prefLabel = null;
    int prefRank = -1;
    // Across the import closure: an imported class carries its label in the imported ontology.
    for (OWLOntology o : ont.getImportsClosure()) {
      for (OWLAnnotationAssertionAxiom ax : o.getAnnotationAssertionAxioms(cls.getIRI())) {
        if (!(ax.getValue() instanceof OWLLiteral literal) || literal.getLiteral().isBlank()) {
          continue;
        }
        int rank = langRank(literal);
        if (ax.getProperty().isLabel()) {
          if (rank > rdfsRank) {
            rdfsRank = rank;
            rdfsLabel = literal.getLiteral();
          }
        } else if (ax.getProperty().getIRI().equals(SKOS_PREF_LABEL) && rank > prefRank) {
          prefRank = rank;
          prefLabel = literal.getLiteral();
        }
      }
    }
    return rdfsLabel != null ? rdfsLabel : prefLabel;
  }

  /**
   * Records every name literal of a class — labels and synonyms ({@link LabelProperties}) — with its
   * language tag into {@code out}, for the snapshot's {@code label} table. Reads the class's own
   * annotation-assertion axioms across the import closure, the same clean source {@link #label} uses
   * (an OBO {@code xref} description exposed as an axiom-annotation label is not surfaced here). This
   * preserves the multilingual names the single {@code pref_label} pick drops; it does not affect
   * which literal that pick chooses.
   */
  private static void captureLabels(OWLClass cls, OWLOntology ont, List<SnapshotStore.LabelRow> out) {
    String iri = cls.getIRI().toString();
    for (OWLOntology o : ont.getImportsClosure()) {
      for (OWLAnnotationAssertionAxiom ax : o.getAnnotationAssertionAxioms(cls.getIRI())) {
        if (!(ax.getValue() instanceof OWLLiteral literal)) {
          continue;
        }
        String curie = LabelProperties.curieFor(ax.getProperty().getIRI());
        if (curie != null) {
          out.add(new SnapshotStore.LabelRow(iri, curie, literal.getLang(), literal.getLiteral()));
        }
      }
    }
  }

  /**
   * Language preference for choosing among a class's labels: English best, then an untagged literal,
   * then any other language. Matches BioPortal, which serves the English label when several exist.
   */
  private static int langRank(OWLLiteral literal) {
    String lang = literal.getLang();
    if (lang == null || lang.isEmpty()) {
      return 1; // untagged
    }
    String lower = lang.toLowerCase();
    return lower.equals("en") || lower.startsWith("en-") ? 2 : 0;
  }

  private static boolean isDeprecated(OWLClass cls, OWLOntology ont, OWLAnnotationProperty deprecated) {
    for (OWLOntology o : ont.getImportsClosure()) {
      for (OWLAnnotationAssertionAxiom ax : o.getAnnotationAssertionAxioms(cls.getIRI())) {
        if (ax.getProperty().equals(deprecated) && ax.getValue() instanceof OWLLiteral literal
            && literal.isBoolean() && literal.parseBoolean()) {
          return true;
        }
      }
    }
    return false;
  }

  private static String replacedBy(OWLClass cls, OWLOntology ont, OWLAnnotationProperty replacedBy) {
    for (OWLOntology o : ont.getImportsClosure()) {
      for (OWLAnnotationAssertionAxiom ax : o.getAnnotationAssertionAxioms(cls.getIRI())) {
        if (!ax.getProperty().equals(replacedBy)) {
          continue;
        }
        OWLAnnotationValue value = ax.getValue();
        if (value instanceof IRI iri) {
          return iri.toString();
        }
        if (value instanceof OWLLiteral literal) {
          return literal.getLiteral();
        }
      }
    }
    return null;
  }
}
