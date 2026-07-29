# Terminology Server Roadmap

The goal is to replace the BioPortal proxy with a local, version-pinned read replica — one ontology
at a time, never regressing quality. Content is served from content-addressed SQLite snapshots the
server owns, so lookups are offline, fast, and reproducible; BioPortal remains the fallback for
anything not yet migrated. Each ontology graduates to local service only after its real, in-use
terminology queries are proven equivalent to BioPortal's.

## How the Migration Works

Two mechanisms already in place make the cutover incremental and reversible:

- **The allowlist is the switch.** `RoutingTerminologyService` routes per ontology: an acronym on
  `terminologyStore.localOntologies` (and present in the catalog) is served from the local SQLite
  store; everything else goes to BioPortal. Ingesting an ontology populates the catalog but does
  *not* serve it locally until it is allowlisted, so ingestion and cutover are decoupled. Flipping
  one acronym on or off is the unit of migration.
- **`localOnly` makes gaps loud.** In strict mode a locally-served ontology never silently falls
  back to BioPortal: a missing operation throws instead of being masked. The equivalence harness
  runs under this mode so a coverage gap surfaces as a failure, not a quiet BioPortal answer.

The quality gate driving the switch is a differential test: replay the *actual* production usage of
each ontology against both backends and compare. An ontology is promoted to the allowlist only when
it passes.

## Status

Done:
- Ingest of 258 public ontologies into content-addressed SQLite snapshots (version_id = content
  hash of the raw download); licensed content refused by policy.
- Relocatable store: snapshot paths are recorded relative to the catalog, so catalog + snapshots
  copy to any host with no path rewriting.
- `RoutingTerminologyService` with the per-ontology allowlist and `localOnly` strict mode.
- The usage corpus: `cedar_ontology_usage.py --emit-constraints` harvests every controlled-term
  field's constraints from production; `cedar_usage_matrix.py` reduces it to the atomic-target
  matrix — one row per distinct `(kind, acronym, target)` lookup (2,885 atoms over 320 ontologies).
- The differential harness: `cedar_termdiff.py` records BioPortal goldens and verifies a local
  instance against them, emitting a per-ontology readiness report.

## Milestones

1. **Equivalence gate on `integrated-search` (in progress).** Record BioPortal goldens for the
   matrix atoms, then verify a local-store instance under `localOnly`. Equivalence bar for the
   enumerate path (`inputText=""`): set-equality on result IRIs plus preferred-label agreement;
   ordering and BioPortal-only metadata (synonyms, definitions, provenance) are out of scope, since
   the snapshot holds hierarchy plus preferred labels. First proof on DOID, GO, HP.
2. **Cut over the clean ontologies.** Add each ontology that passes to `localOntologies`; keep the
   readiness report in CI as the standing quality signal. Re-record goldens on a cadence, since
   BioPortal content drifts and ours is pinned.
3. **Add the ranked-search bar.** Extend the harness to prefix seeds (`inputText` = `a`, `e`, …) and
   score search by result-set recall rather than exact order — the browse and prefix paths diverge
   between implementations.
4. **Close fidelity gaps as they surface.** Where the report shows systematic mismatches (a missing
   label, an obsoletion, a format-specific hierarchy quirk), fix the extractor or the snapshot
   schema and re-ingest.
5. **Cross-backend fan-out (deferred).** Today a single `integrated-search` whose constraint names
   any non-local source goes wholly to BioPortal (conservative, correct, no partial answers). A
   constraint mixing a local branch and a BioPortal branch therefore gets no local benefit yet.
   Serving it partially locally means splitting the constraint by backend, querying each, and
   merging — hard, because the two backends rank and page differently. Deferred until single-source
   cutover is solid; it gets its own test tier.
6. **Version-pinning workflow.** Surface the vocabulary diff between a pinned snapshot and a newer
   one (`SnapshotDiff` already computes it) and let a template re-pin deliberately.
7. **Retention policy.** BioPortal keeps thousands of historical (often daily) submissions; the
   measured pace of change is ~0.03%/day and almost purely additive. Decide a retention policy
   (latest + referenced + coarse cadence) and content-dedup near-identical rebuilds before any
   full-history backfill, so storage stays in tens of GB rather than ~1 TB.

## Open Decisions

- **Equivalence bars per operation** — the enumerate bar (set-equality + labels) is fixed; the
  search-recall tolerance is not yet chosen.
- **Endpoint scope** — the gate covers `POST /bioportal/integrated-search` (what the matrix models)
  and, since the first cutover exposed it, `GET .../classes/roots` (the picker's tree entry point;
  `cedar_termdiff.py --roots`). A root is now a non-obsolete class with no named parent, matching
  BioPortal for hierarchical ontologies (verified against the roots goldens: DOID 15, picker
  navigable). Residual roots divergence is overcount, not undercount: referenced-only orphans
  (CHEBI) and flat/value-set vocabularies where BioPortal returns none (GAZ, RXNORM) still list too
  many locally — cosmetic for the picker, and those flat vocabularies are search-only, not
  tree-browse cutover targets. Gating `children`/`subtree` is still open.

  Roots divergence no longer blocks cutover, because routing is now **per-endpoint**: an ontology on
  `localOntologies` is served locally for search/integrated-search and point lookups, but browses its
  tree (root classes, class tree) from BioPortal unless it is *also* on `localRootsOntologies` (the
  subset whose roots are proven equivalent). So cutover eligibility for the high-value search path is
  integrated-search equivalence alone; an ontology graduates to local roots later, when its roots
  match. This is what lets the search cutover be far wider than the roots-clean set.
- **Golden refresh cadence** — how often to re-record BioPortal baselines against ontology drift.

## Where the Pieces Live

The server and this roadmap are here. The migration tooling — usage harvest, matrix, and the
differential harness — lives in `cedar-development/ops`, run against a live stack; see that repo's
`RUNBOOK.md` for how to run each step.
