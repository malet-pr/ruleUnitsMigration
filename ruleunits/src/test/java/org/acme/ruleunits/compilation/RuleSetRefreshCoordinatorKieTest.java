package org.acme.ruleunits.compilation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.acme.ruleunits.loading.LoadedRuleSetDefinition;
import org.acme.ruleunits.loading.RuleSetDefinitionValidator;
import org.acme.ruleunits.refresh.RuleSetRefreshCoordinator;
import org.acme.ruleunits.refresh.RuleSetRefreshPhase;
import org.acme.ruleunits.refresh.RuleSetRefreshStatus;
import org.acme.ruleunits.runtime.ra1.Ra1RuntimeUnit;
import org.acme.ruleunits.snapshot.RuleExecutionUnavailableException;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;
import org.junit.jupiter.api.Test;

class RuleSetRefreshCoordinatorKieTest {
    @Test
    void failedReplacementCompilationKeepsServingTheLastKnownGoodRuntime() {
        ArrayDeque<LoadedRuleSetDefinition> definitions = new ArrayDeque<>(List.of(
                RuntimeRuleSetFixture.ruleSet(),
                RuntimeRuleSetFixture.ruleSetWithMalformedDrl()));
        RuleSetSnapshotManager snapshots = new RuleSetSnapshotManager();
        RuleSetRefreshCoordinator coordinator = new RuleSetRefreshCoordinator(
                "ACTIVITY_RULES",
                name -> definitions.removeFirst(),
                new RuleSetDefinitionValidator(
                        Set.of("097079", "SS8192", "Q79984", "FG2802", "AZ9593")::contains),
                new TraditionalDrlRenderer(),
                new RuntimeRuleSetCompiler(),
                snapshots);
        try {
            assertThatThrownBy(snapshots::acquire)
                    .isInstanceOf(RuleExecutionUnavailableException.class);

            var initial = coordinator.refresh();

            assertThat(initial.status()).isEqualTo(RuleSetRefreshStatus.PUBLISHED);
            assertThat(initial.attemptedVersion()).isEqualTo(17L);
            assertRa1RulesExecuteFromVersion17(snapshots);

            var rejected = coordinator.refresh();

            assertThat(rejected.status()).isEqualTo(RuleSetRefreshStatus.FAILED);
            assertThat(rejected.attemptedVersion()).isEqualTo(18L);
            assertThat(rejected.failurePhase()).isEqualTo(RuleSetRefreshPhase.COMPILE);
            assertThat(rejected.failureType()).isEqualTo("RuleSetCompilationException");
            assertThat(rejected.summary())
                    .isEqualTo("Failed to compile assembled rule set")
                    .doesNotContain("this is not valid DRL");
            assertRa1RulesExecuteFromVersion17(snapshots);
        } finally {
            snapshots.close();
        }
    }

    private void assertRa1RulesExecuteFromVersion17(RuleSetSnapshotManager snapshots) {
        try (var lease = snapshots.acquire()) {
            assertThat(lease.version()).isEqualTo(17L);
            Ra1RuntimeUnit data = new Ra1RuntimeUnit();
            data.getWorkOrders().add(new WorkOrderEvaluation(
                    "WO", "FM3X635", WorkOrderType.FINAL,
                    List.of(
                            ActivityOccurrence.original(1, "6T8121", null, 1),
                            ActivityOccurrence.original(2, "L81494", "CAT2", 1))));
            try (var instance = lease.createInstance("RA1", data)) {
                assertThat(instance.fire()).isEqualTo(2);
            }
        }
    }
}
