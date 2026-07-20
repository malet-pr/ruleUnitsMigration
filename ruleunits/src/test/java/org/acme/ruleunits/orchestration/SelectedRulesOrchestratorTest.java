package org.acme.ruleunits.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.junit.jupiter.api.Test;

class SelectedRulesOrchestratorTest {

    private static final Set<String> ACTIVE_CATALOG =
            Set.of("097079", "SS8192", "Q79984");

    private final SelectedRulesOrchestrator orchestrator =
            new SelectedRulesOrchestrator(ACTIVE_CATALOG::contains);

    @Test
    void firstVariantPreservesDuplicateOccurrenceSemantics() {
        WorkOrderEvaluation workOrder = workOrder(
                WorkOrderType.FINAL,
                ActivityOccurrence.original(1, "6T8121", "CAT", 1),
                ActivityOccurrence.original(2, "L81494", "CAT", 2),
                ActivityOccurrence.original(3, "L81494", "CAT", 3));

        assertThat(orchestrator.executeRa1(workOrder)).isEqualTo(1);

        assertReplaced(workOrder, "L81494", "097079", "RA1-test-1", 2);
    }

    @Test
    void secondVariantReplacesKo6502() {
        WorkOrderEvaluation workOrder = workOrder(
                WorkOrderType.FINAL,
                ActivityOccurrence.original(1, "DS7068", "CAT", 1),
                ActivityOccurrence.original(2, "KO6502", "CAT", 4));

        assertThat(orchestrator.executeRa1(workOrder)).isEqualTo(1);

        assertReplaced(workOrder, "KO6502", "SS8192", "RA1-test-1-2", 1);
    }

    @Test
    void thirdVariantReplacesG99427() {
        WorkOrderEvaluation workOrder = workOrder(
                WorkOrderType.FINAL,
                ActivityOccurrence.original(1, "DS7068", "CAT", 1),
                ActivityOccurrence.original(2, "G99427", "CAT", 5));

        assertThat(orchestrator.executeRa1(workOrder)).isEqualTo(1);

        assertReplaced(workOrder, "G99427", "Q79984", "RA1-test-1-3", 1);
    }

    @Test
    void multipleVariantsCanFireWithoutDependingOnTheirOrder() {
        WorkOrderEvaluation workOrder = workOrder(
                WorkOrderType.FINAL,
                ActivityOccurrence.original(1, "DS7068", "CAT", 1),
                ActivityOccurrence.original(2, "KO6502", "CAT", 2),
                ActivityOccurrence.original(3, "G99427", "CAT", 3));

        assertThat(orchestrator.executeRa1(workOrder)).isEqualTo(2);

        assertThat(activeCodes(workOrder)).contains("DS7068", "SS8192", "Q79984");
        assertThat(activeCodes(workOrder)).doesNotContain("KO6502", "G99427");
    }

    @Test
    void doesNotFireWithoutAllRequiredActivities() {
        WorkOrderEvaluation workOrder = workOrder(
                WorkOrderType.FINAL,
                ActivityOccurrence.original(1, "DS7068", "CAT", 1));

        assertThat(orchestrator.executeRa1(workOrder)).isZero();
        assertThat(workOrder.getActivities()).hasSize(1);
    }

    @Test
    void doesNotFireForAddType() {
        WorkOrderEvaluation workOrder = workOrder(
                WorkOrderType.ADD,
                ActivityOccurrence.original(1, "DS7068", "CAT", 1),
                ActivityOccurrence.original(2, "KO6502", "CAT", 1));

        assertThat(orchestrator.executeRa1(workOrder)).isZero();
    }

    private static void assertReplaced(
            WorkOrderEvaluation workOrder,
            String oldCode,
            String newCode,
            String ruleName,
            int expectedNewOccurrences) {
        assertThat(workOrder.getActivities())
                .filteredOn(activity -> activity.getCode().equals(oldCode))
                .allSatisfy(activity -> {
                    assertThat(activity.isActive()).isFalse();
                    assertThat(activity.getLastAppliedRule()).isEqualTo(ruleName);
                });
        assertThat(workOrder.getActivities())
                .filteredOn(activity -> activity.getCode().equals(newCode))
                .hasSize(expectedNewOccurrences)
                .allSatisfy(activity -> assertThat(activity.isActive()).isTrue());
    }

    private static List<String> activeCodes(WorkOrderEvaluation workOrder) {
        return workOrder.getActiveActivityCodes();
    }

    private static WorkOrderEvaluation workOrder(
            WorkOrderType type, ActivityOccurrence... activities) {
        return new WorkOrderEvaluation("WO", "JT", type, List.of(activities));
    }
}
