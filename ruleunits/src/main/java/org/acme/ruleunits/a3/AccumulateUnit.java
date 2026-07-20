package org.acme.ruleunits.a3;

import java.util.ArrayList;
import java.util.List;
import org.acme.ruleunits.action.RuleAction;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.RuleUnitData;

/**
 * Isolated Rule Unit data contract used to prove traditional DRL accumulate syntax. It is a
 * capability fixture and is not part of the production RA1 → RA2 → RA3 sequence.
 */
public final class AccumulateUnit implements RuleUnitData {

    private final DataStore<WorkOrderEvaluation> workOrders = DataSource.createStore();
    private final List<RuleAction> actions = new ArrayList<>();

    public DataStore<WorkOrderEvaluation> getWorkOrders() {
        return workOrders;
    }

    public List<RuleAction> getActions() {
        return actions;
    }
}
