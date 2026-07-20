package org.acme.ruleunits.ra3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.acme.ruleunits.action.RuleActionApplier;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.RuleUnitProvider;
import org.junit.jupiter.api.Test;

class Ra3UnitTest {

    @Test
    void activeCat3AndJm5g513FireRa3() {
        WorkOrderEvaluation workOrder = workOrder(
                ActivityOccurrence.ruleCreated(1, "PRIOR", "CAT3", 1, "RA2"));

        assertThat(execute(workOrder)).isEqualTo(1);

        assertThat(workOrder.getActiveActivityCodes()).containsExactly("AZ9593");
    }

    @Test
    void inactiveOnlyCat3DoesNotFireRa3() {
        ActivityOccurrence cat3 =
                ActivityOccurrence.original(1, "E60387", "CAT3", 1);
        cat3.deactivateBy("RA2-test-1");
        WorkOrderEvaluation workOrder = workOrder(
                cat3,
                ActivityOccurrence.ruleCreated(2, "FG2802", null, 1, "RA2"));

        assertThat(execute(workOrder)).isZero();

        assertThat(workOrder.getActiveActivityCodes()).containsExactly("FG2802");
    }

    private static int execute(WorkOrderEvaluation workOrder) {
        Ra3Unit data = new Ra3Unit();
        data.getWorkOrders().add(workOrder);
        int fired;
        try (RuleUnitInstance<Ra3Unit> instance =
                     RuleUnitProvider.get().createRuleUnitInstance(data)) {
            fired = instance.fire();
        }
        Set<String> activeCatalog = Set.of("AZ9593");
        new RuleActionApplier(activeCatalog::contains)
                .apply(workOrder, data.getActions());
        return fired;
    }

    private static WorkOrderEvaluation workOrder(ActivityOccurrence... activities) {
        return new WorkOrderEvaluation(
                "WO", "JM5G513", WorkOrderType.FINAL, List.of(activities));
    }
}
