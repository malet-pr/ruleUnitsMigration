# Rule Units migration workspace

## Why the project now compiles

The project uses Java 21, Spring Boot 3.5.10, and Drools/KIE 10.2.0. The `kjar` packaging activates the KIE-extended Maven lifecycle; without it, Maven only copied DRL resources and the provider fell back to runtime generation.

Dependency and plugin roles:

- `spring-boot-starter`: Spring runtime and logging; it does not configure Drools.
- `spring-boot-starter-web`: embedded HTTP runtime and Spring MVC for the administrative refresh endpoint.
- `drools-ruleunits-engine`: Rule Unit API, data sources, provider, and executable runtime.
- `drools-wiring-dynamic`: permits the isolated runtime KIE classloader to define executable-model classes assembled from database-provided DRL; static wiring alone rejects dynamic class definition.
- `spring-boot-starter-test`: JUnit 5 and AssertJ.
- `kie-maven-plugin`: validates DRL and generates the executable Rule Unit model in Maven's `compile` phase.
- `spring-boot-maven-plugin`: standard Boot packaging and execution.

Removed configuration:

- `kogito-maven-plugin` was not the generator for this standalone Drools Rule Units application.
- `kie-drools-exec-model-ruleunit-archetype` is a project template, not a runtime dependency.
- PostgreSQL, cache, actuator, Lombok, and ModelMapper were unused by increments 1-3. Increment 8 adds Spring Data JPA for Oracle mappings, the runtime Oracle JDBC driver, and test-scoped Testcontainers support.
- The custom compiler executions duplicated the normal Maven lifecycle.

The previous POM combined the wrong generator and an archetype dependency while omitting the Rule Units engine. It therefore did not provide the correct runtime/build-model combination.

## Generation and package contract

For `org.acme.ruleunits.ra1.Ra1Unit`, the DRL declares both
`package org.acme.ruleunits.ra1;` and `unit Ra1Unit;`. The smoke unit follows
the same rule. Resources are stored under the corresponding package path.

The `kjar` lifecycle invokes `kie:build` during Maven `compile`, after the normal Java compiler. The KIE goal generates and compiles the executable model into `target/classes`; verified outputs include `Ra1UnitRuleUnit.class`, `SmokeUnitRuleUnit.class`, and `META-INF/services/org.drools.ruleunits.api.RuleUnit`. Generated sources/classes must
exist before direct IDE execution. After changing DRL or unit data, run
`./mvnw compile` or delegate the IDE build/run to Maven.

`SmokeUnitTest` calls
`RuleUnitProvider.get().createRuleUnitInstance(new SmokeUnit())`. The provider
uses generated service metadata to find and instantiate the generated unit. The
test asserts the firing count and the consequence result, proving discovery,
instantiation, matching, and execution rather than only checking BUILD SUCCESS.

All DRL uses traditional `Type(...) from entry-point "dataSource"` patterns. OOPath is
intentionally excluded until OOPath with `accumulate` is evaluated separately.

## Increment boundaries

1. Correct build, generated-unit discovery, and runtime smoke test.
2. Framework-neutral occurrence/domain/action model, including duplicates,
   activity origin, and applied-rule identity.
3. Database-ready template model plus the same-shape RA1 replacement variants.
4. `RA1-test-2`, with active-category matching and category deactivation.
5. RA2 and explicit RA1 → save → RA2 stage sequencing.
6. Corrected RA3 and complete RA1 → save → RA2 → save → RA3 orchestration.
7. Repository-facing service and mapper verified at a mocked persistence boundary.
8. Oracle JPA adapters, per-stage transactions, and independently committed rule incidents verified with Oracle XE Testcontainers.
9. Versioned Oracle rule-definition schema and JPA mappings for immutable templates, ordered stages, rules, variable conditions, and actions.
10. Transactional Oracle loader, immutable rule-set snapshot, structural validation, and active-catalog validation before DRL assembly.
11. Traditional DRL assembly and isolated runtime KIE compilation.
12. Atomic compiled-snapshot publication with reference-counted lease and drain.
13. Serialized refresh coordination with sanitized failures and last-known-good retention.
14. Dynamic work-order orchestration through one leased compiled snapshot.
15. Full Oracle-to-runtime-to-Oracle migration-path proof.
16. Spring production wiring, single-flight lazy initialization, and an internal full-refresh service.
17. Unprotected administrative refresh HTTP endpoint and documented deployment boundary.
18. Opt-in legacy-shaped work-order batch endpoint with one snapshot lease per request.

