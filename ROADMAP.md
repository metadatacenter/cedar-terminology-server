# Terminology Server Roadmap

This roadmap has moved. The terminology versioning workstream — the local content-addressed store,
the BioPortal cutover, the search API, the term picker, the ingestion tracker and the open decisions
— is tracked in one place:

**[VERSIONING-ROADMAP.md](https://github.com/metadatacenter/cedar-development/blob/develop/ops/VERSIONING-ROADMAP.md)**
in `cedar-development`, alongside
[VERSIONING-RUNBOOK.md](https://github.com/metadatacenter/cedar-development/blob/develop/ops/VERSIONING-RUNBOOK.md),
which says how to run the store, ingest into it, and serve it.

Keeping a second copy here meant keeping it current here, and it was not: it described the store as
holding 258 ontologies where the tracker records 63 across 10 source systems, and it predated the
per-endpoint routing that decides what an ontology is served locally for. Numbers and decisions
belong wherever the work is recorded, and that is the document above.
