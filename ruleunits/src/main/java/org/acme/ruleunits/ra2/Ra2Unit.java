package org.acme.ruleunits.ra2;

import java.util.ArrayList;
import java.util.List;
import org.acme.ruleunits.action.RuleAction;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.RuleUnitData;

/**
 * Build-time Rule Unit data contract for RA2 refinement rules. Its input is the saved accumulated
 * RA1 result, so RA1-created occurrences are ordinary RA2 candidates.
 */
public final class Ra2Unit implements RuleUnitData {

    private final DataStore<WorkOrderEvaluation> workOrders = DataSource.createStore();
    private final List<RuleAction> actions = new ArrayList<>();

    public DataStore<WorkOrderEvaluation> getWorkOrders() {
        return workOrders;
    }

    public List<RuleAction> getActions() {
        return actions;
    }
}