## Increment 9 Oracle rule-definition persistence

`database/ct_rule_definition_schema.sql` adds versioned rule sets and immutable
template revisions without changing the legacy rule tables. Oracle constraints
allow multiple versions, require validation before activation, and permit only one
active version per rule-set name. Stage, rule, condition, and action positions are
unique within their owners and reload in database order.

The JPA aggregate lives under `org.acme.ruleunits.oracle.definition`. It stores
the traditional DRL template as a CLOB and represents replacement and accumulate
shapes as structured ordered data. DRL assembly, compilation,
activation services, first-call loading, and refresh remain deferred to later
increments. No Stage 9 schema was applied to the prepared database.

Oracle XE integration tests verify a complete active version round-trip, ordered
variable-length conditions, stored accumulate data, coexistence of versions, the
single-active-version constraint, validation-before-activation, and duplicate
position rejection.

## Increment 10 active rule-set loading and validation

`OracleRuleDefinitionLoader` reads the single active version inside a read-only
transaction and fully maps its lazy JPA aggregate into immutable records under
`org.acme.ruleunits.loading`. No JPA entity escapes the transaction.

`RuleSetDefinitionValidator` rejects a snapshot unless it has exactly ordered
RA1, RA2, and RA3 stages, consecutive rule/condition/action positions, safe
Java and DRL identifiers, supported traditional-rule shapes, compatible
condition operators and action parameters, and nonempty active rules. RA2 job
types are stored as ordered `JOB_TYPE`/`IN` values so the later assembler can
render one OR-list. Replacement and addition targets must exist and be active
through `ActivityCatalog` before publication.

Supported stored shapes are replacement by required activities, active-category
deactivation, deactivate-all plus addition, and accumulate/leave-one. Runtime
DRL rendering and KIE compilation remain deferred.

## Increment 11 runtime DRL assembly and compilation

`TraditionalDrlRenderer` deterministically combines immutable database-shaped definitions with stored rule-fragment templates. Templates provide `{{ruleName}}`, `{{when}}`, and `{{then}}`; Java package, `unit` declaration, imports, structured conditions, and structured actions are generated centrally. The renderer emits traditional DRL patterns only, including variable-length activity conditions, RA2 `in (...)`, and nested `from` plus `accumulate`.

`RuntimeRuleSetCompiler` builds all rendered stages together as a Drools executable model through the public KIE builder API. It rejects compiler errors before publication, removes embedded KIE stack traces from returned diagnostics, discovers generated units through service metadata, and retains an isolated `KieContainer` until the compiled snapshot is closed. `drools-wiring-dynamic` is a runtime-scoped service provider required because these generated classes do not exist at Maven build time. Stage definitions must use distinct fully qualified Rule Unit data class names.

Runtime unit-data classes use separate `org.acme.ruleunits.runtime.*` identities. Reusing the checked-in RA1/RA2/RA3 unit names caused parent-first classloading to select the build-time implementations instead of the newly compiled rules. The separation allows the reference rules and database-assembled rules to coexist during migration.

Tests prove rendering, compilation, discovery, instantiation, and actual firing for the selected RA1, RA2, and corrected RA3 rules. The traditional accumulate rule remains a separate capability fixture and execution test. A malformed stored template is rejected with sanitized diagnostics. Oracle-loaded execution and production bean wiring remain deferred.

