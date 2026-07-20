# Oracle rule-definition and runtime-loading design

## Scope

This document records the boundary for storing, assembling, validating, loading,
compiling, and publishing database-provided Rule Unit DRL. Oracle access, runtime
compilation, atomic snapshots, lazy initialization, and the administrative HTTP
boundary are implemented through Increment 17. A cache library, production
endpoint authorization, and distributed coordination are not implemented.

Work-order persistence remains a separate boundary. Increment 7 provided the
mocked boundary; Increment 8 implemented and tested its Oracle JPA adapters.
Increments 9 through 17 implement the versioned definition, loader, compiler,
snapshot, refresh, dynamic execution, Spring lifecycle, and HTTP boundaries.

## Proposed rule-definition mappings

### RULE_SET

- `ID`: primary key.
- `NAME`: business name.
- `VERSION`: monotonically increasing version within the name.
- `STATUS`: `DRAFT`, `VALID`, `ACTIVE`, or `RETIRED`.
- `VALIDATED_AT`, `ACTIVATED_AT`: lifecycle timestamps.
- `VALIDATION_MESSAGE`: sanitized validation result.

Only one version of a named rule set may be active. Activation must be atomic
and must only accept a successfully validated version.

### RULE_TYPE

- `ID`: primary key.
- `RULE_SET_ID`: owning rule-set version.
- `CODE`: for example `RA1`, `RA2`, or `RA3`.
- `STAGE_ORDER`: explicit cross-stage execution order.
- `ACTIVE`: activation flag.
- `UNIT_PACKAGE`, `UNIT_NAME`: Java/DRL Rule Unit identity.

Rule type is the natural grouping key for loading and compiling a stage.

### RULE_TEMPLATE

- `ID`: primary key.
- `TEMPLATE_KEY`, `TEMPLATE_VERSION`: stable identity for an immutable template revision.
- `DRL_TEMPLATE`: CLOB containing the reviewed DRL template.
- `SHAPE`: for example `REPLACE_REQUIRED_ACTIVITIES`,
  `DEACTIVATE_CATEGORY`, or `ACCUMULATE_LEAVE_ONE`.
- `ACTIVE`: activation flag.

Templates contain reviewed DRL structure. Values supplied by rule rows are
validated and substituted into known placeholders; arbitrary fragments are not
accepted as condition or consequence values.

### RULE_DEFINITION

- `ID`: primary key.
- `RULE_TYPE_ID`, `RULE_TEMPLATE_ID`: owning stage and template.
- `RULE_NAME`: unique within the Rule Unit.
- `WORK_ORDER_TYPE`, `JOB_TYPE`: optional structured conditions.
- `ACTIVE`: activation flag.
- `RULE_ORDER`: deterministic assembly order only; it does not impose agenda
  order within a Rule Unit.

### RULE_CONDITION

- `ID`: primary key.
- `RULE_DEFINITION_ID`: owning rule.
- `POSITION`: ordered position within the repeated condition family.
- `CONDITION_TYPE`: for example `REQUIRED_ACTIVITY`,
  `ACTIVE_CATEGORY`, or `ACCUMULATED_ACTIVITY`.
- `OPERATOR`: a constrained allow-listed operator.
- `VALUE`: validated condition value.
- `NUMERIC_VALUE`: optional threshold such as the accumulate count.

The unique key `(RULE_DEFINITION_ID, POSITION)` preserves variable-length
condition order.

### RULE_ACTION

- `ID`: primary key.
- `RULE_DEFINITION_ID`: owning rule.
- `POSITION`: consequence order.
- `ACTION_TYPE`: allow-listed command such as `REPLACE_ACTIVITY`,
  `DEACTIVATE_CATEGORY`, `DEACTIVATE_ALL`, `ADD_ACTIVITY`, or
  `DEACTIVATE_EXCEPT_ONE`.
- `OLD_ACTIVITY_CODE`, `NEW_ACTIVITY_CODE`, `CATEGORY`: structured action
  parameters.

Activities introduced by an action must pass the active activity-catalog check
before a stage mutates the work order.

## Assembly and validation

The loader reads one active rule-set version and groups definitions by rule
type. Each group is assembled into a complete DRL resource with matching Java
package, DRL package, and `unit` declaration.

Validation is split across lifecycle steps. Increment 10 validates the active snapshot again before it can be assembled or published:

1. Load one complete active version transactionally into immutable records.
2. Validate identifiers, allow-listed operators, condition types, action types,
   placeholders, unique names, and ordered rows.
3. Validate every activity an action can add exists and is active.
4. Assemble complete traditional-syntax DRL for every unit.
5. Compile all units together using the chosen KIE runtime compilation API.
6. Instantiate each unit through `RuleUnitProvider` and run structural smoke
   tests.
7. Record validation success or sanitized diagnostics.

The accumulate shape uses the verified traditional syntax:

```drl
WorkOrderEvaluation($activities : activeActivityCodes)
    from entry-point "workOrders"
List(size > 1) from accumulate(
    String(this == "FG2802") from $activities;
    collectList()
)
```

## Compilation decision

The checked-in migration currently uses build-time `kjar` generation. Database
rules must be changeable independently of an application build, so the eventual
database-backed loader requires runtime assembly and compilation.

`RuntimeRuleSetCompiler` isolates runtime compilation and returns an owned
`CompiledRuleSet`. The existing build-generated units remain the reference
implementation, while dedicated tests cover runtime compilation, class loading,
diagnostics, KIE resource disposal, and dynamic wiring.

