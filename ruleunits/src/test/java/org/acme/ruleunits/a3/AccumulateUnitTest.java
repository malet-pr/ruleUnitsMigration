package org.acme.ruleunits.a3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.acme.ruleunits.action.RuleActionApplier;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.RuleUnitProvider;
import org.junit.jupiter.api.Test;

class AccumulateUnitTest {

    @Test
    void firesForMoreThanOneActiveFg2802AndLeavesOne() {
        ActivityOccurrence first =
                ActivityOccurrence.ruleCreated(1, "FG2802", null, 1, "RA2");
        ActivityOccurrence second =
                ActivityOccurrence.ruleCreated(2, "FG2802", null, 1, "RA2");
        ActivityOccurrence third =
                ActivityOccurrence.original(3, "FG2802", null, 1);
        WorkOrderEvaluation workOrder = workOrder(
                first,
                second,
                third,
                ActivityOccurrence.original(4, "OTHER", null, 1));

        assertThat(execute(workOrder)).isEqualTo(1);

        assertThat(activeWithCode(workOrder, "FG2802")).containsExactly(first);
        assertThat(second.getLastAppliedRule()).isEqualTo("withAccumulate");
        assertThat(third.getLastAppliedRule()).isEqualTo("withAccumulate");
        assertThat(activeWithCode(workOrder, "OTHER")).hasSize(1);
    }

    @Test
    void doesNotFireForOneActiveFg2802() {
        WorkOrderEvaluation workOrder = workOrder(
                ActivityOccurrence.ruleCreated(1, "FG2802", null, 1, "RA2"),
                ActivityOccurrence.original(2, "OTHER", null, 1));

        assertThat(execute(workOrder)).isZero();
        assertThat(activeWithCode(workOrder, "FG2802")).hasSize(1);
    }

    @Test
    void inactiveFg2802DoesNotContributeToTheAccumulatedList() {
        ActivityOccurrence inactive =
                ActivityOccurrence.ruleCreated(1, "FG2802", null, 1, "RA2");
        inactive.deactivateBy("earlier");
        WorkOrderEvaluation workOrder = workOrder(
                inactive,
                ActivityOccurrence.ruleCreated(2, "FG2802", null, 1, "RA2"));

        assertThat(execute(workOrder)).isZero();
        assertThat(activeWithCode(workOrder, "FG2802")).hasSize(1);
    }

    @Test
    void doesNotFireWithoutFg2802() {
        WorkOrderEvaluation workOrder = workOrder(
                ActivityOccurrence.original(1, "OTHER", null, 1));

        assertThat(execute(workOrder)).isZero();
    }

    private static int execute(WorkOrderEvaluation workOrder) {
        AccumulateUnit data = new AccumulateUnit();
        data.getWorkOrders().add(workOrder);

        int fired;
        try (RuleUnitInstance<AccumulateUnit> instance =
                     RuleUnitProvider.get().createRuleUnitInstance(data)) {
            fired = instance.fire();
        }

        new RuleActionApplier(code -> true).apply(workOrder, data.getActions());
        return fired;
    }

    private static List<ActivityOccurrence> activeWithCode(
            WorkOrderEvaluation workOrder, String code) {
        return workOrder.getActivities().stream()
                .filter(ActivityOccurrence::isActive)
                .filter(activity -> activity.getCode().equals(code))
                .toList();
    }

    private static WorkOrderEvaluation workOrder(ActivityOccurrence... activities) {
        return new WorkOrderEvaluation(
                "WO", "JT", WorkOrderType.FINAL, List.of(activities));
    }
}
