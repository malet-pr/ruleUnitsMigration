package org.acme.ruleunits.ra1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.acme.ruleunits.action.RuleActionApplier;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.RuleUnitProvider;
import org.junit.jupiter.api.Test;

class Ra1CategoryRuleTest {

    @Test
    void activeCat2MatchesAndOnlyCat2IsDeactivated() {
        ActivityOccurrence cat2 =
                ActivityOccurrence.original(1, "G93210", "CAT2", 1);
        ActivityOccurrence cat1 =
                ActivityOccurrence.original(2, "ML6351", "CAT1", 1);
        WorkOrderEvaluation workOrder = workOrder(cat2, cat1);

        assertThat(execute(workOrder)).isEqualTo(1);

        assertThat(cat2.isActive()).isFalse();
        assertThat(cat2.getLastAppliedRule()).isEqualTo("RA1-test-2");
        assertThat(cat1.isActive()).isTrue();
    }

    @Test
    void inactiveOnlyCat2DoesNotMatch() {
        ActivityOccurrence cat2 =
                ActivityOccurrence.original(1, "G93210", "CAT2", 1);
        cat2.deactivateBy("earlier");
        WorkOrderEvaluation workOrder = workOrder(
                cat2, ActivityOccurrence.original(2, "ML6351", "CAT1", 1));

        assertThat(execute(workOrder)).isZero();
        assertThat(cat2.getLastAppliedRule()).isEqualTo("earlier");
    }

    @Test
    void nonCat2RemainsUnchanged() {
        ActivityOccurrence cat1 =
                ActivityOccurrence.original(1, "ML6351", "CAT1", 1);
        WorkOrderEvaluation workOrder = workOrder(cat1);

        assertThat(execute(workOrder)).isZero();
        assertThat(cat1.isActive()).isTrue();
    }

    private static int execute(WorkOrderEvaluation workOrder) {
        Ra1Unit data = new Ra1Unit();
        data.getWorkOrders().add(workOrder);
        int fired;
        try (RuleUnitInstance<Ra1Unit> instance =
                     RuleUnitProvider.get().createRuleUnitInstance(data)) {
            fired = instance.fire();
        }
        new RuleActionApplier(code -> true).apply(workOrder, data.getActions());
        return fired;
    }

    private static WorkOrderEvaluation workOrder(ActivityOccurrence... activities) {
        return new WorkOrderEvaluation(
                "WO", "FM3X635", WorkOrderType.FINAL, List.of(activities));
    }
}
