# RuleBridge

## Hackathon summary

### Problem investigated

RuleBridge investigates the feasibility of migrating a small, representative
portion of a legacy rules-engine service from Java 11 and Drools 7 to Java 21
and Drools 10 while preserving its observed behavior.

The proprietary legacy baseline used during characterization is not included in
this public repository. The repository contains the migrated implementation,
independently created rules, synthetic data, tests, and reproducible development
infrastructure.

### What Codex migrated

Codex analyzed the reduced legacy baseline, created characterization tests, and
developed an incremental migration path from Java 11, Spring Boot 2.7.10,
Drools/KIE 7.48.0.Final, Maven 3.9.16, and Oracle XE 21c to Java 21,
Spring Boot 3.5.10, Drools 10.2.0, Maven 3.9.16, and Oracle XE 21c through
Testcontainers.

The migration was then implemented in 19 human-reviewed steps. Codex also
identified and corrected one defect during its analysis previous revision and approval from the human developer. Codex also corrected another known behavioral defect in the migrated application.

Codex implemented the migrated service, database schema, and a 104-test unit and integration suite, and documented most of the resulting code and operational workflow. Codex wrote the detailed technical runbook and endpoint documentation in this README based on priorities and constraints provided by the human developer.

RuleBridge successfully migrated the selected legacy behavior to KIE Rule Units. The completed prototype then demonstrated that adopting Rule Units in the same production step as the Java and Drools upgrade would create too many simultaneous changes and exposed enough complexity and operational risk to support a different production recommendation.. The recommended production path is therefore incremental: first migrate to Java 21 and Drools 10 using conventional KIE sessions, stabilize that platform, and evaluate Rule Units in a later phase.


### ChatGPT participation

Before the hackathon, ChatGPT helped define a realistic experimental scope and
assisted with SQL scripts for generating synthetic data.

During the hackathon, ChatGPT acted as a discussion partner for architecture,
testing strategy, submission planning, and review of Codex's proposed changes.
It did not directly modify the repository.

### Human participation

The human developer selected the scope, reviewed all architectural and behavioral
decisions, validated characterization outcomes, approved each migration step,
identified unacceptable semantic changes, and decided which Codex recommendations
to accept, reject, or revise.

Codex produced analysis, tests, implementation changes, and documentation, but
the migration contract and final engineering decisions remained human-controlled.

## How to run

The migrated Rule Units project requires Java 21 and docker running. The specific Java version used is Amazon Corretto 21.0.4.
To run tests, cd into the application root.

For fast verification that excludes every Oracle `*IntegrationTest`:

```bash
./mvnw -Dtest='*Test,!**/*IntegrationTest' test
```

This runs the unit, characterization, DRL rendering, runtime-compilation,
snapshot, refresh, and mocked-persistence tests. It normally completes in
seconds and does not require Docker.

For the complete verification suite:

```bash
./mvnw clean test
```

The complete suite requires Docker to be running and accessible to the current
user. Testcontainers starts disposable Oracle XE containers from
`gvenzl/oracle-xe:21-slim-faststart`; the full run normally takes several
minutes because Oracle must start for the integration-test classes.

Neither command connects to or changes the prepared manual Oracle database. All
Oracle integration data is created inside disposable Testcontainers instances.

## Start the application locally with Testcontainers

The supported hackathon launch path starts the complete migrated application
and its own disposable Oracle XE container. Docker must be running. No database
URL, username, password, schema installation, or fixed Oracle host port is
required:

```bash
./mvnw -Dspring-boot.run.main-class=org.acme.ruleunits.local.LocalTestcontainersApplication \
  spring-boot:test-run
```

The launcher lives on the test classpath because Testcontainers and synthetic
fixtures are development infrastructure; the application, controllers, and rule
engine being launched are the production classes. It starts
`gvenzl/oracle-xe:21-slim-faststart`, lets Testcontainers select a random free
host port for Oracle, and prints that mapped port without printing credentials.
It then creates the verified minimal schema and loads the sample work orders and
active version-17 rule set from `src/test/resources/sql`.

The application listens on `http://localhost:8080`. Stop it with `Ctrl+C`; its
Oracle container and all synthetic data are discarded. The launcher makes its
generated datasource settings authoritative, so stale `SPRING_DATASOURCE_*`
variables cannot redirect it to the prepared legacy database. That database is
not contacted or modified.