## Database-ready rule-template design

Increment 3 separates the replace-activity rule family into data that can later
be persisted independently:

- `RuleTemplate.drlTemplate`: shared DRL text with package, unit, and rendered
  rule placeholders.
- `TemplateRuleDefinition`: rule identity, rule type, and work-order type.
- `RequiredActivityCondition`: one ordered row per required activity, allowing
  different rules to have different numbers of conditions.
- `ReplacementActionDefinition`: the old and new activity fields.
- `RuleSetAssembler`: validates stored values and combines the fields into a
  deterministic DRL rule set.

The checked-in `ra1.drl` contains that generated-shape family plus
`RA1-test-2`, which has a different category-deactivation shape. Database
persistence remains deferred. Loading changed database rules without rebuilding
will require a separately tested runtime compilation, validation, caching, and
reload strategy because the current `kjar` provider is generated at build time.

## Stage semantics

Each Rule Unit evaluates one stage's accumulated input. Its queued actions are
then applied against a snapshot of that stage input, so actions from rules in the
same unit do not depend on firing order. Newly created occurrences are appended
after those mutations.

The stage result crosses `WorkOrderStageSaver` before the next unit executes.
At that next boundary, all active activities behave alike regardless of whether
they entered the original work order or were created by an earlier rule. Thus
RA2's deactivate-all command also deactivates RA1-created activities. The
production-only exception for manually created activities is intentionally not
modeled.

`RulesExecutionResult.eligibleForSave` remains true for a valid nonmatching work
order. The current saver is a boundary supplied by the caller; Oracle persistence
is not implemented in these increments.

## Activity guard

`ActivityCatalog` is the future Oracle lookup boundary. Before applying a stage
that would create activity occurrences, `RuleActionApplier` validates every
distinct new code. If any code does not exist or is inactive, it throws
`InvalidActivityAdditionException` before any stage mutation.

The tests use an in-memory catalog. Oracle existence/active-state queries are not
implemented or simulated as persistence tests yet.

## Corrected RA3 behavior

Category conditions use `activeActivityCategories`. Therefore inactive CAT3
activities left by RA2 cannot activate RA3. For the characterized work order
`5007049484`, RA2 deactivates its CAT3 occurrence and creates `FG2802`; RA3
does not fire, so the final active result is only `FG2802`.

## Traditional syntax with accumulate experiment

The isolated `AccumulateUnit` verifies that KIE 10.2 can generate and execute a
Rule Unit containing traditional nested `from` syntax with `accumulate`:

```drl
WorkOrderEvaluation($activities : activeActivityCodes)
    from entry-point "workOrders"
List(size > 1) from accumulate(
    String(this == "FG2802") from $activities;
    collectList()
)
```

This is the Rule Unit equivalent of collecting matching strings from the legacy
adapter's activity-code list. It intentionally uses no OOPath. The consequence
queues `DeactivateActivitiesExceptOne`, which preserves the first active
`FG2802` occurrence in work-order list order and attributes every deactivated
duplicate to `withAccumulate`.

Focused tests cover zero, one, multiple, and inactive occurrences. The experiment
is isolated from the RA1 → RA2 → RA3 production sequence until its intended stage
placement and database-template shape are approved.

## Increment 7 mocked persistence adapter

`WorkOrderRulesService` loads a repository record, maps it to
`WorkOrderEvaluation`, executes the staged rules, maps each successful stage
back onto the same record, and saves through `WorkOrderRepository`. Missing work
orders are ignored; valid nonmatching work orders still cross all three save
boundaries.

`WorkOrderMapper` preserves persistence IDs for existing activity rows and uses
a separate in-memory domain occurrence ID to correlate newly created rows across
later stage saves. New records keep a null persistence ID for the eventual Oracle
sequence and reference their owning work order.

