package org.acme.ruleunits.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.junit.jupiter.api.Test;

class SelectedRulesStagesTest {

    private static final Set<String> ACTIVE_CATALOG =
            Set.of("097079", "SS8192", "Q79984", "FG2802", "AZ9593");

    @Test
    void executesAndSavesRa1ThenRa2ThenRa3() {
        List<String> saves = new ArrayList<>();
        WorkOrderEvaluation workOrder = workOrder(
                "OTHER",
                WorkOrderType.ADD,
                ActivityOccurrence.original(1, "I51434", "CAT1", 1));

        RulesExecutionResult result = orchestrator(saves).execute(workOrder);

        assertThat(result.executionTrace()).containsExactly("RA1", "RA2", "RA3");
        assertThat(saves).containsExactly("RA1", "RA2", "RA3");
        assertThat(result.eligibleForSave()).isTrue();
        assertThat(result.firedRules()).isZero();
        assertThat(activeCodes(workOrder)).containsExactly("I51434");
    }

    @Test
    void duplicateRa1ReplacementsAreConsumedByRa2Refinement() {
        List<String> saves = new ArrayList<>();
        WorkOrderEvaluation workOrder = workOrder(
                "KPVG961",
                WorkOrderType.FINAL,
                ActivityOccurrence.original(1, "6T8121", "CAT3", 1),
                ActivityOccurrence.original(2, "L81494", "CAT2", 1),
                ActivityOccurrence.original(3, "L81494", "CAT2", 1),
                ActivityOccurrence.original(4, "ZN8450", null, 1));

        RulesExecutionResult result = orchestrator(saves).execute(workOrder);

        assertThat(result.firedRules()).isEqualTo(2);
        assertThat(activeCodes(workOrder)).containsExactly("FG2802");
        assertThat(workOrder.getActivities())
                .filteredOn(activity -> activity.getCode().equals("097079"))
                .hasSize(2)
                .allSatisfy(activity -> {
                    assertThat(activity.isActive()).isFalse();
                    assertThat(activity.getLastAppliedRule()).isEqualTo("RA2-test-1");
                });
    }

    @Test
    void correctedRa3DoesNotMatchCat3DeactivatedByRa2() {
        WorkOrderEvaluation workOrder = workOrder(
                "JM5G513",
                WorkOrderType.FINAL,
                ActivityOccurrence.original(1, "E60387", "CAT3", 1),
                ActivityOccurrence.original(2, "KO6502", null, 1));

        RulesExecutionResult result = orchestrator(new ArrayList<>()).execute(workOrder);

        assertThat(result.firedRules()).isEqualTo(1);
        assertThat(activeCodes(workOrder)).containsExactly("FG2802");
        assertThat(activeCodes(workOrder)).doesNotContain("AZ9593");
    }
    private static SelectedRulesOrchestrator orchestrator(List<String> saves) {
        return new SelectedRulesOrchestrator(
                ACTIVE_CATALOG::contains,
                (workOrder, stage) -> saves.add(stage));
    }

    private static WorkOrderEvaluation workOrder(
            String jobType,
            WorkOrderType type,
            ActivityOccurrence... activities) {
        return new WorkOrderEvaluation("WO", jobType, type, List.of(activities));
    }

    private static List<String> activeCodes(WorkOrderEvaluation workOrder) {
        return workOrder.getActiveActivityCodes();
    }
}
