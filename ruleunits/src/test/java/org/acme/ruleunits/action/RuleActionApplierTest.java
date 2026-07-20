package org.acme.ruleunits.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.acme.ruleunits.catalog.ActivityCatalog;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.junit.jupiter.api.Test;

class RuleActionApplierTest {

    @Test
    void laterStageDeactivateAllIncludesEarlierRuleCreatedActivities() {
        WorkOrderEvaluation workOrder = workOrder(
                ActivityOccurrence.original(1, "L81494", "CAT2", 2));
        RuleActionApplier applier = new RuleActionApplier(
                activeCatalog("097079", "FG2802"));

        applier.apply(workOrder, List.of(
                new ReplaceActivity("L81494", "097079", "RA1", "RA1-test-1")));
        ActivityOccurrence ra1Created = active(workOrder, "097079");

        applier.apply(workOrder, List.of(
                new DeactivateAllActivities("RA2", "RA2-test-1"),
                new AddActivity("FG2802", "RA2", "RA2-test-1")));

        assertThat(ra1Created.isActive()).isFalse();
        assertThat(ra1Created.getLastAppliedRule()).isEqualTo("RA2-test-1");
        assertThat(active(workOrder, "FG2802").getCreatingRuleType()).isEqualTo("RA2");
    }

    @Test
    void sameStageActionsUseOneSnapshotAndDoNotDependOnTheirOrder() {
        WorkOrderEvaluation workOrder = workOrder(
                ActivityOccurrence.original(1, "L81494", "CAT2", 1));
        RuleActionApplier applier = new RuleActionApplier(activeCatalog("097079"));

        applier.apply(workOrder, List.of(
                new ReplaceActivity("L81494", "097079", "RA1", "replace"),
                new DeactivateActivityCategory("CAT2", "RA1", "category")));

        assertThat(active(workOrder, "097079").isActive()).isTrue();
    }

    @Test
    void rejectsInvalidAdditionBeforeAnyStageMutation() {
        ActivityOccurrence original =
                ActivityOccurrence.original(1, "L81494", "CAT2", 1);
        WorkOrderEvaluation workOrder = workOrder(original);

        assertThatThrownBy(() -> new RuleActionApplier(code -> false).apply(
                workOrder,
                List.of(
                        new DeactivateAllActivities("RA2", "RA2-test-1"),
                        new AddActivity("FG2802", "RA2", "RA2-test-1"))))
                .isInstanceOf(InvalidActivityAdditionException.class)
                .hasMessageContaining("FG2802");

        assertThat(original.isActive()).isTrue();
        assertThat(workOrder.getActivities()).containsExactly(original);
    }

    private static ActivityOccurrence active(
            WorkOrderEvaluation workOrder, String code) {
        return workOrder.getActivities().stream()
                .filter(ActivityOccurrence::isActive)
                .filter(activity -> activity.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private static WorkOrderEvaluation workOrder(ActivityOccurrence... activities) {
        return new WorkOrderEvaluation(
                "WO", "JT", WorkOrderType.FINAL, List.of(activities));
    }

    private static ActivityCatalog activeCatalog(String... codes) {
        Set<String> activeCodes = Set.of(codes);
        return activeCodes::contains;
    }
}