For the exercise failure policy, an invalid activity addition leaves its stage
unchanged, preserves earlier successful saves, and stops later stages. The failing
stage is not saved. `BLOCKED`, the failed stage, a stable error code, and bounded
sanitized detail remain available in the processing outcome/record, while an
append-only incident is committed independently. Repository failures themselves
are not converted into rule-stage failures.

The repository-facing models remain independent of JPA. Increment 8 maps them
through Oracle entities and Spring Data repositories. Each successful stage save
uses `REQUIRES_NEW`; generated activity IDs are copied back to the record so later
stages update those rows. `ActivityCatalog` reads Oracle existence and active state.

`CT_OT_RULE_INCIDENT` is a separate append-oriented operational table keyed to
the work-order ID and optionally to the unique rule name. Incidents use their own
`REQUIRES_NEW` transaction, store no stack trace, and are marked `RESOLVED` after
a later successful run. The migration script is checked in but is not applied to
the prepared database. Oracle XE 21 integration tests create a minimal schema and
verify matching, nonmatching, missing, generated-ID/attribution, and blocked-stage
behavior against real Oracle constraints and transactions.

The proposed dynamic Oracle rule-definition/loading design remains in
`docs/oracle-rule-definition-runtime-loading-design.md`.

## Increment 12 atomic compiled-snapshot lifecycle

`RuleSetSnapshotManager` owns the single published `CompiledRuleSet`. Calling
`publish` transfers ownership to the manager, including when publication is
rejected because the manager has already shut down. Callers must not close a
compiled rule set after transferring it. Publication is serialized and swaps the
current snapshot atomically; compilation itself remains outside this class.

Executions acquire a reference-counted `RuleSetLease`. Publication immediately
prevents new leases on the retired snapshot, while existing leases may continue
to create Rule Unit instances. The retired snapshot closes only after its final
lease is released. Manager shutdown uses the same drain behavior. Both the lease
and every `RuleUnitInstance` must be closed with try-with-resources:

```java
try (RuleSetLease lease = snapshotManager.acquire()) {
    try (var instance = lease.createInstance("RA1", unitData)) {
        instance.fire();
    }
}
```

Before an initial publication, and after manager shutdown, `acquire` throws
`RuleExecutionUnavailableException`. A later candidate that fails validation,
assembly, or compilation is never passed to `publish`, so the current snapshot
is unchanged. `RuleSetRefreshCoordinator` now owns the fair lock spanning the
complete `LOAD` → `VALIDATE` → `ASSEMBLE` → `COMPILE` → `PUBLISH` operation, adds correlation-aware
sanitized refresh reporting, and retains the last-known-good snapshot on failure.
Production bean wiring remains disconnected; Increment 14 adds dynamic orchestration without registering production beans.

Retirement releases the owned `KieContainer`, generated-unit references, and KIE
repository module exactly once after drain. A cleanup failure is reported using
only rule-set name, version, failure type, and a fixed summary; it does not undo
a successful publication. This lifecycle deliberately makes no claim that the
JVM will unload generated classes immediately after their strong owners are
released.

Concurrency tests cover readers draining across publication, simultaneous
publish calls, shutdown, rejected publication, idempotent lease closure, and
sanitized cleanup failure. A real runtime-compilation test additionally verifies
actual Rule Unit execution and KIE repository-module retention/removal at lease
boundaries.

## Increment 13 serialized refresh coordination

`RuleSetRefreshCoordinator` executes one complete refresh at a time under a fair
lock. Its phases are explicit and ordered: `LOAD`, `VALIDATE`, `ASSEMBLE`,
`COMPILE`, and `PUBLISH`. `OracleRuleDefinitionLoader` is now a pure
`RuleSetDefinitionSource`; it maps and detaches the active Oracle aggregate but
does not hide validation inside the load phase. `TraditionalDrlRenderer` performs
assembly, while `RuntimeRuleSetCompiler` accepts only an already rendered rule
set.