## Stage 16 daily in-memory behavior

The operational requirement is:

1. On the first engine call after service startup, read the complete active rule set from Oracle.
2. Publish one immutable in-memory snapshot containing its version and all rule types.
3. Reuse that snapshot for subsequent work orders until process restart or an explicit full refresh.

Increment 16 implements this policy through `RuleSetRuntimeService` and
`LazyInitializingRulesEngine`. Initialization is deferred until a found work order
reaches rules execution. Concurrent first executions share an attempt; a later
call can retry after an initial failure. The synchronous internal refresh method
reloads the complete configured rule set and preserves last-known-good behavior.

Cache implementation is deferred. The initial deployment has one OCP instance, a planned daily restart, and an explicit full-refresh mechanism. Distributed invalidation and multi-instance coordination are outside the current scope. A later cache library may add convenience, but it does not replace atomic snapshot publication or last-known-good refresh behavior.

## Stage 17 administrative HTTP boundary

`POST /admin/rules/refresh` synchronously invokes the same complete serialized
refresh used internally. It accepts no body and cannot select another rule-set
name. Successful publication returns HTTP 200; any failed phase returns HTTP 503
with a sanitized correlation-aware result. A failed later call leaves the
last-known-good snapshot published.

The endpoint is intentionally unprotected for the prototype. It does not apply
DDL, seed definitions, or make snapshot availability an OCP readiness gate.
Automated HTTP verification uses disposable Oracle Testcontainers data.
Production authorization, audit, OCP health configuration, and rollout controls
remain deployment decisions.


## Stage 18 work-order execution HTTP boundary

`POST /reglas/correr-reglas?agrupador={code}` restores the exercise-facing
legacy request shape without coupling the controller to group `A`. A
`RuleGroupExecutorRegistry` maps configured group codes to execution
strategies. The prototype registers one code from
`rulebridge.rules.group-code`; a valid but unregistered code returns HTTP 404.

The endpoint is mutating and disabled by default. When enabled, it validates the
raw JSON array and configured maximum size, retains only the first occurrence of
each work-order number, preserves request order, and returns Boolean `true` on
successful request completion. Missing work orders and work orders without
activities remain silently ignored. If none remain, no lazy rule initialization
occurs.

One batch acquires one immutable snapshot lease and processes found work orders
sequentially. A refresh may atomically publish a replacement while the batch is
running, but every work order in that request keeps using the leased version.
The retired compiled rule set closes only after the batch releases its lease.
Stage saves remain independent `REQUIRES_NEW` transactions; there is no outer
batch rollback. Rule-blocked outcomes continue to later entries, while unexpected
infrastructure failures terminate the request.

A disposable Oracle HTTP integration test verifies refresh, runtime compilation,
mixed-batch execution, duplicate suppression through persisted row counts,
missing-work-order handling, nonmatching state, and last-known-good retention.
The endpoint does not apply schema changes or seed the manually prepared database.

## Stage persistence and failure policy

Execution remains `RA1 → save → RA2 → save → RA3 → save`. A stage validates
all additions before applying any action, making the failing stage atomic.

For this exercise:

- Successful earlier stage saves remain.
- A failing stage does not mutate its input.
- Later stages do not execute.
- The failing stage is not saved; the already committed previous-stage state remains.
- A separate append-oriented incident row is committed independently with the
  work-order ID, nullable unique rule name, stage, code, bounded detail, timestamp,
  status, and processing-attempt ID. Full stack traces remain in application logs.

No rule-error columns are added to the already large work-order table. The script
`database/ct_ot_rule_incident_schema.sql` defines the separate incident table but
has not been applied to the prepared database. A successful later run resolves
open incidents for that work order; retry policy remains an operational decision.

## Oracle adapter boundaries

- `ActivityCatalog`: query an activity by code and require its active flag.
- `WorkOrderStageSaver`: implemented by the orchestration callback and
  `OracleWorkOrderRepository`; maps accumulated state and commits each successful
  stage in `REQUIRES_NEW`.
- `WorkOrderRepository`: find by work-order number and save the aggregate.
- `RuleDefinitionRepository`: read the single active rule-set version with its
  types, templates, ordered conditions, and ordered actions.

## Required tests before database-provided execution

- Mapping preserves work-order and occurrence identity, duplicates, inactive
  rows, quantities, category, origin, and rule attribution.
- Matching and nonmatching valid work orders are saved.
- Missing work orders are ignored.
- Every successful stage is saved in order.
- An invalid addition leaves the failing stage unchanged, stops later stages,
  preserves earlier saves, and records structured failure status.
- Rule-set activation rejects incomplete, unsafe, or uncompilable definitions.
- Traditional replacement, category, add/deactivate, and accumulate templates
  compile and execute from assembled database-shaped input.
- Concurrent first load publishes one complete compiled rule set and never a
  partially built map.
- Refresh failure retains the previously valid compiled set, subject to later
  cache-policy approval.

## Open decisions

- The prototype administrative refresh and work-order execution endpoints are
  unprotected. Production authorization remains open and will be decided with
  Actuator endpoint protection.
- The production mapping of group codes B, C, and D to rule-set names and stage
  sequences is not represented by the one-group prototype registry.
- The production batch-size limit requires capacity and request-timeout testing.
- Multi-instance coordination if the service later becomes safe to scale horizontally.
- Retry behavior for blocked work orders.
- Maximum diagnostic length and whether full compiler diagnostics belong in a
  separate audit table.
- Manual-activity exception omitted from this exercise.
