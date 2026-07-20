# Codex session record — 2026-07-16

## Repository analysis completed

- Read the root and legacy `AGENTS.md` instructions.
- Mapped the Oracle schema, fixtures, reset process, and application connection.
- Identified the legacy stack: Java 11, Spring Boot 2.7.10, Maven 3.9.16, Drools/KIE 7.48.0.Final, and Oracle XE 21c.
- Traced the selected rules from request/work-order input through database DRL loading, classpath headers, KIE construction, agenda execution, queued actions, and persistence.
- Confirmed from code, read-only Oracle queries, and the sample log that rule types run RA1 → RA2 → RA3.
- Confirmed that duplicate activity occurrences are distinct and that only work-order activity state is materially changed in the reduced project.
- Treated `ruleunits/` as an unvalidated migration placeholder.

## Corrections and clarifications provided

- Any migration will continue to use Oracle; the PostgreSQL dependency is accidental.
- Saving the work-order aggregate is reduced-project plumbing. The full system also records which rule set was applied to the work order.
- The selected rules do not execute concurrently.
- No order is enforced among rules inside one agenda group.
- An “original activity” is an occurrence not added by a rule; this distinction is used later in the system.
- The sample run is repeatable after resetting one table in the database.
- Inactive activities satisfying RA3 category matching is a legacy defect and should be corrected in a migrated implementation.

## Characterization tests added

Added `SelectedLegacyRulesCharacterizationTests`, using mocked repositories with the production DRL assembly, Drools 7 KIE builder/session, adapters, action service, and orchestration.

1. `preservesDuplicateOccurrencesReplacedByRa1WhenRa2RunsLater`
   - Freezes one-for-one replacement of duplicate `L81494` occurrences.
   - Confirms RA1-created `097079` occurrences survive RA2 and retain RA1 attribution.
2. `ra1CategoryRuleDeactivatesCat2BeforeRa2AddsItsActivity`
   - Freezes RA1 CAT2 deactivation followed by RA2 processing.
   - Confirms each original occurrence records the rule that deactivated it.
3. `ra3CanMatchAnOriginalCat3ActivityDeactivatedByRa2`
   - Documents the legacy defect where RA3 sees an inactive original CAT3 activity.
   - This is a legacy baseline, not the desired migrated outcome.
4. `leavesANonMatchingWorkOrderUnchangedButStillSavesIt`
   - Freezes unchanged activity state and the save boundary for valid nonmatching work orders.
5. `silentlyIgnoresAMissingWorkOrder`
   - Freezes current handling of missing work orders: no save and overall success.

The empty Spring Initializr test was removed.

## Agreed migration contract

- Preserve duplicate activity occurrences and replace each matching occurrence independently.
- Preserve accurate applied rule attribution.
- Preserve explicit RA1 → RA2 → RA3 sequencing.
- Do not introduce ordering within a rule stage.
- Preserve the distinction between original and rule-created activities.
- Later “deactivate all” actions affect original activities, not activities created by earlier rules.
- Preserve the save boundary for valid nonmatching work orders.
- Continue ignoring missing work orders for now.
- Correct category matching in the migrated engine so inactive activities do not satisfy RA3.
- Keep Oracle as the persistence target.

## Commands and results

```bash
sdk use java 11.0.29-tem
./mvnw --version
./mvnw compile
./mvnw -Dtest=SelectedLegacyRulesCharacterizationTests test
./mvnw clean test
```

Results:

- Maven Wrapper: 3.9.16 under Java 11.0.29.
- Legacy compilation: successful, 25 production source files.
- Focused characterization suite: 5 tests passed.
- Clean full suite after removing the generated test: 5 tests passed, no failures or errors.
- Maven reports a non-blocking warning because `maven-compiler-plugin` is declared twice.
- Read-only Oracle queries confirmed rule metadata, sample work orders, duplicate occurrences, and missing sample identifiers. No rules or database mutations were executed during analysis.

## Files changed

- Added `droolslegacy/src/test/java/org/acme/droolslegacy/service/SelectedLegacyRulesCharacterizationTests.java`.
- Removed `droolslegacy/src/test/java/org/acme/droolslegacy/DroolslegacyApplicationTests.java`.
- Added this session record.

## Unresolved decisions

- Whether migrated rules must remain dynamically editable in Oracle or may initially be source-controlled Rule Unit DRLs.
- Exact compatible Spring Boot and KIE 10 versions for the migration application.
- Concrete Rule Unit data design and action-command representation.
- Oracle integration-test isolation strategy.
- Exact work-order rule-set audit column and update semantics omitted from the reduced project.
- Whether the migrated application needs an HTTP endpoint.

## Recommended next step

After review and approval of the migration plan, make `ruleunits/` a minimal Java 21 KIE 10 Rule Unit project with corrected dependencies and one engine smoke test. Do not migrate a business rule until Maven generation, tests, and IntelliJ execution are validated.