Every call returns a `RuleSetRefreshResult`. Success identifies the published
rule-set version and correlation ID. Failure identifies the attempted version
when loading reached it, the exact phase, exception type, correlation ID, and a
fixed sanitized summary. Exception messages, generated DRL, KIE diagnostics, and
stack traces are not retained in the result or passed to the ordinary failure
logger. The narrow internal Drools `KieProject` logger is disabled in application
configuration because it otherwise duplicates verbose compiler diagnostics before
the coordinator can sanitize them.

Validation always completes before assembly begins. A failure in any phase skips
all later phases. A candidate reaches `RuleSetSnapshotManager.publish` only after
successful compilation; invoking publication transfers ownership to the snapshot
manager. Failed load, validation, assembly, or compilation therefore leaves the
current snapshot untouched. Before an initial successful refresh, execution
remains unavailable. After an initial publication, a failed refresh continues to
serve the last-known-good snapshot.

Focused tests cover all five failure phases, exact phase order, sanitized results,
reporter isolation, and concurrent refresh serialization. A real KIE test publishes
version 17, rejects syntactically invalid version 18 during compilation, and then
executes RA1 again through the still-current version 17 snapshot.

The coordinator is intentionally not yet registered as a Spring production bean.
Lazy first-call loading, the explicit administrative refresh mechanism, and
production wiring remain later increments. Increment 14 provides work-order
execution through a leased dynamic snapshot.

## Increment 14 dynamic snapshot execution

`WorkOrderRulesEngine` separates repository-facing processing from a particular
rule implementation. The existing `ActivityCatalog` constructors still adapt to
`SelectedRulesOrchestrator`, preserving the build-time migration path and its
existing tests. A new constructor accepts the dynamic engine without changing
mapping, incident, missing-work-order, or per-stage persistence behavior.

`DynamicRulesOrchestrator` acquires one `RuleSetLease` for the complete work-order
run. It executes RA1, saves, executes RA2, saves, executes RA3, and saves before
releasing that lease. Each stage creates its runtime unit data and closes its
`RuleUnitInstance` with try-with-resources. Consequently, a snapshot published at
a stage save boundary can serve new work while the in-flight work order completes
all later stages against its original rule-set version.

The runtime test fixture now represents the complete selected migration rules:
the three same-template RA1 replacement variants, job-type-guarded RA1 category
deactivation, RA2 refinement, and corrected active-category RA3. The accumulate
experiment remains in a separate runtime-compiled fixture, so it continues to
prove traditional `from accumulate` execution without entering the selected
RA1 → RA2 → RA3 sequence.

Six dynamic characterization tests execute generated runtime Rule Units through
`WorkOrderRulesService` and a mocked repository boundary. They freeze duplicate
replacement and attribution, category attribution, corrected RA3 active-only
matching, nonmatching saves, missing-work-order handling, and the invalid-addition
policy that preserves the last saved stage. A separate real-KIE test publishes a
version 18 during the RA1 save and proves that RA2 still follows leased version
17; after the run releases its lease, the retired version-17 KIE module is
removed.

This increment does not register Spring beans, invoke the Oracle definition
loader, define first-call initialization, or expose the administrative refresh
mechanism. Those production lifecycle decisions remain the next integration
boundary.

## Increment 15 complete migration-path proof

`Stage15MigrationPathIntegrationTest` composes all migration boundaries against
one Oracle XE Testcontainer. It persists the selected rule templates, ordered
stages, parameterized rules, conditions, and actions through the Oracle JPA
model. `OracleRuleDefinitionLoader` then loads the active version and
`RuleSetRefreshCoordinator` validates it against `OracleActivityCatalog`,
assembles traditional DRL, compiles the runtime Rule Units, and atomically
publishes version 17.

