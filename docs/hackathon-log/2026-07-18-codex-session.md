# Codex session handoff — 2026-07-18

## Completed and verified work

### Migration foundation

- Converted `ruleunits/` into a Java 21, Spring Boot 3.5.10, Drools/KIE 10.2.0 `kjar`.
- Corrected Maven dependencies and enabled build-time Rule Unit generation through `kie-maven-plugin`.
- Confirmed generated Rule Unit discovery through `RuleUnitProvider`.
- Removed placeholder persistence, PostgreSQL, and unrelated dependencies.
- Used traditional DRL pattern syntax throughout; OOPath is not used.

### Current migration architecture

- `WorkOrderEvaluation` represents accumulated work-order state.
- `ActivityOccurrence` preserves duplicates, active state, quantity, category, origin, creating stage, and applied-rule attribution.
- Each rule stage has its own Rule Unit and action queue.
- `RuleActionApplier` applies queued actions against a snapshot of the stage input.
- New occurrences are appended after stage mutations, avoiding reliance on rule firing order within a unit.
- Explicit orchestration is `RA1 → save → RA2 → save → RA3 → save`.
- `WorkOrderStageSaver` represents persistence without implementing Oracle access.
- `ActivityCatalog` represents the future Oracle activity-validation boundary.
- Valid nonmatching work orders remain eligible for saving.

### Rules migrated

- `RA1-test-1`: requires a final work order with `6T8121` and `L81494`; replaces every active `L81494` occurrence with `097079`.
- `RA1-test-1-2`: requires a final work order with `DS7068` and `KO6502`; replaces `KO6502` with `SS8192`.
- `RA1-test-1-3`: requires a final work order with `DS7068` and `G99427`; replaces `G99427` with `Q79984`.
- `RA1-test-2`: requires job type `FM3X635` and active CAT2; deactivates active CAT2 occurrences.
- `RA2-test-1`: matches the four characterized job types, deactivates every active occurrence from RA1, and adds `FG2802`.
- `RA3-test-1`: requires `JM5G513` and active CAT3, deactivates accumulated active occurrences, and adds `AZ9593`.

### Parameterization

Introduced a database-ready model:

- `RuleTemplate`
- `TemplateRuleDefinition`
- `RequiredActivityCondition`
- `ReplacementActionDefinition`
- `RuleSetAssembler`

It supports shared templates, variable numbers of activity conditions, separate action fields, deterministic DRL assembly, and value validation. Database storage and runtime loading were not implemented.

### Activity-validation bug correction

- Every activity an action attempts to add must exist and be active.
- `ActivityCatalog` is consulted before stage mutation.
- All distinct additions are validated first.
- Invalid additions raise `InvalidActivityAdditionException`.
- No stage mutation occurs when validation fails.
- Tests currently use an in-memory catalog.

### Accumulate experiment

An isolated A3 Rule Unit uses traditional syntax:

```drl
WorkOrderEvaluation(
    $activities : activeActivityCodes
) from entry-point "workOrders"

List(size > 1) from accumulate(
    String(this == "FG2802") from $activities;
    collectList()
)
```

KIE model generation and execution passed. Multiple active `FG2802` occurrences fire the rule; zero, one, and inactive-only cases do not. The consequence leaves one active occurrence and deactivates the remaining duplicates. The experiment is not integrated into the production stage sequence.

### Approved divergences and decisions

- Inactive activities cannot satisfy category conditions, correcting the legacy RA3 defect.
- After a stage is saved, all active activities behave equally in the next stage, including rule-created activities.
- RA2 deactivate-all therefore consumes RA1-created `097079` occurrences.
- Work order `5007049484` ends with only active `FG2802`.
- Actions within one stage must not depend on rule firing order.
- The production-only exception for manually created activities remains intentionally excluded.
- Missing work orders remain ignored.
- Valid nonmatching work orders remain eligible for saving.
- Oracle remains the target database.

## Files added or modified

- `ruleunits/pom.xml` and `ruleunits/README.md`.
- Domain, action, catalog, definition, orchestration, and Rule Unit classes under `ruleunits/src/main/java/`.
- RA1, RA2, RA3, smoke, template, and accumulate resources under `ruleunits/src/main/resources/`.
- Corresponding tests under `ruleunits/src/test/java/`.
- Removed the obsolete generated Spring context test.

The unrelated existing `database/notes.txt` change was not modified.

## Tests and commands

Codex repeatedly ran:

```bash
sdk use java 21.0.4-amzn
./mvnw clean test
```

Final result:

- 24 tests passed.
- No failures or errors.
- 58 canonical-model files generated.
- Generated RA1, RA2, RA3, smoke, and accumulate Rule Units confirmed.
- Provider service metadata confirmed.
- `git diff --check` passed.
- No OOPath found.

The user independently reviewed the changes and reran the tests after the earlier migration increments and after the staged-rule changes. The exact user-side command was not stated.

## Work currently in progress

- No implementation is currently running.
- The accumulate rule remains an isolated compatibility experiment.
- `WorkOrderStageSaver` and `ActivityCatalog` have test implementations only.
- Rule templates and definitions are not persisted or loaded dynamically.

## Proposed future work

### Unresolved issues, risks, and assumptions

- Decide whether database rule changes require runtime compilation or a controlled rebuild.
- Design DRL validation, caching, versioning, and atomic reload behavior.
- Define the schema for templates, ordered conditions, actions, activation state, and versions.
- Determine Oracle transaction boundaries for stage saves.
- Add the omitted rule-set-applied audit column after its schema and semantics are provided.
- Decide whether a later-stage failure rolls back earlier stage saves.
- Determine where the accumulate rule belongs in the real sequence.
- Design parameterization for accumulate rules.
- Confirm that deactivate-all-but-one must preserve the first occurrence in list order.
- Add the manually created activity exception only when its semantics are supplied.
- Design isolated Oracle integration tests.

### Exact recommended next task

Design, but do not yet implement, the Oracle-backed rule-definition and runtime-loading boundary. Specify:

1. Tables or mappings for templates, rules, ordered conditions, and actions.
2. Rule-set versioning and activation.
3. Assembly and validation of traditional DRL, including `accumulate`.
4. Build-time versus runtime compilation.
5. Cache and atomic reload behavior.
6. Stage transaction and rollback boundaries.
7. Oracle mappings for `ActivityCatalog` and `WorkOrderStageSaver`.
8. Tests required before enabling database-provided rules.