### Refresh rules

The application starts on port 8080 by default. Startup creates the Spring/JPA
bean graph but does not load or compile the rule set; normal work-order execution
loads it lazily on first use. The administrative operation forces a full reload:

```bash
curl --fail-with-body -i -X POST \
  http://localhost:8080/admin/rules/refresh
```

A successful publication returns HTTP 200 and a sanitized body similar to:

```json
{
  "ruleSetName": "ACTIVITY_RULES",
  "attemptedVersion": 17,
  "correlationId": "<generated UUID>",
  "status": "PUBLISHED",
  "failurePhase": null,
  "failureType": null,
  "summary": null
}
```

A failed load, validation, assembly, compilation, or publication returns HTTP
503 with the attempted version when known, correlation ID, failure phase,
exception type, and fixed summary. It never returns generated DRL or detailed
compiler diagnostics. If an earlier snapshot exists, that last-known-good
snapshot remains available despite the 503.

In Postman, create a request with method `POST`, URL
`http://localhost:8080/admin/rules/refresh`, no request body, and no prototype
authorization header. The endpoint is intentionally unprotected for this
exercise. Do not expose it outside a controlled development environment;
production authentication and authorization must be decided together with the
Actuator security policy.

### Execute work-order rules

The mutating work-order endpoint is disabled by default. The local Testcontainers
launcher enables it; for an external Oracle launch, the
`RULEBRIDGE_RULES_EXECUTION_ENDPOINT_ENABLED=true` setting above does so.
The configured group code defaults to `A`; the controller itself does not
hardcode that value, so additional group executors can be registered later.

The request is a JSON array of work-order numbers:

```bash
curl --fail-with-body -i -X POST \
  'http://localhost:8080/reglas/correr-reglas?agrupador=A' \
  -H 'Content-Type: application/json' \
  --data '["matching","missing","matching","nonmatching"]'
```

Every found, nonempty work order produces legacy-compatible INFO logs in the
same terminal or IntelliJ Run console. Only active activity occurrences are
shown, duplicates are retained, and each entry uses `code-quantity`:

```text
### Evaluando OT: matching
Estado inicial: OT: matching - ACTIVIDADES: [6T8121-1, L81494-1, L81494-1, ZN8450-1]
Estado final: OT: matching - ACTIVIDADES: [FG2802-1]
```

A successful request returns HTTP 200 with the legacy-compatible body `true`.
The submitted list is limited to 100 entries by default before deduplication.
Repeated numbers are reduced to their first occurrence while preserving request
order. Missing work orders and work orders with no activities are silently
ignored. If all entries are ignored, the rules are not initialized.

All found work orders run sequentially using one leased, immutable rule-set
snapshot for the complete batch. A concurrent refresh can publish a new version
for later batches, while the in-flight batch finishes with its original version.
Each successful RA1, RA2, and RA3 stage still commits through its existing
independent transaction; a blocked work order does not stop later list entries.
An unexpected infrastructure failure stops the request, and earlier committed
stages or work orders are not rolled back.

A malformed group or work-order list returns HTTP 400. A syntactically valid but
unconfigured group returns HTTP 404. If the configured group cannot obtain an
initial valid rule snapshot, the endpoint returns a sanitized HTTP 503. The
response does not provide per-work-order outcomes.

In Postman, use method `POST`, URL
`http://localhost:8080/reglas/correr-reglas?agrupador=A`, select
**Body → raw → JSON**, and enter a JSON string array such as
`["matching","nonmatching"]`. No prototype authorization header is required.

Snapshot availability is not currently an OCP readiness gate. That is deliberate
because initialization is lazy: making an unloaded snapshot fail readiness would
prevent normal traffic from triggering the initial load. No Actuator endpoint is
part of this prototype.

## What this prototype does not prove

- It is not connected to a shared or production-like Oracle environment.
- The administrative refresh and work-order execution endpoints are not
  authenticated or authorized.
- It does not provide final OCP manifests, secret injection, health groups,
  monitoring, alerting, or an operational rollback runbook.
- It has not undergone production-scale performance or repeated-refresh memory
  qualification.