One published snapshot is reused to process multiple Oracle work orders through
`DynamicRulesOrchestrator` and `WorkOrderRulesService`. The test verifies duplicate
replacement and later RA2 attribution, a second RA1 rule assembled from the same
template with different database parameters, corrected active-only RA3 behavior,
generated activity persistence, and completion through the RA3 save boundary.

The same service-lifetime scenario then marks `FG2802` inactive after publication.
The runtime activity guard rejects the next RA2 addition, retains the work order
at its successfully saved RA1 state, and writes the rule name, stage, and stable
error code to `CT_OT_RULE_INCIDENT`. This demonstrates why definition-time
validation and execution-time catalog validation are both required.

The proof uses only disposable Testcontainers data; it does not alter the prepared
Oracle database. Increment 16 updates this test to obtain the runtime through the
production Spring bean graph and trigger its first publication through lazy
work-order execution.

## Increment 16 Spring wiring and operational refresh

`RuleUnitsRuntimeConfiguration` registers the Oracle-backed definition source,
validator, traditional DRL renderer, runtime compiler, snapshot manager, dynamic
rules engine, repository-facing service, and UTC processing clock as one Spring
bean graph. The snapshot manager is a managed closeable bean, so application
context shutdown retires the current snapshot and releases its owned KIE resources
after outstanding leases drain. Merely creating the application context does not
read or compile rules.

`LazyInitializingRulesEngine` calls `RuleSetRuntimeService.ensureInitialized()`
before delegating to the existing one-lease RA1 -> RA2 -> RA3 orchestrator. The
first execution for a work order found by the repository loads, validates,
assembles, compiles, and publishes the configured `ACTIVITY_RULES` rule set.
Later executions reuse the published snapshot. Concurrent first executions share
one initialization attempt. If that attempt fails, its callers receive
`RuleExecutionUnavailableException`; a later execution may retry. No work-order
stage runs or saves before initial publication succeeds. A missing requested work
order remains ignored before rules execution and therefore does not initialize the
runtime.

`RuleSetRuntimeService.refresh()` is the synchronous internal administrative
boundary. It always performs a complete serialized refresh and returns the
sanitized `RuleSetRefreshResult`; a failed later refresh retains the current
last-known-good snapshot. It accepts no caller-provided rule-set name. Stage 16
deliberately exposed no HTTP endpoint. Stage 17 adds the agreed unprotected
prototype administrative endpoint; its production authorization policy remains
part of the same open decision as Actuator endpoint protection.

Focused tests verify bean registration without eager rule loading, context-owned
snapshot shutdown, successful single-flight initialization, shared concurrent
failure, retry by a later call, internal refresh delegation, and prevention of
rule execution while initialization is unavailable. The Oracle migration-path
test verifies lazy loading and the internal refresh through the production bean
graph.

## Increment 17 administrative refresh and deployment boundary

`spring-boot-starter-web` makes the migrated project an executable HTTP
application. `RuleSetRefreshController` exposes
`POST /admin/rules/refresh` as a production Spring MVC endpoint. It accepts no
body and no caller-selected rule-set name. The request synchronously delegates to
`RuleSetRuntimeService.refresh()`, so it shares the existing serialized
load → validate → assemble → compile → publish path.

A published snapshot returns HTTP 200. A failed attempt returns HTTP 503 with
only the sanitized `RuleSetRefreshResponse`: configured rule-set name, attempted
version when known, correlation ID, status, failure phase, exception type, and
fixed summary. Generated DRL, exception messages, stack traces, and verbose KIE
diagnostics are not exposed. A failed later refresh retains and continues serving
the last-known-good snapshot.

The focused MVC test verifies both response contracts and raw-diagnostic
suppression. `RuleSetRefreshEndpointIntegrationTest` starts the complete
application on a random HTTP port with disposable Oracle XE, loads an active
database-shaped definition, invokes the real endpoint, and verifies publication.
It then corrupts a stored condition, invokes the endpoint again, receives a
validation failure, and proves the previously published version remains current.

