# cedar-terminology-server

[![CI](https://github.com/metadatacenter/cedar-terminology-server/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/metadatacenter/cedar-terminology-server/actions/workflows/ci.yml)

CEDAR's terminology service. It exposes the BioPortal-compatible endpoints used by CEDAR clients and
the version-aware search and hierarchy API backed by the local terminology store.

The reactor has five modules:

- `cedar-terminology-server-common` — shared terminology records and utilities
- `cedar-terminology-server-store` — versioned SQLite catalog and search index
- `cedar-terminology-server-core` — BioPortal and local-store service logic
- `cedar-terminology-server-ingest` — catalog ingestion, validation, and maintenance tools
- `cedar-terminology-server-application` — the deployable Dropwizard service

## Development

CEDAR backend development uses Java 17. From a configured CEDAR workspace:

```bash
export CEDAR_HOME="$HOME/CEDAR"
source "$CEDAR_HOME/cedar-profile-native-develop.sh"
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./mvnw test
```

Use `cedar-development/ops/cedar-services.sh` to run the service with the rest of the native stack.
The service configuration is
`cedar-terminology-server-application/src/main/resources/config.yml`; the committed OpenAPI files are
under that module's `src/main/resources/assets/swagger-api/` directory.

The canonical setup, build, test, and runtime instructions are in the
[CEDAR backend runbook](https://github.com/metadatacenter/cedar-development/blob/develop/ops/BACKEND-RUNBOOK.md).
Local terminology-store ingestion and version-aware serving are documented in the
[versioning runbook](https://github.com/metadatacenter/cedar-development/blob/develop/ops/VERSIONING-RUNBOOK.md).