The application never applies Oracle DDL or rule seed data. The reviewed
`database/ct_ot_rule_incident_schema.sql` and
`database/ct_rule_definition_schema.sql` scripts remain explicit deployment
inputs. Automated integration tests apply equivalent test resources only inside
Testcontainers. A manual successful refresh requires the configured database to
contain those objects and one validated, active `ACTIVITY_RULES` version.

The prototype endpoint is intentionally unauthenticated and must remain confined
to a controlled environment. Production authentication, authorization, audit,
and the relationship to Actuator security remain open. Snapshot availability is
not an OCP readiness gate because first initialization is lazy; a readiness gate
would otherwise prevent ordinary traffic from causing that first load. Final OCP
manifests, secret injection, health groups, monitoring, capacity qualification,
staged rollout, and operational rollback remain production work rather than
claims made by this hackathon prototype.

## Increment 18 work-order execution HTTP boundary

`WorkOrderRulesController` exposes the legacy-shaped
`POST /reglas/correr-reglas?agrupador={code}` operation only when
`rulebridge.rules.execution-endpoint-enabled=true`. It accepts a JSON array of
work-order numbers and returns the legacy-compatible Boolean `true` after
successful processing. Invalid group/list input returns HTTP 400, an unconfigured
but syntactically valid group returns HTTP 404, and an unavailable initial rule
snapshot returns a sanitized HTTP 503.

The controller validates the raw request against
`rulebridge.rules.execution.max-batch-size` before reducing duplicates to the
first occurrence in request order. `RuleGroupExecutorRegistry` resolves the
requested group without hardcoding `A` in the HTTP layer. The current Spring
configuration registers the value of `rulebridge.rules.group-code`—default
`A`—against the selected migrated engine; later production groups require their
own registered executors and rule-set mapping.

`WorkOrderRulesService.processBatch` performs each unique repository lookup
once, silently ignores missing work orders and records with no activities, and
does not initialize rules when none remain. Found work orders run sequentially.
One `WorkOrderRulesBatch` owns one snapshot lease for the entire request, so a
refresh published during a batch affects only later batches. Closing the batch
releases the lease and allows a retired compiled rule set to drain.

Per-stage persistence semantics are unchanged: every successful RA1, RA2, and
RA3 boundary uses its existing `REQUIRES_NEW` save. A rule-blocked work order
returns an internal blocked outcome and processing continues with the next work
order. Unexpected infrastructure exceptions stop the HTTP request; already
committed work remains committed because there is no outer batch transaction.

Focused tests verify HTTP validation, deduplication, disabled-by-default
registration, service filtering, lazy initialization, one lease per batch, and
retirement during a real runtime refresh. The full HTTP integration test loads
and compiles Oracle-provided rules, processes a mixed batch through the embedded
server, verifies Oracle activity rows, and then reconfirms last-known-good
retention after a failed refresh. It uses only disposable Oracle Testcontainers
data and never the manually prepared database.

## Local Testcontainers application launcher

`LocalTestcontainersApplication` is a development-only main class that starts a
disposable Oracle XE container and then launches the existing production Spring
application. The launcher and its synthetic SQL resources deliberately remain
on the test classpath and are not packaged as production runtime dependencies.

```bash
sdk use java 21.0.4-amzn
./mvnw -Dspring-boot.run.main-class=org.acme.ruleunits.local.LocalTestcontainersApplication \
  spring-boot:test-run
```

Testcontainers assigns Oracle a random free host port. The launcher creates the
minimal persistence and rule-definition schemas, loads the verified endpoint
fixtures, and enables work-order execution. Its datasource property source takes
precedence over environment variables to prevent accidental legacy-DB access.

Datasource credentials are generated by Testcontainers and are neither required
nor printed. The externally prepared legacy Oracle container is not contacted;
stopping the application also stops the disposable container, and its data is
discarded.
